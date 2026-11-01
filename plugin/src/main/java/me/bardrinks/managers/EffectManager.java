package me.bardrinks.managers;

import io.papermc.paper.entity.TeleportFlag;
import me.bardrinks.BarDrinks;
import me.bardrinks.models.DrunkStage;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
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

    // Players atingidos por vômito -> Timestamp de término dos 10 segundos exatos
    private final Map<UUID, Long> vomitStainedUntilMs = new ConcurrentHashMap<>();

    /**
     * Tick rápido (a cada 2 ticks = 0.1s) para:
     * - Balanço suave (sem flutuar no ar)
     * - Partículas de bêbado (Bebo 2 e Bebo 3, sem efeito LUCK)
     * - Efeito de jogadores sujos de vômito (partículas + afastar mobs pacíficos por 10s)
     */
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

        // 3. Balanço suave (Wobble) no Bebo 2 e Bebo 3
        if (stage == DrunkStage.BEBO_2) {
            applySmoothWobble(p, 0.045);
        } else if (stage == DrunkStage.BEBO_3) {
            applySmoothWobble(p, 0.085);
        } else {
            wobblePhase.remove(uuid);
        }
    }

    /**
     * Checagem a cada 2 segundos (40 ticks) para:
     * - Náusea no Bebo 3
     * - Chance de Vômito (1 em 50 a cada 2 segundos)
     * - Chance de Sentar ou Deitar sozinho no Bebo 3
     */
    public void tickPeriodicEffects(Player p, double birita) {
        UUID uuid = p.getUniqueId();
        DrunkStage stage = DrunkStage.fromPercentage(birita);

        if (stage != DrunkStage.BEBO_3) {
            return;
        }

        // Náusea constante no Bebo 3
        p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 160, 0, false, false, false));

        // Se já estiver vomitando, deitado, sentado ou dormindo, não dispara nova ação
        if (isVomiting(uuid) || isLyingDown(uuid) || isSitting(uuid) || p.isInsideVehicle() || p.isSleeping()) {
            return;
        }

        // Chance de Vômito: 1 em 50 a cada 2 segundos
        if (random.nextInt(50) == 0) {
            triggerVomit(p);
            return;
        }

        // Forçar Sentar ou Deitar (sem GSit) se estiver no chão
        trySitOrLay(p);
    }

    /**
     * Balanço suave usando curva senoidal:
     * - Só funciona se o player estiver firme no chão (isOnGround), sem nadar/voar/veículo.
     * - NUNCA altera a velocidade Y, impedindo que o player flutue no ar!
     */
    private void applySmoothWobble(Player p, double maxStrength) {
        UUID uuid = p.getUniqueId();

        if (!p.isOnGround() || p.isInsideVehicle() || p.isSleeping() || p.isSwimming()
                || p.isFlying() || p.isGliding() || p.isClimbing() || isLyingDown(uuid) || isSitting(uuid)) {
            return;
        }

        double phase = wobblePhase.getOrDefault(uuid, random.nextDouble() * Math.PI * 2.0);
        // Avança a fase suavemente
        phase += 0.22 + (random.nextDouble() * 0.08);
        wobblePhase.put(uuid, phase);

        // Combina duas ondas senoidais para parecer um passo cambaleante orgânico
        double wave = Math.sin(phase) * 0.7 + Math.sin(phase * 0.45) * 0.3;
        if (Math.abs(wave) < 0.15) return;

        Location loc = p.getLocation();
        double radYaw = Math.toRadians(loc.getYaw());
        // Vetor perpendicular (lateral direita/esquerda de onde o player olha)
        double sideX = Math.cos(radYaw);
        double sideZ = Math.sin(radYaw);

        Vector currentVel = p.getVelocity();
        double pushX = sideX * wave * maxStrength;
        double pushZ = sideZ * wave * maxStrength;

        // Mantém o Y exatamente como está para nunca flutuar
        Vector newVel = new Vector(
                currentVel.getX() + pushX,
                currentVel.getY(),
                currentVel.getZ() + pushZ
        );
        p.setVelocity(newVel);
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

        // Se estava sentado ou deitado, levanta para vomitar
        stopSitting(uuid);
        stopLying(uuid);

        VomitSession session = new VomitSession(p);
        activeVomits.put(uuid, session);
        session.start();
    }

    /**
     * Marca um jogador como atingido por vômito por exatos 10 segundos.
     */
    public void markPlayerVomitStained(Player target) {
        UUID uuid = target.getUniqueId();
        long now = System.currentTimeMillis();
        Long prev = vomitStainedUntilMs.get(uuid);
        // Se era a primeira vez ou já tinha expirado, avisa o player
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

    /**
     * Qualquer mob pacífico mantém distância do player sujo de vômito durante os 10 segundos.
     */
    private void repelPassiveMobs(Player p) {
        Location pLoc = p.getLocation();
        for (Entity entity : p.getNearbyEntities(7.0, 4.0, 7.0)) {
            if (!(entity instanceof Mob mob)) continue;
            if (entity instanceof Enemy || entity instanceof Monster || entity instanceof Boss) continue;

            // É um mob pacífico (Animals, Villager, WanderingTrader, Allay, Fish, Golem, etc.)
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

            // Ponto de fuga a 8 blocos de distância do player
            Location fleeTarget = pLoc.clone().add(dirX * 8.5, 0, dirZ * 8.5);
            fleeTarget.setY(mLoc.getY());
            try {
                mob.getPathfinder().moveTo(fleeTarget, 1.45);
            } catch (Throwable ignored) {}

            // Se estiver a menos de 5.5 blocos, aplica impulso suave para garantir que mantenha distância
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
        private int vomitTicks = 0; // 4 segundos = 80 ticks após chegar no pitch 90

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

                    // FASE 1: Força a câmera do player a descer até olhar totalmente para baixo (pitch = 90)
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
                            // Perde 50% de enchimento da barra ao vomitar
                            BarDrinks.get().getDrunkManager().removeBirita(player, 50.0);
                            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 0.7f, 0.5f);
                        }
                        return;
                    }

                    // FASE 2: Jato de vômito por 4 segundos (80 ticks) a partir do momento que chegou na base
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

            // Origem na altura da boca do player
            Location mouth = eye.clone().subtract(0, 0.18, 0);
            double floorY = p.getLocation().getY();
            double totalDrop = Math.max(0.5, mouth.getY() - floorY);

            // Pitch: 90° = olhando reto para baixo (0 blocos de distância horizontal)
            //        0°  = olhando reto para frente (até 3 blocos de distância horizontal)
            float rawPitch = eye.getPitch();
            float effectivePitch = Math.max(0.0f, Math.min(90.0f, rawPitch));
            double maxHorizontalDist = 3.0 * ((90.0 - effectivePitch) / 90.0);

            double radYaw = Math.toRadians(eye.getYaw());
            double forwardX = -Math.sin(radYaw);
            double forwardZ = Math.cos(radYaw);

            int steps = 10;
            for (int i = 1; i <= steps; i++) {
                double s = (double) i / steps;

                // Distância horizontal proporcional ao progresso s
                double horizDist = maxHorizontalDist * s;

                // Curva parabólica vertical: y(s) = mouthY - totalDrop * (s^2)
                // Se estiver olhando mais reto, dá um leve arco inicial antes de cair
                double arcBoost = (maxHorizontalDist / 3.0) * 0.25 * Math.sin(s * Math.PI);
                double y = mouth.getY() - (totalDrop * (s * s)) + arcBoost;

                double spread = 0.06 * s;
                double px = mouth.getX() + forwardX * horizDist + (random.nextDouble() - 0.5) * spread;
                double py = y + (random.nextDouble() - 0.5) * 0.05;
                double pz = mouth.getZ() + forwardZ * horizDist + (random.nextDouble() - 0.5) * spread;

                Particle.DustOptions color = VOMIT_COLORS[(i + vomitTicks) % VOMIT_COLORS.length];
                world.spawnParticle(Particle.DUST, px, py, pz, 1, 0, 0, 0, 0, color);

                // Verifica se acertou outros players no trajeto da parábola
                Location pointLoc = new Location(world, px, py, pz);
                for (Entity nearby : world.getNearbyEntities(pointLoc, 0.55, 0.65, 0.55)) {
                    if (nearby instanceof Player other && !other.getUniqueId().equals(p.getUniqueId())) {
                        markPlayerVomitStained(other);
                    }
                }
            }

            // Se estiver vomitando para baixo no chão perto dos próprios pés (distância <= 0.85 blocos), acerta a si próprio
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

        // Cooldown de 25 segundos entre quedas
        if (now - last < 25000L) return;

        double roll = random.nextDouble();
        if (roll < 0.04) {
            lastSitOrLayMs.put(uuid, now);
            forceLayDown(p, 120); // Deita por 6 segundos (120 ticks)
        } else if (roll < 0.08) {
            lastSitOrLayMs.put(uuid, now);
            forceSitDown(p, 120); // Senta por 6 segundos (120 ticks)
        }
    }

    /**
     * Força o player a deitar no chão usando a mesma mecânica da Preguiça (bloco BARRIER falso + setSwimming).
     */
    public void forceLayDown(Player p, int durationTicks) {
        UUID uuid = p.getUniqueId();
        if (lyingSessions.containsKey(uuid) || sittingSeats.containsKey(uuid)) return;

        LyingSession session = new LyingSession(p, durationTicks);
        lyingSessions.put(uuid, session);
        session.start();
    }

    public boolean isLyingDown(UUID uuid) {
        return lyingSessions.containsKey(uuid);
    }

    public void stopLying(UUID uuid) {
        LyingSession session = lyingSessions.remove(uuid);
        if (session != null) {
            session.cleanup();
        }
    }

    /**
     * Força o player a sentar no chão criando um ArmorStand invisível e sem hitbox sob ele.
     */
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
        wobblePhase.clear();
        vomitStainedUntilMs.clear();
    }

    public class LyingSession {
        private final Player player;
        private final Location startLoc;
        private final int durationTicks;
        private BukkitTask task;
        private int elapsedTicks = 0;

        public LyingSession(Player player, int durationTicks) {
            this.player = player;
            this.startLoc = player.getLocation().clone();
            this.durationTicks = durationTicks;
        }

        public void start() {
            Location headLoc = startLoc.clone().add(0, 1.0, 0);
            player.sendBlockChange(headLoc, Material.BARRIER.createBlockData());
            player.setSwimming(true);
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§c§oVocê desabou no chão de tão bêbado..."));

            task = new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isOnline() || player.isDead()) {
                        stopLying(player.getUniqueId());
                        return;
                    }

                    elapsedTicks += 5;
                    if (elapsedTicks >= durationTicks) {
                        stopLying(player.getUniqueId());
                        return;
                    }

                    // Mantém a barreira falsa acima do player e a pose deitada (igual ao Pecado da Preguiça)
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
