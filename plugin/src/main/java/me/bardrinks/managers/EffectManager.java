package me.bardrinks.managers;

import io.papermc.paper.entity.TeleportFlag;
import me.bardrinks.BarDrinks;
import me.bardrinks.models.DrunkStage;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class EffectManager {

    private final Random random = new Random();

    // Cores do vômito: Amarelo, Verde e Marrom
    private static final Particle.DustOptions VOMIT_YELLOW = new Particle.DustOptions(Color.fromRGB(235, 205, 40), 1.25f);
    private static final Particle.DustOptions VOMIT_GREEN = new Particle.DustOptions(Color.fromRGB(95, 185, 35), 1.25f);
    private static final Particle.DustOptions VOMIT_BROWN = new Particle.DustOptions(Color.fromRGB(115, 75, 25), 1.25f);
    private static final Particle.DustOptions[] VOMIT_COLORS = {VOMIT_YELLOW, VOMIT_GREEN, VOMIT_BROWN};

    // Cor das partículas de bêbado (substitui o antigo efeito de poção LUCK para não interferir na pesca)
    private static final Color DRUNK_GREEN_COLOR = Color.fromRGB(89, 201, 53);
    private static final Particle.DustOptions DRUNK_GREEN_DUST = new Particle.DustOptions(DRUNK_GREEN_COLOR, 1.15f);

    // Controle de balanço suave (fase senoidal por player)
    private final Map<UUID, Double> wobblePhase = new ConcurrentHashMap<>();

    // Cooldown para sentar/deitar involuntariamente
    private final Map<UUID, Long> lastSitOrLayMs = new ConcurrentHashMap<>();

    // Sessões de players deitados no chão (estilo Preguiça, com BARRIER falsa na cabeça)
    private final Map<UUID, LyingSession> lyingSessions = new ConcurrentHashMap<>();

    // Assentos invisíveis de players forçados a sentar
    private final Map<UUID, Entity> sittingSeats = new ConcurrentHashMap<>();

    // Sessões ativas de vômito
    private final Map<UUID, VomitSession> activeVomits = new ConcurrentHashMap<>();

    // Sessões ativas de Apagão / Teleporte (Bebo 3)
    private final Set<UUID> activeBlackouts = ConcurrentHashMap.newKeySet();

    // Players atingidos por vômito -> Timestamp de término dos 10 segundos exatos
    private final Map<UUID, Long> vomitStainedUntilMs = new ConcurrentHashMap<>();

    public void tickFastEffects(Player p, double birita) {
        UUID uuid = p.getUniqueId();
        long now = System.currentTimeMillis();

        // 1. Processa estado de "Sujo de Vômito" (10 segundos exatos)
        Long stainedUntil = vomitStainedUntilMs.get(uuid);
        if (stainedUntil != null) {
            if (now >= stainedUntil) {
                vomitStainedUntilMs.remove(uuid);
            } else {
                spawnStainedVomitParticles(p);
                repelPassiveMobs(p);
            }
        }

        // Remove resquício do antigo efeito LUCK se existir (para nunca afetar pesca)
        if (p.hasPotionEffect(PotionEffectType.LUCK)) {
            p.removePotionEffect(PotionEffectType.LUCK);
        }

        DrunkStage stage = DrunkStage.fromPercentage(birita);

        // 2. Partículas verdes a partir do Bebo 2 (51% a 70%) e Bebo 3 (71% a 100%)
        if (stage == DrunkStage.BEBO_2 || stage == DrunkStage.BEBO_3) {
            if (random.nextDouble() < 0.45) {
                Location pLoc = p.getLocation().add(
                        (random.nextDouble() - 0.5) * 0.7,
                        0.5 + random.nextDouble() * 1.1,
                        (random.nextDouble() - 0.5) * 0.7
                );
                try {
                    p.getWorld().spawnParticle(Particle.ENTITY_EFFECT, pLoc, 1, 0, 0, 0, 1.0, DRUNK_GREEN_COLOR);
                } catch (Throwable ignored) {
                    p.getWorld().spawnParticle(Particle.DUST, pLoc, 1, 0, 0, 0, 0, DRUNK_GREEN_DUST);
                }
            }
        }

        // 3. Balanço suave (Wobble) no Bebo 2 e Bebo 3 (mais forte quando o player está andando!)
        if (stage == DrunkStage.BEBO_2) {
            applySmoothWobble(p, 0.025, 0.065);
        } else if (stage == DrunkStage.BEBO_3) {
            applySmoothWobble(p, 0.040, 0.115);
        } else {
            wobblePhase.remove(uuid);
        }
    }

    /**
     * Checagem a cada 2 segundos (40 ticks) para:
     * - Náusea no Bebo 3
     * - Chance de Apagão/Teleporte (1/100 até 1/40)
     * - Chance de Vômito (1/50)
     * - Chance de Sentar ou Deitar sozinho no Bebo 3
     */
    public void tickPeriodicEffects(Player p, double birita) {
        UUID uuid = p.getUniqueId();
        DrunkStage stage = DrunkStage.fromPercentage(birita);

        if (stage != DrunkStage.BEBO_3) {
            return;
        }

        p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 160, 0, false, false, false));

        if (isVomiting(uuid) || isLyingDown(uuid) || isSitting(uuid) || activeBlackouts.contains(uuid)
                || p.isInsideVehicle() || p.isSleeping()) {
            return;
        }

        // 1. Chance de Apagão / Teleporte (começa em 1/100, cai de 10 em 10 até 1/40 ao beber no Bebo 3)
        int blackoutDenom = BarDrinks.get().getDrunkManager().getBlackoutDenominator(uuid);
        if (random.nextInt(Math.max(40, blackoutDenom)) == 0) {
            triggerBlackout(p);
            return;
        }

        // 2. Chance de Vômito: 1 em 50 a cada 2 segundos
        if (random.nextInt(50) == 0) {
            triggerVomit(p);
            return;
        }

        // 3. Forçar Sentar ou Deitar (sem GSit) se estiver no chão
        trySitOrLay(p);
    }

    /**
     * Verifica se o player está pressionando teclas de movimento (W/A/S/D) sem confundir com o empurrão lateral do plugin.
     */
    private boolean isPlayerActivelyWalking(Player p) {
        try {
            Input input = p.getCurrentInput();
            if (input.isForward() || input.isBackward() || input.isLeft() || input.isRight() || input.isSprint()) {
                return true;
            }
        } catch (Throwable ignored) {}

        // Fallback caso getCurrentInput não esteja disponível: checa velocidade na direção do olhar (frente/trás)
        Location loc = p.getLocation();
        double radYaw = Math.toRadians(loc.getYaw());
        double forwardX = -Math.sin(radYaw);
        double forwardZ = Math.cos(radYaw);
        Vector vel = p.getVelocity();
        double forwardSpeed = Math.abs(vel.getX() * forwardX + vel.getZ() * forwardZ);
        return forwardSpeed > 0.04 || p.isSprinting();
    }

    /**
     * Balanço suave usando curva senoidal:
     * - Quando parado: força leve (idleStrength)
     * - Quando andando: força bem mais intensa (walkingStrength), sem criar loop infinito!
     * - NUNCA altera a velocidade Y, impedindo que o player flutue no ar!
     */
    private void applySmoothWobble(Player p, double idleStrength, double walkingStrength) {
        UUID uuid = p.getUniqueId();

        if (!p.isOnGround() || p.isInsideVehicle() || p.isSleeping() || p.isSwimming()
                || p.isFlying() || p.isGliding() || p.isClimbing()
                || isLyingDown(uuid) || isSitting(uuid) || activeBlackouts.contains(uuid)) {
            return;
        }

        boolean walking = isPlayerActivelyWalking(p);
        double maxStrength = walking ? walkingStrength : idleStrength;

        double phase = wobblePhase.getOrDefault(uuid, random.nextDouble() * Math.PI * 2.0);
        phase += (walking ? 0.28 : 0.16) + (random.nextDouble() * 0.06);
        wobblePhase.put(uuid, phase);

        double wave = Math.sin(phase) * 0.7 + Math.sin(phase * 0.45) * 0.3;
        if (Math.abs(wave) < 0.15) return;

        Location loc = p.getLocation();
        double radYaw = Math.toRadians(loc.getYaw());
        double sideX = Math.cos(radYaw);
        double sideZ = Math.sin(radYaw);

        Vector currentVel = p.getVelocity();
        double pushX = sideX * wave * maxStrength;
        double pushZ = sideZ * wave * maxStrength;

        Vector newVel = new Vector(
                currentVel.getX() + pushX,
                currentVel.getY(),
                currentVel.getZ() + pushZ
        );
        p.setVelocity(newVel);
    }

    // =========================================================================
    //  EFEITO DE APAGÃO / TELEPORTE (50 a 120 blocos, 8s Cegueira + Warden)
    // =========================================================================

    public void triggerBlackout(Player p) {
        UUID uuid = p.getUniqueId();
        if (activeBlackouts.contains(uuid)) return;
        activeBlackouts.add(uuid);

        // Se estiver com a mangueira do barril, desconecta
        BarrelManager.PlacedBarrel heldBarrel = BarDrinks.get().getBarrelManager().getBarrelHeldBy(uuid);
        if (heldBarrel != null) {
            BarDrinks.get().getBarrelManager().disconnectCurrentHolder(heldBarrel, false);
        }
        stopSitting(uuid);
        stopLying(uuid);

        // T = 0s: Cegueira por 8 segundos (160 ticks) + Batimento cardíaco do Warden
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 160, 0, false, false, true));
        p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.5f, 1.0f);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead()) {
                    activeBlackouts.remove(uuid);
                    cancel();
                    return;
                }

                ticks += 5;

                // Batimento cardíaco contínuo durante a cegueira
                if (ticks % 20 == 0 && ticks < 160) {
                    p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.5f, 1.0f);
                }

                // T = 1s (20 ticks): Mensagem no chat + Teleporte para 50-120 blocos + Deitado travado
                if (ticks == 20) {
                    String rawMsg = "Ai não to me sentindo muito bem...";
                    String drunkMsg = BarDrinks.get().getChatListener().applyDrunkSpeech(rawMsg, DrunkStage.BEBO_3);

                    // Para o próprio player sai normal; para todos os outros sai com fala de bêbado
                    p.sendMessage("§f<" + p.getName() + "> " + rawMsg);
                    for (Player other : Bukkit.getOnlinePlayers()) {
                        if (!other.getUniqueId().equals(uuid)) {
                            other.sendMessage("§f<" + p.getName() + "> " + drunkMsg);
                        }
                    }

                    Location safeDest = findSafeBlackoutLocation(p);
                    if (safeDest != null) {
                        p.teleport(safeDest);
                    }

                    // Coloca na animação deitada IMÓVEL (travado durante os 7s restantes da cegueira, e depois acorda ao se mexer)
                    startBlackoutLyingSession(p);
                }

                // T = 8s (160 ticks): Fim da cegueira -> Zera a barra, reseta a chance para 1/100 e libera para levantar ao se mover
                if (ticks >= 160) {
                    activeBlackouts.remove(uuid);
                    BarDrinks.get().getDrunkManager().setBiritaInstant(uuid, 0.0);
                    BarDrinks.get().getDrunkManager().resetBlackoutDenominator(uuid);
                    BarDrinks.get().getRankingManager().saveDataAsync();

                    LyingSession session = lyingSessions.get(uuid);
                    if (session != null) {
                        session.unlockMovementAndWaitStep();
                    }
                    p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                            new TextComponent("§eVocê acordou do apagão... Mova-se para se levantar."));
                    cancel();
                }
            }
        }.runTaskTimer(BarDrinks.get(), 5L, 5L);
    }

    /**
     * Procura um local seguro entre 50 e 120 blocos de distância (para os lados ou abaixo, nunca preso em blocos).
     */
    private Location findSafeBlackoutLocation(Player player) {
        Location start = player.getLocation();
        World world = start.getWorld();
        if (world == null) return start;

        for (int attempt = 0; attempt < 45; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = 50.0 + (random.nextDouble() * 70.0); // Entre 50 e 120 blocos
            int targetX = start.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
            int targetZ = start.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);

            // Garante que o chunk está carregado para checar os blocos com segurança
            Chunk chunk = world.getChunkAt(targetX >> 4, targetZ >> 4);
            if (!chunk.isLoaded()) {
                chunk.load();
            }

            int maxY = Math.min(world.getMaxHeight() - 3, start.getBlockY() + 25);
            int minY = Math.max(world.getMinHeight() + 2, start.getBlockY() - 45);

            // Procura de cima para baixo (superfície ou andares abaixo) um bloco sólido com 2 blocos livres de ar acima
            for (int y = maxY; y >= minY; y--) {
                Block ground = world.getBlockAt(targetX, y - 1, targetZ);
                Block feet = world.getBlockAt(targetX, y, targetZ);
                Block head = world.getBlockAt(targetX, y + 1, targetZ);

                if (isSafeGround(ground) && isSafeAirSpace(feet) && isSafeAirSpace(head)) {
                    return new Location(world, targetX + 0.5, y, targetZ + 0.5, start.getYaw(), 0.0f);
                }
            }

            // Fallback usando o bloco mais alto do mundo naquela coordenada
            int highestY = world.getHighestBlockYAt(targetX, targetZ);
            if (highestY > world.getMinHeight() + 1 && highestY < world.getMaxHeight() - 2) {
                Block ground = world.getBlockAt(targetX, highestY, targetZ);
                Block feet = world.getBlockAt(targetX, highestY + 1, targetZ);
                Block head = world.getBlockAt(targetX, highestY + 2, targetZ);
                if (isSafeGround(ground) && isSafeAirSpace(feet) && isSafeAirSpace(head)) {
                    return new Location(world, targetX + 0.5, highestY + 1, targetZ + 0.5, start.getYaw(), 0.0f);
                }
            }
        }
        return start;
    }

    private boolean isSafeGround(Block b) {
        Material m = b.getType();
        if (!m.isSolid() || b.isLiquid()) return false;
        return m != Material.LAVA && m != Material.MAGMA_BLOCK && m != Material.CACTUS
                && m != Material.FIRE && m != Material.SOUL_FIRE && m != Material.CAMPFIRE
                && m != Material.POWDER_SNOW && m != Material.BARRIER;
    }

    private boolean isSafeAirSpace(Block b) {
        Material m = b.getType();
        if (m.isSolid() || b.isLiquid()) return false;
        return m != Material.LAVA && m != Material.FIRE && m != Material.SOUL_FIRE
                && m != Material.SWEET_BERRY_BUSH && m != Material.POWDER_SNOW && m != Material.COBWEB;
    }

    // =========================================================================
    //  SISTEMA DE VÔMITO (Câmera até 90° + Parábola 0 a 3 blocos + 10s Fedor)
    // =========================================================================

    public boolean isVomiting(UUID uuid) {
        return activeVomits.containsKey(uuid);
    }

    public void triggerVomit(Player p) {
        UUID uuid = p.getUniqueId();
        if (activeVomits.containsKey(uuid)) return;

        stopSitting(uuid);
        stopLying(uuid);

        VomitSession session = new VomitSession(p);
        activeVomits.put(uuid, session);
        session.start();
    }

    public void markPlayerVomitStained(Player target) {
        UUID uuid = target.getUniqueId();
        long now = System.currentTimeMillis();
        Long prev = vomitStainedUntilMs.get(uuid);
        if (prev == null || now >= prev) {
            target.sendMessage("§2§oEca! Você se sujou de vômito! Os animais vão fugir de você por 10 segundos.");
        }
        vomitStainedUntilMs.put(uuid, now + 10000L);
    }

    private void spawnStainedVomitParticles(Player p) {
        Location base = p.getLocation();
        World w = base.getWorld();
        if (w == null) return;

        for (int i = 0; i < 3; i++) {
            Particle.DustOptions color = VOMIT_COLORS[random.nextInt(VOMIT_COLORS.length)];
            double ox = (random.nextDouble() - 0.5) * 0.7;
            double oy = 0.2 + random.nextDouble() * 1.3;
            double oz = (random.nextDouble() - 0.5) * 0.7;
            w.spawnParticle(Particle.DUST, base.getX() + ox, base.getY() + oy, base.getZ() + oz, 1, 0, 0, 0, 0, color);
        }
    }

    private void repelPassiveMobs(Player p) {
        Location pLoc = p.getLocation();
        for (Entity entity : p.getNearbyEntities(7.0, 4.0, 7.0)) {
            if (!(entity instanceof Mob mob)) continue;
            if (entity instanceof Enemy || entity instanceof Monster || entity instanceof Boss) continue;
            if (BarDrinks.get().getBarrelManager().isBarrelLeashEntity(entity)) continue;

            Location mLoc = mob.getLocation();
            double dx = mLoc.getX() - pLoc.getX();
            double dz = mLoc.getZ() - pLoc.getZ();
            double distSq = dx * dx + dz * dz;
            if (distSq < 0.0001) {
                dx = random.nextDouble() - 0.5;
                dz = random.nextDouble() - 0.5;
                distSq = dx * dx + dz * dz;
            }

            double dist = Math.sqrt(distSq);
            double dirX = dx / dist;
            double dirZ = dz / dist;

            Location fleeTarget = pLoc.clone().add(dirX * 8.5, 0, dirZ * 8.5);
            fleeTarget.setY(mLoc.getY());
            try {
                mob.getPathfinder().moveTo(fleeTarget, 1.45);
            } catch (Throwable ignored) {}

            if (dist < 5.5 && mob.isOnGround()) {
                Vector vel = mob.getVelocity();
                double speed = 0.22 * ((5.5 - dist) / 5.5 + 0.35);
                mob.setVelocity(new Vector(dirX * speed, vel.getY(), dirZ * speed));
            }
        }
    }

    private class VomitSession {
        private final Player player;
        private BukkitTask task;
        private boolean reachedBottom = false;
        private int vomitTicks = 0;

        public VomitSession(Player player) {
            this.player = player;
        }

        public void start() {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, 1.0f, 0.6f);
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§2§l🤢 Você está passando mal..."));

            task = new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isOnline() || player.isDead()) {
                        cancelSession();
                        return;
                    }

                    if (!reachedBottom) {
                        Location loc = player.getLocation();
                        float currentPitch = loc.getPitch();
                        float nextPitch = Math.min(90.0f, currentPitch + 9.0f);
                        loc.setPitch(nextPitch);

                        try {
                            player.teleport(loc,
                                    TeleportFlag.Relative.X,
                                    TeleportFlag.Relative.Y,
                                    TeleportFlag.Relative.Z,
                                    TeleportFlag.Relative.YAW);
                        } catch (Throwable t) {
                            player.teleport(loc);
                        }

                        if (nextPitch >= 89.5f) {
                            reachedBottom = true;
                            BarDrinks.get().getDrunkManager().removeBirita(player, 50.0);
                            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 0.7f, 0.5f);
                        }
                        return;
                    }

                    vomitTicks++;
                    if (vomitTicks > 80) {
                        cancelSession();
                        return;
                    }

                    if (vomitTicks % 10 == 1) {
                        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_SLIME_BLOCK_PLACE, 0.9f, 0.6f + random.nextFloat() * 0.3f);
                    }
                    if (vomitTicks % 25 == 1) {
                        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, 0.8f, 0.5f);
                    }

                    spawnVomitParabola(player);
                }
            }.runTaskTimer(BarDrinks.get(), 0L, 1L);
        }

        private void spawnVomitParabola(Player p) {
            Location eye = p.getEyeLocation();
            World world = eye.getWorld();
            if (world == null) return;

            Location mouth = eye.clone().subtract(0, 0.18, 0);
            double floorY = p.getLocation().getY();
            double totalDrop = Math.max(0.5, mouth.getY() - floorY);

            float rawPitch = eye.getPitch();
            float effectivePitch = Math.max(0.0f, Math.min(90.0f, rawPitch));
            double maxHorizontalDist = 3.0 * ((90.0 - effectivePitch) / 90.0);

            double radYaw = Math.toRadians(eye.getYaw());
            double forwardX = -Math.sin(radYaw);
            double forwardZ = Math.cos(radYaw);

            int steps = 10;
            for (int i = 1; i <= steps; i++) {
                double s = (double) i / steps;
                double horizDist = maxHorizontalDist * s;
                double arcBoost = (maxHorizontalDist / 3.0) * 0.25 * Math.sin(s * Math.PI);
                double y = mouth.getY() - (totalDrop * (s * s)) + arcBoost;

                double spread = 0.06 * s;
                double px = mouth.getX() + forwardX * horizDist + (random.nextDouble() - 0.5) * spread;
                double py = y + (random.nextDouble() - 0.5) * 0.05;
                double pz = mouth.getZ() + forwardZ * horizDist + (random.nextDouble() - 0.5) * spread;

                Particle.DustOptions color = VOMIT_COLORS[(i + vomitTicks) % VOMIT_COLORS.length];
                world.spawnParticle(Particle.DUST, px, py, pz, 1, 0, 0, 0, 0, color);

                Location pointLoc = new Location(world, px, py, pz);
                for (Entity nearby : world.getNearbyEntities(pointLoc, 0.55, 0.65, 0.55)) {
                    if (nearby instanceof Player other && !other.getUniqueId().equals(p.getUniqueId())) {
                        markPlayerVomitStained(other);
                    }
                }
            }

            if (maxHorizontalDist <= 0.85) {
                markPlayerVomitStained(p);
            }
        }

        public void cancelSession() {
            if (task != null) {
                task.cancel();
                task = null;
            }
            activeVomits.remove(player.getUniqueId());
        }
    }

    // =========================================================================
    //  SENTAR E DEITAR INVOLUNTÁRIO NO BEBO 3 (100% Nativo, Sem GSit)
    // =========================================================================

    private void trySitOrLay(Player p) {
        if (!p.isOnGround()) return;

        UUID uuid = p.getUniqueId();
        long now = System.currentTimeMillis();
        long last = lastSitOrLayMs.getOrDefault(uuid, 0L);

        if (now - last < 25000L) return;

        double roll = random.nextDouble();
        if (roll < 0.04) {
            lastSitOrLayMs.put(uuid, now);
            forceLayDown(p, 120);
        } else if (roll < 0.08) {
            lastSitOrLayMs.put(uuid, now);
            forceSitDown(p, 120);
        }
    }

    public void forceLayDown(Player p, int durationTicks) {
        UUID uuid = p.getUniqueId();
        if (lyingSessions.containsKey(uuid) || sittingSeats.containsKey(uuid)) return;

        LyingSession session = new LyingSession(p, durationTicks, false);
        lyingSessions.put(uuid, session);
        session.start();
    }

    private void startBlackoutLyingSession(Player p) {
        UUID uuid = p.getUniqueId();
        stopLying(uuid);
        stopSitting(uuid);

        LyingSession session = new LyingSession(p, 0, true);
        lyingSessions.put(uuid, session);
        session.start();
    }

    public boolean isLyingDown(UUID uuid) {
        return lyingSessions.containsKey(uuid);
    }

    public LyingSession getLyingSession(UUID uuid) {
        return lyingSessions.get(uuid);
    }

    public void stopLying(UUID uuid) {
        LyingSession session = lyingSessions.remove(uuid);
        if (session != null) {
            session.cleanup();
        }
    }

    public void forceSitDown(Player p, int durationTicks) {
        UUID uuid = p.getUniqueId();
        if (sittingSeats.containsKey(uuid) || lyingSessions.containsKey(uuid)) return;

        Location sitLoc = p.getLocation().clone();
        World world = sitLoc.getWorld();
        if (world == null) return;

        ArmorStand seat = world.spawn(sitLoc, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setMarker(true);
            stand.setSmall(true);
            stand.setSilent(true);
            stand.setInvulnerable(true);
            stand.setPersistent(false);
        });

        sittingSeats.put(uuid, seat);
        seat.addPassenger(p);
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§e§oVocê perdeu o equilíbrio e sentou no chão..."));

        Bukkit.getScheduler().runTaskLater(BarDrinks.get(), () -> {
            stopSitting(uuid);
        }, durationTicks);
    }

    public boolean isSitting(UUID uuid) {
        return sittingSeats.containsKey(uuid);
    }

    public void stopSitting(UUID uuid) {
        Entity seat = sittingSeats.remove(uuid);
        if (seat != null && seat.isValid()) {
            seat.eject();
            seat.remove();
        }
    }

    public boolean isSeatEntity(Entity entity) {
        return sittingSeats.containsValue(entity);
    }

    public void cleanupPlayer(Player p) {
        UUID uuid = p.getUniqueId();
        stopLying(uuid);
        stopSitting(uuid);
        activeBlackouts.remove(uuid);
        VomitSession vomit = activeVomits.remove(uuid);
        if (vomit != null) {
            vomit.cancelSession();
        }
        wobblePhase.remove(uuid);
    }

    public void cleanupAll() {
        for (UUID uuid : new HashSet<>(lyingSessions.keySet())) {
            stopLying(uuid);
        }
        for (UUID uuid : new HashSet<>(sittingSeats.keySet())) {
            stopSitting(uuid);
        }
        for (VomitSession session : new ArrayList<>(activeVomits.values())) {
            session.cancelSession();
        }
        activeVomits.clear();
        activeBlackouts.clear();
        wobblePhase.clear();
        vomitStainedUntilMs.clear();
    }

    public class LyingSession {
        private final Player player;
        private final Location startLoc;
        private final int durationTicks;
        private boolean lockedMovement;
        private boolean wakeOnMove;
        private BukkitTask task;
        private int elapsedTicks = 0;

        public LyingSession(Player player, int durationTicks, boolean isBlackout) {
            this.player = player;
            this.startLoc = player.getLocation().clone();
            this.durationTicks = durationTicks;
            this.lockedMovement = isBlackout;
            this.wakeOnMove = false;
        }

        public boolean isLockedMovement() {
            return lockedMovement;
        }

        public boolean isWakeOnMove() {
            return wakeOnMove;
        }

        public Location getStartLoc() {
            return startLoc;
        }

        public void unlockMovementAndWaitStep() {
            this.lockedMovement = false;
            this.wakeOnMove = true;
        }

        public void start() {
            Location headLoc = startLoc.clone().add(0, 1.0, 0);
            player.sendBlockChange(headLoc, Material.BARRIER.createBlockData());
            player.setSwimming(true);
            if (!lockedMovement) {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§c§oVocê desabou no chão de tão bêbado..."));
            }

            task = new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isOnline() || player.isDead()) {
                        stopLying(player.getUniqueId());
                        return;
                    }

                    elapsedTicks += 5;
                    if (durationTicks > 0 && elapsedTicks >= durationTicks) {
                        stopLying(player.getUniqueId());
                        return;
                    }

                    Location curHead = startLoc.clone().add(0, 1.0, 0);
                    player.sendBlockChange(curHead, Material.BARRIER.createBlockData());
                    if (!player.isSwimming()) {
                        player.setSwimming(true);
                    }
                }
            }.runTaskTimer(BarDrinks.get(), 5L, 5L);
        }

        public void cleanup() {
            if (task != null) {
                task.cancel();
                task = null;
            }
            if (player != null && player.isOnline()) {
                Location headLoc = startLoc.clone().add(0, 1.0, 0);
                player.sendBlockChange(headLoc, headLoc.getBlock().getBlockData());
                player.setSwimming(false);
            }
        }
    }
}
