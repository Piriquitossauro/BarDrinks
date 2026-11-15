package me.bardrinks.managers;

import me.bardrinks.BarDrinks;
import me.bardrinks.models.DrinkType;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.bukkit.util.Transformation;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BarrelManager {

    private static final double MAX_HOSE_DISTANCE = 7.5;
    private static final long DOUBLE_CLICK_WINDOW_MS = 550L;
    private static final long DRINK_PROTECTION_MS = 60_000L; // 1 minuto

    public static class PlacedBarrel {
        public final UUID id;
        public final String worldName;
        public final int blockX;
        public final int blockY;
        public final int blockZ;
        public final float yaw;

        public int barrelUses; // 0 a 20
        public boolean hasHose;
        public int hoseUses;   // 0 a 100

        // Estado em tempo de execução
        public UUID currentHolder;
        public long holderProtectedUntilMs;

        public UUID displayEntityId;
        public UUID interactionEntityId;
        public UUID leashEntityId;

        public PlacedBarrel(UUID id, String worldName, int blockX, int blockY, int blockZ, float yaw,
                            int barrelUses, boolean hasHose, int hoseUses) {
            this.id = id;
            this.worldName = worldName;
            this.blockX = blockX;
            this.blockY = blockY;
            this.blockZ = blockZ;
            this.yaw = yaw;
            this.barrelUses = barrelUses;
            this.hasHose = hasHose;
            this.hoseUses = hoseUses;
        }

        public Location getBlockLocation() {
            World w = Bukkit.getWorld(worldName);
            if (w == null) return null;
            return new Location(w, blockX, blockY, blockZ, yaw, 0f);
        }

        public Location getCenterLocation() {
            World w = Bukkit.getWorld(worldName);
            if (w == null) return null;
            return new Location(w, blockX + 0.5, blockY + 0.5, blockZ + 0.5, yaw, 0f);
        }

        public int getCurrentModelCmd() {
            if (!hasHose) {
                return DrinkType.CMD_BARRIL_SEM_MANGUEIRA; // 1005
            }
            if (currentHolder != null) {
                return DrinkType.CMD_BARRIL_SENDO_USADO;   // 1007
            }
            return DrinkType.CMD_BARRIL_COM_MANGUEIRA;     // 1006
        }
    }

    private final BarDrinks plugin;
    private final File barrelsFile;
    private FileConfiguration barrelsConfig;

    private final Map<UUID, PlacedBarrel> barrelsById = new ConcurrentHashMap<>();
    private final Map<String, UUID> barrelByBlockKey = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> barrelByEntityId = new ConcurrentHashMap<>();

    // Controle de duplo clique do Barman: PlayerUUID -> (BarrelUUID + Timestamp)
    private final Map<UUID, Long> lastBarmanClickTime = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> lastBarmanClickBarrel = new ConcurrentHashMap<>();
    private final Map<UUID, Long> interactionDebounce = new ConcurrentHashMap<>();

    public BarrelManager(BarDrinks plugin) {
        this.plugin = plugin;
        this.barrelsFile = new File(plugin.getDataFolder(), "barrels.yml");
        loadBarrels();
    }

    private String blockKey(String world, int x, int y, int z) {
        return world + ":" + x + ":" + y + ":" + z;
    }

    public PlacedBarrel getBarrelAtBlock(Block b) {
        if (b == null) return null;
        UUID id = barrelByBlockKey.get(blockKey(b.getWorld().getName(), b.getX(), b.getY(), b.getZ()));
        return id != null ? barrelsById.get(id) : null;
    }

    public PlacedBarrel getBarrelByEntity(Entity entity) {
        if (entity == null) return null;
        UUID id = barrelByEntityId.get(entity.getUniqueId());
        return id != null ? barrelsById.get(id) : null;
    }

    public PlacedBarrel getBarrelById(UUID barrelId) {
        if (barrelId == null) return null;
        return barrelsById.get(barrelId);
    }

    public PlacedBarrel getBarrelHeldBy(UUID playerUuid) {
        for (PlacedBarrel barrel : barrelsById.values()) {
            if (playerUuid.equals(barrel.currentHolder)) {
                return barrel;
            }
        }
        return null;
    }

    /**
     * Busca o barril posicionado mais próximo de um player (até maxDistance blocos) que precise de Refill (< 20 usos).
     */
    public PlacedBarrel findNearestRefillableBarrel(Player p, double maxDistance) {
        Location pLoc = p.getLocation();
        PlacedBarrel closest = null;
        double minDistSq = maxDistance * maxDistance;

        for (PlacedBarrel barrel : barrelsById.values()) {
            if (!barrel.worldName.equals(p.getWorld().getName())) continue;
            if (barrel.barrelUses >= DrinkType.BARRIL_MAX_USES) continue;
            Location bLoc = barrel.getCenterLocation();
            if (bLoc == null) continue;
            double dSq = bLoc.distanceSquared(pLoc);
            if (dSq <= minDistSq) {
                minDistSq = dSq;
                closest = barrel;
            }
        }
        return closest;
    }

    /**
     * Posiciona um novo barril no chão com bloco de BARRIER para física e entidade de interação maior.
     */
    public PlacedBarrel placeBarrel(Block targetBlock, float playerYaw, int initialUses) {
        World world = targetBlock.getWorld();
        UUID id = UUID.randomUUID();
        float snappedYaw = Math.round(playerYaw / 90.0f) * 90.0f;

        PlacedBarrel barrel = new PlacedBarrel(
                id,
                world.getName(),
                targetBlock.getX(),
                targetBlock.getY(),
                targetBlock.getZ(),
                snappedYaw,
                initialUses,
                false,
                0
        );

        barrelsById.put(id, barrel);
        barrelByBlockKey.put(blockKey(barrel.worldName, barrel.blockX, barrel.blockY, barrel.blockZ), id);

        spawnBarrelEntities(barrel);
        saveBarrels();
        return barrel;
    }

    private void spawnBarrelEntities(PlacedBarrel barrel) {
        Location blockLoc = barrel.getBlockLocation();
        if (blockLoc == null) return;
        World world = blockLoc.getWorld();
        if (world == null) return;

        // 1. Bloco de BARRIER real dentro do barril para simular física
        blockLoc.getBlock().setType(Material.BARRIER);

        // 2. Entidade visual ItemDisplay com o modelo 3D (1005, 1006 ou 1007)
        Location displayLoc = barrel.getCenterLocation();
        ItemDisplay display = world.spawn(displayLoc, ItemDisplay.class, d -> {
            d.setItemStack(createBarrelModelStack(barrel.getCurrentModelCmd()));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setPersistent(false);
            d.setInvulnerable(true);
            Transformation tr = d.getTransformation();
            d.setTransformation(tr);
        });
        barrel.displayEntityId = display.getUniqueId();
        barrelByEntityId.put(display.getUniqueId(), barrel.id);

        // 3. Entidade Interaction MAIOR que o bloco de barreira para o player conseguir clicar no barril sem ser bloqueado pela barreira
        Location interLoc = new Location(world, barrel.blockX + 0.5, barrel.blockY - 0.05, barrel.blockZ + 0.5);
        Interaction interaction = world.spawn(interLoc, Interaction.class, inter -> {
            inter.setInteractionWidth(1.30f);
            inter.setInteractionHeight(1.35f);
            inter.setResponsive(true);
            inter.setPersistent(false);
            inter.setInvulnerable(true);
        });
        barrel.interactionEntityId = interaction.getUniqueId();
        barrelByEntityId.put(interaction.getUniqueId(), barrel.id);
    }

    private ItemStack createBarrelModelStack(int cmd) {
        ItemStack stack = new ItemStack(Material.POTION, 1);
        PotionMeta meta = (PotionMeta) stack.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(cmd);
            meta.setBasePotionType(PotionType.WATER);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public void updateBarrelModel(PlacedBarrel barrel) {
        if (barrel.displayEntityId == null) return;
        Entity ent = Bukkit.getEntity(barrel.displayEntityId);
        if (ent instanceof ItemDisplay display && display.isValid()) {
            display.setItemStack(createBarrelModelStack(barrel.getCurrentModelCmd()));
        }
    }

    /**
     * Spawna a âncora invisível dentro do barril e liga uma corda física (Leash) até o player.
     */
    private void attachRopeToPlayer(PlacedBarrel barrel, Player player) {
        removeRope(barrel);
        Location center = barrel.getCenterLocation();
        if (center == null || center.getWorld() == null) return;

        Location anchorLoc = center.clone().subtract(0, 0.15, 0);
        Chicken anchor = center.getWorld().spawn(anchorLoc, Chicken.class, c -> {
            c.setBaby();
            c.setInvisible(true);
            c.setSilent(true);
            c.setAI(false);
            c.setGravity(false);
            c.setInvulnerable(true);
            c.setCollidable(false);
            c.setPersistent(false);
            c.setAgeLock(true);
        });

        anchor.setLeashHolder(player);
        barrel.leashEntityId = anchor.getUniqueId();
        barrelByEntityId.put(anchor.getUniqueId(), barrel.id);
    }

    private void removeRope(PlacedBarrel barrel) {
        if (barrel.leashEntityId != null) {
            barrelByEntityId.remove(barrel.leashEntityId);
            Entity ent = Bukkit.getEntity(barrel.leashEntityId);
            if (ent != null) {
                if (ent instanceof LivingEntity living) {
                    try {
                        living.setLeashHolder(null);
                    } catch (Throwable ignored) {}
                }
                ent.remove();
            }
            barrel.leashEntityId = null;
        }
    }

    public boolean isBarrelLeashEntity(Entity entity) {
        if (entity == null) return false;
        PlacedBarrel b = getBarrelByEntity(entity);
        return b != null && entity.getUniqueId().equals(b.leashEntityId);
    }

    /**
     * Interação principal quando um player clica com o botão direito no Barril.
     */
    public void handleBarrelRightClick(Player player, PlacedBarrel barrel) {
        UUID pUuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        // Debounce de 60ms para evitar disparo duplo entre MAIN_HAND e OFF_HAND
        Long lastInteract = interactionDebounce.get(pUuid);
        if (lastInteract != null && (now - lastInteract) < 60L) {
            return;
        }
        interactionDebounce.put(pUuid, now);

        // 1. Verifica se o jogador está banido do bar (/bardrink ban)
        if (plugin.getRankingManager().isBanned(pUuid)) {
            player.sendMessage("§cVocê está proibido pelo barman de interagir com o barril!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        boolean isBarman = player.hasPermission("bardrinks.admin");
        ItemStack inHand = player.getInventory().getItemInMainHand();

        // =========================================================================
        // ESTADO 1: BARRIL SEM MANGUEIRA (CMD 1005)
        // =========================================================================
        if (!barrel.hasHose) {
            if (!isBarman) {
                player.sendMessage("§cEste barril está sem mangueira! Apenas o barman pode ativá-lo.");
                player.playSound(player.getLocation(), Sound.BLOCK_CHEST_LOCKED, 0.8f, 1.2f);
                return;
            }

            // Barman segurando a Mangueira (CMD 1008) -> 2 cliques rápidos para equipar
            if (DrinkType.isHoseItem(inHand)) {
                int hoseUses = DrinkType.getHoseItemUses(inHand);
                if (hoseUses <= 0) {
                    player.sendMessage("§cEsta mangueira está quebrada (0/100 usos) e fica inutilizável!");
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                    return;
                }

                if (isBarmanDoubleClick(pUuid, barrel.id, now)) {
                    player.getInventory().setItemInMainHand(null);
                    barrel.hasHose = true;
                    barrel.hoseUses = hoseUses;
                    updateBarrelModel(barrel); // Muda para CMD 1006 (barril_com_mangueira)
                    saveBarrels();
                    player.playSound(barrel.getCenterLocation(), Sound.BLOCK_ANVIL_PLACE, 0.6f, 1.5f);
                    player.sendMessage("§aMangueira equipada no barril! (§f" + hoseUses + "/100 usos§a)");
                } else {
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                            new TextComponent("§eClique novamente rápido para equipar a mangueira no barril!"));
                }
                return;
            }

            // Barman de mão sem mangueira -> 2 cliques rápidos para recolher o Barril para o inventário
            if (isBarmanDoubleClick(pUuid, barrel.id, now)) {
                if (!hasInventorySpace(player)) {
                    player.sendMessage("§cSem espaço necessário no inventário");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return;
                }
                ItemStack barrelItem = DrinkType.createBarrelItem(barrel.barrelUses);
                removeBarrelCompletely(barrel);
                player.getInventory().addItem(barrelItem);
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
                player.sendMessage("§aBarril recolhido para o inventário! (§f" + barrel.barrelUses + "/20 doses§a)");
            } else {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        new TextComponent("§eDê 2 cliques rápidos para recolher o barril (§f" + barrel.barrelUses + "/20 doses§e)"));
            }
            return;
        }

        // =========================================================================
        // ESTADO 2 e 3: BARRIL COM MANGUEIRA (CMD 1006 ou 1007)
        // =========================================================================

        // Verifica se o Barman deu 2 cliques rápidos para RETIRAR a mangueira do barril
        if (isBarman && (barrel.currentHolder == null || barrel.currentHolder.equals(pUuid))) {
            if (isBarmanDoubleClick(pUuid, barrel.id, now)) {
                // Se no 1º clique o bocal tinha ido pra hotbar do Barman, recolhe o bocal primeiro
                if (pUuid.equals(barrel.currentHolder)) {
                    disconnectCurrentHolder(barrel, false);
                }

                if (!hasInventorySpace(player)) {
                    player.sendMessage("§cSem espaço necessário no inventário");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return;
                }

                ItemStack detachedHose = DrinkType.createHoseItem(barrel.hoseUses);
                barrel.hasHose = false;
                barrel.hoseUses = 0;
                updateBarrelModel(barrel); // Volta para CMD 1005 (barril_sem_mangueira)
                saveBarrels();

                player.getInventory().addItem(detachedHose);
                player.playSound(barrel.getCenterLocation(), Sound.ITEM_ARMOR_EQUIP_CHAIN, 1.0f, 1.2f);
                player.sendMessage("§aVocê retirou a mangueira do barril!");
                return;
            }
        }

        // Caso o player atual JÁ ESTEJA com a mangueira deste barril -> clicar no barril devolve a mangueira!
        if (pUuid.equals(barrel.currentHolder)) {
            disconnectCurrentHolder(barrel, false);
            player.playSound(barrel.getCenterLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.0f, 1.2f);
            player.sendMessage("§eVocê devolveu a mangueira ao barril.");
            return;
        }

        // Se o player já estiver segurando a mangueira de OUTRO barril, desconecta do outro primeiro
        PlacedBarrel otherHeld = getBarrelHeldBy(pUuid);
        if (otherHeld != null && !otherHeld.id.equals(barrel.id)) {
            disconnectCurrentHolder(otherHeld, false);
        }

        // Caso OUTRO player esteja com a mangueira atualmente (Estado 1007 - barril_sendo_usado)
        if (barrel.currentHolder != null) {
            Player currentHolderPlayer = Bukkit.getPlayer(barrel.currentHolder);
            if (currentHolderPlayer == null || !currentHolderPlayer.isOnline()) {
                disconnectCurrentHolder(barrel, false);
            } else {
                // Verifica se o portador atual está protegido pelo 1 minuto após beber
                if (now < barrel.holderProtectedUntilMs) {
                    long segs = Math.max(1L, (barrel.holderProtectedUntilMs - now) / 1000L);
                    player.sendMessage("§cEste barril está ocupado! §f" + currentHolderPlayer.getName()
                            + " §cestá com a mangueira atualmente (protegido por mais " + segs + "s).");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return;
                }
            }
        }

        // Verifica se o barril tem doses e se a mangueira tem durabilidade
        if (barrel.barrelUses <= 0) {
            player.sendMessage("§cO chopp deste barril acabou (0/20 doses)! O barman precisa fazer um refill.");
            player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 1.0f, 1.0f);
            return;
        }
        if (barrel.hoseUses <= 0) {
            player.sendMessage("§cA mangueira deste barril quebrou (0/100 usos)! O barman precisa trocá-la.");
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            return;
        }

        // Verifica se o player tem espaço disponível na HOTBAR (slots 0 a 8)
        int hotbarSlot = findAvailableHotbarSlot(player);
        if (hotbarSlot == -1) {
            player.sendMessage("§cVocê precisa de pelo menos 1 slot vazio na sua hotbar para pegar a mangueira!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Se tinha outro portador sem proteção de 1 minuto, remove a mangueira dele agora
        if (barrel.currentHolder != null) {
            Player prevHolder = Bukkit.getPlayer(barrel.currentHolder);
            disconnectCurrentHolder(barrel, false);
            if (prevHolder != null && prevHolder.isOnline()) {
                prevHolder.sendMessage("§e" + player.getName() + " pegou a mangueira do barril!");
            }
        }

        // Equipa a boca da mangueira na hotbar do novo portador e liga a corda!
        ItemStack mouthpiece = DrinkType.createConnectedMouthpiece(barrel.id, barrel.barrelUses, barrel.hoseUses);
        player.getInventory().setItem(hotbarSlot, mouthpiece);
        barrel.currentHolder = pUuid;
        barrel.holderProtectedUntilMs = 0L; // Ganha 1 minuto de proteção ao dar o primeiro gole

        updateBarrelModel(barrel); // Muda para CMD 1007 (barril_sendo_usado)
        attachRopeToPlayer(barrel, player);

        player.playSound(barrel.getCenterLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 1.0f, 1.1f);
        player.sendMessage("§aVocê pegou a mangueira do barril! Segure o botão direito com ela para beber.");
    }

    /**
     * Chamado quando o player bebe um gole usando a boca da Mangueira Conectada (CMD 1008).
     */
    public void handleSipFromMouthpiece(Player player, ItemStack mouthpieceItem) {
        UUID barrelId = DrinkType.getConnectedBarrelId(mouthpieceItem);
        PlacedBarrel barrel = getBarrelById(barrelId);

        if (barrel == null || !barrel.hasHose || !player.getUniqueId().equals(barrel.currentHolder)) {
            removeConnectedMouthpiecesFromPlayer(player);
            player.sendMessage("§cEsta mangueira não está mais conectada ao barril.");
            return;
        }

        if (plugin.getRankingManager().isBanned(player.getUniqueId())) {
            disconnectCurrentHolder(barrel, false);
            player.sendMessage("§cVocê foi banido de beber no bar!");
            return;
        }

        if (barrel.barrelUses <= 0) {
            disconnectCurrentHolder(barrel, false);
            player.sendMessage("§cO barril secou!");
            return;
        }

        if (barrel.hoseUses <= 0) {
            disconnectCurrentHolder(barrel, false);
            player.sendMessage("§cA mangueira quebrou por excesso de uso (0/100)!");
            return;
        }

        // Consome 1 dose do barril (de 20) e 1 uso da mangueira (de 100)
        barrel.barrelUses = Math.max(0, barrel.barrelUses - 1);
        barrel.hoseUses = Math.max(0, barrel.hoseUses - 1);

        // Protege o portador atual por 1 minuto contra outros players tentarem puxar a mangueira
        barrel.holderProtectedUntilMs = System.currentTimeMillis() + DRINK_PROTECTION_MS;

        // Adiciona +8% na barra de birita (carregando suavemente em 1,5s) e soma no ranking
        plugin.getDrunkManager().addBirita(player, DrinkType.BARRIL_BIRITA_PER_USE);
        plugin.getRankingManager().addDrink(player);
        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_DRINK, 1.0f, 1.0f);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, 0.7f, 1.15f);

        saveBarrelsAsync();

        // Se o barril zerou as 20 doses
        if (barrel.barrelUses <= 0) {
            player.sendMessage("§6§l[Bar] §eVocê tomou a última dose do barril! O barril esvaziou.");
            player.playSound(barrel.getCenterLocation(), Sound.BLOCK_BREWING_STAND_BREW, 1.0f, 0.7f);
            Bukkit.getScheduler().runTask(plugin, () -> disconnectCurrentHolder(barrel, false));
            return;
        }

        // Se a mangueira zerou os 100 usos
        if (barrel.hoseUses <= 0) {
            player.sendMessage("§c§l[Bar] §cA mangueira atingiu 100 usos e ficou inutilizável!");
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.9f);
            Bukkit.getScheduler().runTask(plugin, () -> disconnectCurrentHolder(barrel, false));
            return;
        }

        // Atualiza o item na mão do player com a nova contagem de doses e barra de durabilidade
        ItemStack updated = DrinkType.createConnectedMouthpiece(barrel.id, barrel.barrelUses, barrel.hoseUses);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            PlayerInventory inv = player.getInventory();
            for (int i = 0; i < 9; i++) {
                ItemStack slotItem = inv.getItem(i);
                if (DrinkType.isConnectedMouthpiece(slotItem)) {
                    inv.setItem(i, updated);
                    break;
                }
            }
        });

        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                new TextComponent("§6🍺 +8% Birita! §7(Barril: §e" + barrel.barrelUses + "/20 §7| Mangueira: §f" + barrel.hoseUses + "/100§7)"));
    }

    /**
     * Desconecta o portador atual da mangueira, remove o bocal da hotbar, remove a corda e volta o barril para CMD 1006.
     */
    public void disconnectCurrentHolder(PlacedBarrel barrel, boolean notifyRopeSnap) {
        if (barrel == null) return;
        UUID holderId = barrel.currentHolder;
        barrel.currentHolder = null;
        barrel.holderProtectedUntilMs = 0L;

        removeRope(barrel);
        updateBarrelModel(barrel); // Volta de 1007 para 1006 (ou 1005 se não tiver mangueira)

        if (holderId != null) {
            Player holder = Bukkit.getPlayer(holderId);
            if (holder != null) {
                removeConnectedMouthpiecesFromPlayer(holder);
                if (notifyRopeSnap && holder.isOnline()) {
                    holder.sendMessage("§cA corda da mangueira esticou demais e voltou para o barril!");
                    holder.playSound(holder.getLocation(), Sound.ENTITY_LEASH_KNOT_BREAK, 1.0f, 1.0f);
                }
            }
        }
    }

    public void removeConnectedMouthpiecesFromPlayer(Player player) {
        if (player == null) return;
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (DrinkType.isConnectedMouthpiece(item)) {
                inv.setItem(i, null);
            }
        }
        ItemStack off = inv.getItemInOffHand();
        if (DrinkType.isConnectedMouthpiece(off)) {
            inv.setItemInOffHand(null);
        }
        if (DrinkType.isConnectedMouthpiece(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
        }
    }

    /**
     * Loop de verificação da corda/distância (a cada 4 ticks):
     * Se o player se afastar demais do barril (> 7.5 blocos) ou a corda romper, a mangueira sai da hotbar e o barril volta ao modelo 1006.
     */
    public void tickBarrels() {
        for (PlacedBarrel barrel : barrelsById.values()) {
            if (barrel.currentHolder == null) continue;

            Player holder = Bukkit.getPlayer(barrel.currentHolder);
            Location center = barrel.getCenterLocation();
            if (holder == null || !holder.isOnline() || holder.isDead() || center == null
                    || !holder.getWorld().equals(center.getWorld())) {
                disconnectCurrentHolder(barrel, false);
                continue;
            }

            // Verifica se o player ainda tem o bocal na hotbar
            boolean stillHasMouthpiece = false;
            for (int i = 0; i < 9; i++) {
                ItemStack item = holder.getInventory().getItem(i);
                if (DrinkType.isConnectedMouthpiece(item) && barrel.id.equals(DrinkType.getConnectedBarrelId(item))) {
                    stillHasMouthpiece = true;
                    break;
                }
            }
            if (!stillHasMouthpiece) {
                disconnectCurrentHolder(barrel, false);
                continue;
            }

            // Se afastou além do limite da corda -> rompe a corda e recolhe a mangueira
            if (holder.getLocation().distanceSquared(center) > (MAX_HOSE_DISTANCE * MAX_HOSE_DISTANCE)) {
                disconnectCurrentHolder(barrel, true);
                continue;
            }

            // Garante que a âncora da corda continua ligada ao player
            if (barrel.leashEntityId != null) {
                Entity anchor = Bukkit.getEntity(barrel.leashEntityId);
                if (anchor instanceof LivingEntity living && (!living.isLeashed() || !holder.equals(living.getLeashHolder()))) {
                    living.setLeashHolder(holder);
                }
            }
        }
    }

    private int findAvailableHotbarSlot(Player player) {
        PlayerInventory inv = player.getInventory();
        int held = inv.getHeldItemSlot();
        ItemStack heldItem = inv.getItem(held);
        if (heldItem == null || heldItem.getType() == Material.AIR) {
            return held;
        }
        for (int i = 0; i < 9; i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType() == Material.AIR) {
                return i;
            }
        }
        return -1;
    }

    public boolean hasInventorySpace(Player player) {
        return player.getInventory().firstEmpty() != -1;
    }

    private boolean isBarmanDoubleClick(UUID playerUuid, UUID barrelId, long now) {
        UUID lastBarrel = lastBarmanClickBarrel.get(playerUuid);
        Long lastTime = lastBarmanClickTime.get(playerUuid);

        if (barrelId.equals(lastBarrel) && lastTime != null && (now - lastTime) <= DOUBLE_CLICK_WINDOW_MS) {
            lastBarmanClickTime.remove(playerUuid);
            lastBarmanClickBarrel.remove(playerUuid);
            return true;
        }

        lastBarmanClickBarrel.put(playerUuid, barrelId);
        lastBarmanClickTime.put(playerUuid, now);
        return false;
    }

    public void removeBarrelCompletely(PlacedBarrel barrel) {
        if (barrel == null) return;
        disconnectCurrentHolder(barrel, false);
        despawnBarrelEntities(barrel);
        barrelsById.remove(barrel.id);
        barrelByBlockKey.remove(blockKey(barrel.worldName, barrel.blockX, barrel.blockY, barrel.blockZ));
        saveBarrels();
    }

    private void despawnBarrelEntities(PlacedBarrel barrel) {
        removeRope(barrel);
        if (barrel.displayEntityId != null) {
            barrelByEntityId.remove(barrel.displayEntityId);
            Entity d = Bukkit.getEntity(barrel.displayEntityId);
            if (d != null) d.remove();
            barrel.displayEntityId = null;
        }
        if (barrel.interactionEntityId != null) {
            barrelByEntityId.remove(barrel.interactionEntityId);
            Entity i = Bukkit.getEntity(barrel.interactionEntityId);
            if (i != null) i.remove();
            barrel.interactionEntityId = null;
        }
        Location blockLoc = barrel.getBlockLocation();
        if (blockLoc != null && blockLoc.getBlock().getType() == Material.BARRIER) {
            blockLoc.getBlock().setType(Material.AIR);
        }
    }

    public synchronized void loadBarrels() {
        if (!barrelsFile.exists()) {
            try {
                barrelsFile.getParentFile().mkdirs();
                barrelsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Não foi possível criar barrels.yml!");
            }
        }
        barrelsConfig = YamlConfiguration.loadConfiguration(barrelsFile);
        barrelsById.clear();
        barrelByBlockKey.clear();
        barrelByEntityId.clear();

        ConfigurationSection sec = barrelsConfig.getConfigurationSection("barrels");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    String path = "barrels." + key;
                    String world = barrelsConfig.getString(path + ".world", "world");
                    int x = barrelsConfig.getInt(path + ".x");
                    int y = barrelsConfig.getInt(path + ".y");
                    int z = barrelsConfig.getInt(path + ".z");
                    float yaw = (float) barrelsConfig.getDouble(path + ".yaw", 0.0);
                    int barrelUses = barrelsConfig.getInt(path + ".barrelUses", DrinkType.BARRIL_MAX_USES);
                    boolean hasHose = barrelsConfig.getBoolean(path + ".hasHose", false);
                    int hoseUses = barrelsConfig.getInt(path + ".hoseUses", 0);

                    PlacedBarrel barrel = new PlacedBarrel(id, world, x, y, z, yaw, barrelUses, hasHose, hoseUses);
                    barrelsById.put(id, barrel);
                    barrelByBlockKey.put(blockKey(world, x, y, z), id);
                    spawnBarrelEntities(barrel);
                } catch (Exception ignored) {}
            }
        }
    }

    public synchronized void saveBarrels() {
        if (barrelsConfig == null) {
            barrelsConfig = new YamlConfiguration();
        }
        barrelsConfig.set("barrels", null);
        for (PlacedBarrel barrel : barrelsById.values()) {
            String path = "barrels." + barrel.id.toString();
            barrelsConfig.set(path + ".world", barrel.worldName);
            barrelsConfig.set(path + ".x", barrel.blockX);
            barrelsConfig.set(path + ".y", barrel.blockY);
            barrelsConfig.set(path + ".z", barrel.blockZ);
            barrelsConfig.set(path + ".yaw", barrel.yaw);
            barrelsConfig.set(path + ".barrelUses", barrel.barrelUses);
            barrelsConfig.set(path + ".hasHose", barrel.hasHose);
            barrelsConfig.set(path + ".hoseUses", barrel.hoseUses);
        }
        try {
            barrelsConfig.save(barrelsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Não foi possível salvar barrels.yml!");
        }
    }

    public void saveBarrelsAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::saveBarrels);
    }

    public void cleanupOnDisable() {
        for (PlacedBarrel barrel : barrelsById.values()) {
            disconnectCurrentHolder(barrel, false);
            removeRope(barrel);
            if (barrel.displayEntityId != null) {
                Entity d = Bukkit.getEntity(barrel.displayEntityId);
                if (d != null) d.remove();
            }
            if (barrel.interactionEntityId != null) {
                Entity i = Bukkit.getEntity(barrel.interactionEntityId);
                if (i != null) i.remove();
            }
        }
        saveBarrels();
    }
}
