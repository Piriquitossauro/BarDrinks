package me.bardrinks.managers;

import me.bardrinks.BarDrinks;
import me.bardrinks.models.DrinkType;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.bukkit.util.Transformation;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FurnitureManager {

    private static final long DOUBLE_CLICK_WINDOW_MS = 550L;

    public enum FurnitureType {
        CHAIR(DrinkType.CMD_CADEIRA, "§6Cadeira de Madeira"),
        TABLE(DrinkType.CMD_MESA, "§6Mesa de Madeira");

        private final int cmd;
        private final String displayName;

        FurnitureType(int cmd, String displayName) {
            this.cmd = cmd;
            this.displayName = displayName;
        }

        public int getCmd() {
            return cmd;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public static class PlacedFurniture {
        public final UUID id;
        public final FurnitureType type;
        public final String worldName;
        public final int blockX;
        public final int blockY;
        public final int blockZ;
        public final float yaw;

        public UUID displayEntityId;
        public UUID interactionEntityId;
        public UUID seatEntityId;

        public PlacedFurniture(UUID id, FurnitureType type, String worldName, int blockX, int blockY, int blockZ, float yaw) {
            this.id = id;
            this.type = type;
            this.worldName = worldName;
            this.blockX = blockX;
            this.blockY = blockY;
            this.blockZ = blockZ;
            this.yaw = yaw;
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
    }

    private final BarDrinks plugin;
    private final File file;
    private FileConfiguration config;

    private final Map<UUID, PlacedFurniture> furnitureById = new ConcurrentHashMap<>();
    private final Map<String, UUID> furnitureByBlockKey = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> furnitureByEntityId = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> chairSeatByPlayer = new ConcurrentHashMap<>();

    private final Map<UUID, Long> lastBarmanClickTime = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> lastBarmanClickFurniture = new ConcurrentHashMap<>();
    private final Map<UUID, Long> interactDebounce = new ConcurrentHashMap<>();

    public FurnitureManager(BarDrinks plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "furniture.yml");
        loadFurniture();
    }

    private String blockKey(String world, int x, int y, int z) {
        return world + ":" + x + ":" + y + ":" + z;
    }

    public PlacedFurniture getFurnitureAtBlock(Block b) {
        if (b == null) return null;
        UUID id = furnitureByBlockKey.get(blockKey(b.getWorld().getName(), b.getX(), b.getY(), b.getZ()));
        return id != null ? furnitureById.get(id) : null;
    }

    public PlacedFurniture getFurnitureByEntity(Entity entity) {
        if (entity == null) return null;
        UUID id = furnitureByEntityId.get(entity.getUniqueId());
        return id != null ? furnitureById.get(id) : null;
    }

    public boolean hasSolidGroundBelow(Block targetBlock) {
        if (targetBlock == null) return false;
        Block below = targetBlock.getRelative(BlockFace.DOWN);
        Material mat = below.getType();
        if (!mat.isSolid() || below.isLiquid()) return false;
        // Não permite empilhar móvel em cima de bebidas apoiadas ou de outra cadeira
        if (plugin.getTableDrinkManager().getClusterAtBlock(below) != null) return false;
        PlacedFurniture belowFurn = getFurnitureAtBlock(below);
        return belowFurn == null || belowFurn.type != FurnitureType.CHAIR;
    }

    public PlacedFurniture placeFurniture(FurnitureType type, Block targetBlock, float playerYaw) {
        World world = targetBlock.getWorld();
        UUID id = UUID.randomUUID();
        float snappedYaw = Math.round(playerYaw / 90.0f) * 90.0f;

        PlacedFurniture furn = new PlacedFurniture(
                id,
                type,
                world.getName(),
                targetBlock.getX(),
                targetBlock.getY(),
                targetBlock.getZ(),
                snappedYaw
        );

        furnitureById.put(id, furn);
        furnitureByBlockKey.put(blockKey(furn.worldName, furn.blockX, furn.blockY, furn.blockZ), id);

        spawnFurnitureEntities(furn);
        saveFurnitureAsync();
        return furn;
    }

    private void spawnFurnitureEntities(PlacedFurniture furn) {
        Location blockLoc = furn.getBlockLocation();
        if (blockLoc == null || blockLoc.getWorld() == null) return;
        World world = blockLoc.getWorld();

        // 1. Bloco de BARRIER para dar colisão física (e permitir apoiar bebidas no topo da Mesa)
        blockLoc.getBlock().setType(Material.BARRIER);

        // 2. ItemDisplay com o modelo 3D (CMD 1009 = Cadeira, CMD 1010 = Mesa)
        Location displayLoc = furn.getCenterLocation();
        ItemStack stack = new ItemStack(Material.POTION, 1);
        PotionMeta meta = (PotionMeta) stack.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(furn.type.getCmd());
            meta.setBasePotionType(PotionType.WATER);
            stack.setItemMeta(meta);
        }

        ItemDisplay display = world.spawn(displayLoc, ItemDisplay.class, d -> {
            d.setItemStack(stack);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setPersistent(false);
            d.setInvulnerable(true);
            Transformation tr = d.getTransformation();
            d.setTransformation(tr);
        });
        furn.displayEntityId = display.getUniqueId();
        furnitureByEntityId.put(display.getUniqueId(), furn.id);

        // 3. Entidade Interaction (1.04 x 1.04) para capturar cliques no móvel
        Location interLoc = new Location(world, furn.blockX + 0.5, furn.blockY, furn.blockZ + 0.5);
        Interaction interaction = world.spawn(interLoc, Interaction.class, inter -> {
            inter.setInteractionWidth(1.04f);
            inter.setInteractionHeight(1.04f);
            inter.setResponsive(true);
            inter.setPersistent(false);
            inter.setInvulnerable(true);
        });
        furn.interactionEntityId = interaction.getUniqueId();
        furnitureByEntityId.put(interaction.getUniqueId(), furn.id);
    }

    /**
     * Interação ao clicar com o botão direito em uma Cadeira ou Mesa.
     */
    public void handleFurnitureRightClick(Player player, PlacedFurniture furn) {
        UUID pUuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        Long last = interactDebounce.get(pUuid);
        if (last != null && (now - last) < 65L) return;
        interactDebounce.put(pUuid, now);

        ItemStack inHand = player.getInventory().getItemInMainHand();

        // Se for uma MESA e o player estiver segurando uma Bebida ou um Barril -> apoia em cima da mesa!
        if (furn.type == FurnitureType.TABLE) {
            DrinkType handDrink = DrinkType.getDrinkTypeFromItem(inHand);
            Location bLoc = furn.getBlockLocation();
            if (bLoc != null) {
                Block aboveTable = bLoc.getBlock().getRelative(BlockFace.UP);
                if (handDrink != null) {
                    plugin.getTableDrinkManager().handlePlaceOrInteractAtBlock(player, aboveTable);
                    return;
                }
                if (DrinkType.isBarrelItem(inHand)) {
                    if (!aboveTable.getType().isAir() && !aboveTable.isReplaceable()) {
                        player.sendMessage("§cJá existe algo em cima desta mesa!");
                        return;
                    }
                    int uses = DrinkType.getBarrelItemUses(inHand);
                    inHand.setAmount(inHand.getAmount() - 1);
                    plugin.getBarrelManager().placeBarrel(aboveTable, player.getLocation().getYaw(), uses);
                    player.playSound(aboveTable.getLocation(), Sound.BLOCK_WOOD_PLACE, 1.0f, 0.8f);
                    player.sendMessage("§aBarril de Chopp posicionado sobre a mesa! (§f" + uses + "/20 doses§a)");
                    return;
                }
            }
        }

        boolean isBarman = plugin.isBarman(player);

        // ESTRITAMENTE PROIBIDO para quem não é Barman ou OP remover cadeiras e mesas:
        // Somente Barman/OP dando 2 cliques rápidos recolhe a Cadeira ou a Mesa
        if (isBarman && isBarmanDoubleClick(pUuid, furn.id, now)) {
            if (isSittingOnChair(pUuid)) {
                dismountChair(pUuid);
            }
            if (furn.type == FurnitureType.TABLE) {
                Location bLoc = furn.getBlockLocation();
                if (bLoc != null) {
                    Block above = bLoc.getBlock().getRelative(BlockFace.UP);
                    if (plugin.getBarrelManager().getBarrelAtBlock(above) != null) {
                        player.sendMessage("§cRetire o barril de cima da mesa antes de recolhê-la!");
                        return;
                    }
                    TableDrinkManager.DrinkCluster clusterAbove = plugin.getTableDrinkManager().getClusterAtBlock(above);
                    if (clusterAbove != null) {
                        plugin.getTableDrinkManager().dropAllAndRemoveCluster(clusterAbove);
                    }
                }
            }

            ItemStack returnItem = (furn.type == FurnitureType.CHAIR)
                    ? DrinkType.createChairItem(1)
                    : DrinkType.createTableItem(1);

            if (!canFitItem(player, returnItem)) {
                player.sendMessage("§cSem espaço necessário no inventário");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            removeFurnitureCompletely(furn);
            player.getInventory().addItem(returnItem);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.9f, 1.1f);
            player.sendMessage("§aVocê recolheu 1x " + furn.type.getDisplayName() + "§a!");
            return;
        }

        // Se for uma MESA e deu apenas 1 clique
        if (furn.type == FurnitureType.TABLE) {
            if (isBarman) {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        new TextComponent("§eDê 2 cliques rápidos para recolher a mesa"));
            }
            return;
        }

        // =========================================================================
        // CADEIRA (CMD 1009): Sentar na Cadeira
        // =========================================================================
        if (furn.type == FurnitureType.CHAIR) {
            if (furn.seatEntityId != null) {
                Entity existingSeat = Bukkit.getEntity(furn.seatEntityId);
                if (existingSeat != null && existingSeat.isValid() && !existingSeat.getPassengers().isEmpty()) {
                    if (!existingSeat.getPassengers().contains(player)) {
                        player.sendMessage("§cJá tem alguém sentado nesta cadeira!");
                    }
                    return;
                } else if (existingSeat != null) {
                    existingSeat.remove();
                    furn.seatEntityId = null;
                }
            }

            sitOnChair(player, furn);
        }
    }

    private void sitOnChair(Player player, PlacedFurniture chair) {
        // Se já estava sentado em outra cadeira, limpa primeiro
        dismountChair(player.getUniqueId());

        Location blockLoc = chair.getBlockLocation();
        if (blockLoc == null || blockLoc.getWorld() == null) return;

        // Altura do assento da cadeira (Y + 0.55 para alinhar com o estofado do Cadeira.json)
        Location seatLoc = new Location(
                blockLoc.getWorld(),
                chair.blockX + 0.5,
                chair.blockY + 0.55,
                chair.blockZ + 0.5,
                chair.yaw,
                0f
        );

        ArmorStand seat = blockLoc.getWorld().spawn(seatLoc, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setMarker(true);
            stand.setSmall(true);
            stand.setSilent(true);
            stand.setInvulnerable(true);
            stand.setPersistent(false);
        });

        chair.seatEntityId = seat.getUniqueId();
        furnitureByEntityId.put(seat.getUniqueId(), chair.id);
        chairSeatByPlayer.put(player.getUniqueId(), chair.id);

        seat.addPassenger(player);
        player.playSound(seatLoc, Sound.BLOCK_WOOD_STEP, 0.8f, 1.1f);
    }

    public boolean isSittingOnChair(UUID playerUuid) {
        return chairSeatByPlayer.containsKey(playerUuid);
    }

    public void dismountChair(UUID playerUuid) {
        UUID chairId = chairSeatByPlayer.remove(playerUuid);
        if (chairId == null) return;

        PlacedFurniture chair = furnitureById.get(chairId);
        if (chair != null && chair.seatEntityId != null) {
            furnitureByEntityId.remove(chair.seatEntityId);
            Entity seat = Bukkit.getEntity(chair.seatEntityId);
            if (seat != null && seat.isValid()) {
                seat.eject();
                seat.remove();
            }
            chair.seatEntityId = null;
        }

        // Posiciona o player em segurança em cima da cadeira (Y + 1.05) para nunca afundar no chão
        Player p = Bukkit.getPlayer(playerUuid);
        if (p != null && p.isOnline() && !p.isDead() && chair != null) {
            Location bLoc = chair.getBlockLocation();
            if (bLoc != null) {
                Location safeStand = new Location(
                        bLoc.getWorld(),
                        chair.blockX + 0.5,
                        chair.blockY + 1.05,
                        chair.blockZ + 0.5,
                        p.getLocation().getYaw(),
                        p.getLocation().getPitch()
                );
                p.setFallDistance(0f);
                p.teleport(safeStand);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (p.isOnline() && !p.isDead()) {
                        Location retry = safeStand.clone();
                        retry.setYaw(p.getLocation().getYaw());
                        retry.setPitch(p.getLocation().getPitch());
                        p.setFallDistance(0f);
                        p.teleport(retry);
                    }
                }, 1L);
            }
        }
    }

    public boolean canFitItem(Player player, ItemStack itemToGive) {
        if (player.getInventory().firstEmpty() != -1) return true;
        for (ItemStack slot : player.getInventory().getStorageContents()) {
            if (slot != null && slot.isSimilar(itemToGive) && slot.getAmount() + itemToGive.getAmount() <= 64) {
                return true;
            }
        }
        return false;
    }

    private boolean isBarmanDoubleClick(UUID playerUuid, UUID furnId, long now) {
        UUID lastFurn = lastBarmanClickFurniture.get(playerUuid);
        Long lastTime = lastBarmanClickTime.get(playerUuid);

        if (furnId.equals(lastFurn) && lastTime != null && (now - lastTime) <= DOUBLE_CLICK_WINDOW_MS) {
            lastBarmanClickTime.remove(playerUuid);
            lastBarmanClickFurniture.remove(playerUuid);
            return true;
        }

        lastBarmanClickFurniture.put(playerUuid, furnId);
        lastBarmanClickTime.put(playerUuid, now);
        return false;
    }

    public void removeFurnitureCompletely(PlacedFurniture furn) {
        if (furn == null) return;
        despawnFurnitureEntities(furn);
        furnitureById.remove(furn.id);
        furnitureByBlockKey.remove(blockKey(furn.worldName, furn.blockX, furn.blockY, furn.blockZ));
        saveFurnitureAsync();
    }

    private void despawnFurnitureEntities(PlacedFurniture furn) {
        if (furn.seatEntityId != null) {
            furnitureByEntityId.remove(furn.seatEntityId);
            Entity s = Bukkit.getEntity(furn.seatEntityId);
            if (s != null) {
                for (Entity pass : s.getPassengers()) {
                    chairSeatByPlayer.remove(pass.getUniqueId());
                }
                s.eject();
                s.remove();
            }
            furn.seatEntityId = null;
        }
        if (furn.displayEntityId != null) {
            furnitureByEntityId.remove(furn.displayEntityId);
            Entity d = Bukkit.getEntity(furn.displayEntityId);
            if (d != null) d.remove();
            furn.displayEntityId = null;
        }
        if (furn.interactionEntityId != null) {
            furnitureByEntityId.remove(furn.interactionEntityId);
            Entity i = Bukkit.getEntity(furn.interactionEntityId);
            if (i != null) i.remove();
            furn.interactionEntityId = null;
        }
        Location blockLoc = furn.getBlockLocation();
        if (blockLoc != null && blockLoc.getBlock().getType() == Material.BARRIER) {
            blockLoc.getBlock().setType(Material.AIR);
        }
    }

    /**
     * Auto-recuperação caso a chunk descarregue e recarregue.
     */
    public void tickFurniture() {
        for (PlacedFurniture furn : furnitureById.values()) {
            World w = Bukkit.getWorld(furn.worldName);
            if (w == null || !w.isChunkLoaded(furn.blockX >> 4, furn.blockZ >> 4)) continue;

            Entity display = furn.displayEntityId != null ? Bukkit.getEntity(furn.displayEntityId) : null;
            Entity inter = furn.interactionEntityId != null ? Bukkit.getEntity(furn.interactionEntityId) : null;
            if (display == null || !display.isValid() || inter == null || !inter.isValid()) {
                if (display != null) {
                    furnitureByEntityId.remove(display.getUniqueId());
                    display.remove();
                }
                if (inter != null) {
                    furnitureByEntityId.remove(inter.getUniqueId());
                    inter.remove();
                }
                spawnFurnitureEntities(furn);
            }
        }
    }

    public synchronized void loadFurniture() {
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Não foi possível criar furniture.yml!");
            }
        }
        config = YamlConfiguration.loadConfiguration(file);
        furnitureById.clear();
        furnitureByBlockKey.clear();
        furnitureByEntityId.clear();

        ConfigurationSection sec = config.getConfigurationSection("furniture");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    String path = "furniture." + key;
                    FurnitureType type = FurnitureType.valueOf(config.getString(path + ".type", "CHAIR"));
                    String world = config.getString(path + ".world", "world");
                    int x = config.getInt(path + ".x");
                    int y = config.getInt(path + ".y");
                    int z = config.getInt(path + ".z");
                    float yaw = (float) config.getDouble(path + ".yaw", 0.0);

                    PlacedFurniture furn = new PlacedFurniture(id, type, world, x, y, z, yaw);
                    furnitureById.put(id, furn);
                    furnitureByBlockKey.put(blockKey(world, x, y, z), id);
                    spawnFurnitureEntities(furn);
                } catch (Exception ignored) {}
            }
        }
    }

    public synchronized void saveFurniture() {
        if (config == null) {
            config = new YamlConfiguration();
        }
        config.set("furniture", null);
        for (PlacedFurniture furn : furnitureById.values()) {
            String path = "furniture." + furn.id.toString();
            config.set(path + ".type", furn.type.name());
            config.set(path + ".world", furn.worldName);
            config.set(path + ".x", furn.blockX);
            config.set(path + ".y", furn.blockY);
            config.set(path + ".z", furn.blockZ);
            config.set(path + ".yaw", furn.yaw);
        }
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Não foi possível salvar furniture.yml!");
        }
    }

    public void saveFurnitureAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::saveFurniture);
    }

    public void cleanupOnDisable() {
        for (PlacedFurniture furn : furnitureById.values()) {
            if (furn.seatEntityId != null) {
                Entity s = Bukkit.getEntity(furn.seatEntityId);
                if (s != null) {
                    s.eject();
                    s.remove();
                }
            }
            if (furn.displayEntityId != null) {
                Entity d = Bukkit.getEntity(furn.displayEntityId);
                if (d != null) d.remove();
            }
            if (furn.interactionEntityId != null) {
                Entity i = Bukkit.getEntity(furn.interactionEntityId);
                if (i != null) i.remove();
            }
        }
        chairSeatByPlayer.clear();
        saveFurniture();
    }
}
