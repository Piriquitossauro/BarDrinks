package me.bardrinks.managers;

import me.bardrinks.BarDrinks;
import me.bardrinks.models.DrinkType;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TableDrinkManager {

    // Posições dos 4 espaços aproximadas do centro (0.50, 0.50) do bloco para ficarem mais agrupadas visualmente
    private static final double[][] QUADRANT_OFFSETS = {
            {0.35, 0.35}, // Quadrante 0 (Noroeste)
            {0.65, 0.35}, // Quadrante 1 (Nordeste)
            {0.35, 0.65}, // Quadrante 2 (Sudoeste)
            {0.65, 0.65}  // Quadrante 3 (Sudeste)
    };

    public static class PlacedDrinkSlot {
        public DrinkType type;
        public int usesLeft;
        public float yaw;
        public UUID displayEntityId;

        public PlacedDrinkSlot(DrinkType type, int usesLeft, float yaw) {
            this.type = type;
            this.usesLeft = usesLeft;
            this.yaw = yaw;
        }
    }

    public static class DrinkCluster {
        public final UUID id;
        public final String worldName;
        public final int blockX;
        public final int blockY;
        public final int blockZ;
        public final PlacedDrinkSlot[] slots = new PlacedDrinkSlot[4];
        public UUID interactionEntityId;

        public DrinkCluster(UUID id, String worldName, int blockX, int blockY, int blockZ) {
            this.id = id;
            this.worldName = worldName;
            this.blockX = blockX;
            this.blockY = blockY;
            this.blockZ = blockZ;
        }

        public Location getBlockLocation() {
            World w = Bukkit.getWorld(worldName);
            if (w == null) return null;
            return new Location(w, blockX, blockY, blockZ);
        }

        public Location getQuadrantCenter(int q, DrinkType type, float yaw) {
            World w = Bukkit.getWorld(worldName);
            if (w == null) return null;
            // - Latas ficam em blockY + 0.0
            // - Cerveja (garrafa) desce mais 0.10 Y (de 0.25 para 0.15)
            double yOffset = (type == DrinkType.CERVEJA) ? 0.15 : 0.0;
            return new Location(w,
                    blockX + QUADRANT_OFFSETS[q][0],
                    blockY + yOffset,
                    blockZ + QUADRANT_OFFSETS[q][1],
                    yaw,
                    0f);
        }

        public int getOccupiedCount() {
            int count = 0;
            for (PlacedDrinkSlot s : slots) {
                if (s != null) count++;
            }
            return count;
        }

        public boolean isEmpty() {
            return getOccupiedCount() == 0;
        }
    }

    private final BarDrinks plugin;
    private final File file;
    private FileConfiguration config;

    private final Map<UUID, DrinkCluster> clustersById = new ConcurrentHashMap<>();
    private final Map<String, UUID> clusterByBlockKey = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> clusterByEntityId = new ConcurrentHashMap<>();
    private final Map<UUID, Long> interactDebounce = new ConcurrentHashMap<>();

    public TableDrinkManager(BarDrinks plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "placed_drinks.yml");
        loadClusters();
    }

    private String blockKey(String world, int x, int y, int z) {
        return world + ":" + x + ":" + y + ":" + z;
    }

    public DrinkCluster getClusterAtBlock(Block block) {
        if (block == null) return null;
        UUID id = clusterByBlockKey.get(blockKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ()));
        return id != null ? clustersById.get(id) : null;
    }

    public DrinkCluster getClusterByEntity(Entity entity) {
        if (entity == null) return null;
        UUID id = clusterByEntityId.get(entity.getUniqueId());
        return id != null ? clustersById.get(id) : null;
    }

    /**
     * Verifica se o bloco abaixo é um chão sólido válido para apoiar bebidas.
     */
    public boolean hasSolidGroundBelow(Block targetBlock) {
        if (targetBlock == null) return false;
        Block below = targetBlock.getRelative(BlockFace.DOWN);
        Material mat = below.getType();
        if (!mat.isSolid() || below.isLiquid()) return false;
        // Impede apoiar bebidas em cima de outro bloco de bebidas ou ar/barreira invisível sem suporte
        return getClusterAtBlock(below) == null;
    }

    /**
     * Tenta iniciar ou interagir com um bloco onde o player clicou segurando uma bebida.
     */
    public void handlePlaceOrInteractAtBlock(Player player, Block targetBlock) {
        UUID pUuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = interactDebounce.get(pUuid);
        if (last != null && (now - last) < 70L) return;
        interactDebounce.put(pUuid, now);

        DrinkCluster existing = getClusterAtBlock(targetBlock);
        if (existing != null) {
            handleClusterInteraction(player, existing);
            return;
        }

        // Está tentando colocar a 1ª bebida neste bloco de ar
        ItemStack inHand = player.getInventory().getItemInMainHand();
        DrinkType handType = DrinkType.getDrinkTypeFromItem(inHand);
        if (handType == null) return;

        if (!targetBlock.getType().isAir() && !targetBlock.isReplaceable()) {
            player.sendMessage("§cNão há espaço livre neste bloco para apoiar a bebida!");
            return;
        }

        if (!hasSolidGroundBelow(targetBlock)) {
            player.sendMessage("§cVocê precisa de um chão sólido abaixo para apoiar a bebida!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Evita prender o próprio player dentro do bloco de barreira
        Location pLoc = player.getLocation();
        if (pLoc.getBlockX() == targetBlock.getX() && pLoc.getBlockZ() == targetBlock.getZ()
                && (pLoc.getBlockY() == targetBlock.getY() || pLoc.getBlockY() + 1 == targetBlock.getY())) {
            player.sendMessage("§cAfaste-se um pouco para apoiar a bebida neste bloco!");
            return;
        }

        UUID clusterId = UUID.randomUUID();
        DrinkCluster cluster = new DrinkCluster(
                clusterId,
                targetBlock.getWorld().getName(),
                targetBlock.getX(),
                targetBlock.getY(),
                targetBlock.getZ()
        );
        clustersById.put(clusterId, cluster);
        clusterByBlockKey.put(blockKey(cluster.worldName, cluster.blockX, cluster.blockY, cluster.blockZ), clusterId);

        setupClusterBarrierAndInteraction(cluster);

        int bestQuadrant = findClosestQuadrant(player, cluster, true);
        int uses = DrinkType.getDrinkUses(inHand);
        float yaw = player.getLocation().getYaw();

        cluster.slots[bestQuadrant] = new PlacedDrinkSlot(handType, uses, yaw);
        spawnSlotDisplay(cluster, bestQuadrant);

        player.getInventory().setItemInMainHand(null);
        player.playSound(cluster.getBlockLocation(), Sound.BLOCK_GLASS_PLACE, 0.9f, 1.2f);
        saveClustersAsync();
    }

    /**
     * Interação com um bloco que já possui bebidas posicionadas (1 a 4 espaços).
     */
    public void handleClusterInteraction(Player player, DrinkCluster cluster) {
        UUID pUuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = interactDebounce.get(pUuid);
        if (last != null && (now - last) < 70L) return;
        interactDebounce.put(pUuid, now);

        // Verifica se o bloco sólido abaixo ainda existe
        Block block = cluster.getBlockLocation() != null ? cluster.getBlockLocation().getBlock() : null;
        if (block != null && !hasSolidGroundBelow(block)) {
            dropAllAndRemoveCluster(cluster);
            return;
        }

        ItemStack inHand = player.getInventory().getItemInMainHand();
        DrinkType handType = DrinkType.getDrinkTypeFromItem(inHand);

        // =========================================================================
        // CASO 1: Player está SEGURANDO uma bebida na mão
        // =========================================================================
        if (handType != null) {
            int handUses = DrinkType.getDrinkUses(inHand);
            float yaw = player.getLocation().getYaw();

            // Se ainda houver alguma das 4 vagas livres -> busca a vaga livre mais próxima do player
            if (cluster.getOccupiedCount() < 4) {
                int freeQuad = findClosestQuadrant(player, cluster, true);
                if (freeQuad != -1) {
                    cluster.slots[freeQuad] = new PlacedDrinkSlot(handType, handUses, yaw);
                    spawnSlotDisplay(cluster, freeQuad);
                    player.getInventory().setItemInMainHand(null);
                    player.playSound(cluster.getBlockLocation(), Sound.BLOCK_GLASS_PLACE, 0.9f, 1.25f);
                    saveClustersAsync();
                    return;
                }
            }

            // Se todas as 4 vagas estiverem ocupadas -> Troca (Swap) pela bebida mais próxima que ele interagiu!
            int closestOccupied = findClosestQuadrant(player, cluster, false);
            if (closestOccupied != -1 && cluster.slots[closestOccupied] != null) {
                PlacedDrinkSlot oldSlot = cluster.slots[closestOccupied];
                ItemStack oldItem = oldSlot.type.createItemWithUses(oldSlot.usesLeft);

                // Remove o display antigo e coloca a bebida da mão do player naquele exato lugar
                despawnSlotDisplay(cluster, closestOccupied);
                cluster.slots[closestOccupied] = new PlacedDrinkSlot(handType, handUses, yaw);
                spawnSlotDisplay(cluster, closestOccupied);

                // A bebida que estava no bloco vai para a mão do player
                player.getInventory().setItemInMainHand(oldItem);
                player.playSound(cluster.getBlockLocation(), Sound.ENTITY_ITEM_PICKUP, 0.85f, 1.15f);
                player.playSound(cluster.getBlockLocation(), Sound.BLOCK_GLASS_PLACE, 0.85f, 1.25f);
                saveClustersAsync();
            }
            return;
        }

        // =========================================================================
        // CASO 2: Player NÃO está com bebida na mão -> Pega a bebida mais próxima da pilha
        // =========================================================================
        int closestOccupied = findClosestQuadrant(player, cluster, false);
        if (closestOccupied == -1 || cluster.slots[closestOccupied] == null) {
            removeClusterCompletely(cluster);
            return;
        }

        // Verifica se o player tem espaço no inventário
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage("§cSem espaço necessário no inventário");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        PlacedDrinkSlot taken = cluster.slots[closestOccupied];
        despawnSlotDisplay(cluster, closestOccupied);
        cluster.slots[closestOccupied] = null;

        ItemStack givenItem = taken.type.createItemWithUses(taken.usesLeft);
        player.getInventory().addItem(givenItem);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.9f, 1.2f);

        // Se tirou a última bebida do bloco, remove a barreira e a entidade de interação
        if (cluster.isEmpty()) {
            removeClusterCompletely(cluster);
        } else {
            saveClustersAsync();
        }
    }

    /**
     * Calcula qual dos 4 quadrantes está mais próximo do ponto de interação / posição do player.
     * @param wantFree true para buscar entre as vagas livres; false para buscar entre as vagas ocupadas.
     */
    private int findClosestQuadrant(Player player, DrinkCluster cluster, boolean wantFree) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();

        // Calcula o ponto onde a mira do player cruza o plano horizontal do bloco (y = blockY + 0.5)
        double targetY = cluster.blockY + 0.5;
        double refX = player.getLocation().getX();
        double refZ = player.getLocation().getZ();

        if (Math.abs(dir.getY()) > 0.01) {
            double t = (targetY - eye.getY()) / dir.getY();
            if (t > 0 && t <= 5.5) {
                double hitX = eye.getX() + dir.getX() * t;
                double hitZ = eye.getZ() + dir.getZ() * t;
                // Se o ponto mirado estiver perto do bloco, combina 70% do ponto clicado + 30% da posição do player
                if (hitX >= cluster.blockX - 0.6 && hitX <= cluster.blockX + 1.6
                        && hitZ >= cluster.blockZ - 0.6 && hitZ <= cluster.blockZ + 1.6) {
                    refX = hitX * 0.75 + refX * 0.25;
                    refZ = hitZ * 0.75 + refZ * 0.25;
                }
            }
        }

        int bestQ = -1;
        double bestDistSq = Double.MAX_VALUE;

        for (int q = 0; q < 4; q++) {
            boolean isFree = (cluster.slots[q] == null);
            if (wantFree != isFree) continue;

            double qx = cluster.blockX + QUADRANT_OFFSETS[q][0];
            double qz = cluster.blockZ + QUADRANT_OFFSETS[q][1];
            double dx = qx - refX;
            double dz = qz - refZ;
            double dSq = dx * dx + dz * dz;

            if (dSq < bestDistSq) {
                bestDistSq = dSq;
                bestQ = q;
            }
        }
        return bestQ;
    }

    private void setupClusterBarrierAndInteraction(DrinkCluster cluster) {
        Location blockLoc = cluster.getBlockLocation();
        if (blockLoc == null || blockLoc.getWorld() == null) return;
        World world = blockLoc.getWorld();

        blockLoc.getBlock().setType(Material.BARRIER);

        Location interLoc = new Location(world, cluster.blockX + 0.5, cluster.blockY, cluster.blockZ + 0.5);
        Interaction interaction = world.spawn(interLoc, Interaction.class, inter -> {
            inter.setInteractionWidth(1.04f);
            inter.setInteractionHeight(1.04f);
            inter.setResponsive(true);
            inter.setPersistent(false);
            inter.setInvulnerable(true);
        });
        cluster.interactionEntityId = interaction.getUniqueId();
        clusterByEntityId.put(interaction.getUniqueId(), cluster.id);
    }

    private void spawnSlotDisplay(DrinkCluster cluster, int q) {
        PlacedDrinkSlot slot = cluster.slots[q];
        if (slot == null) return;

        Location center = cluster.getQuadrantCenter(q, slot.type, slot.yaw);
        if (center == null || center.getWorld() == null) return;

        ItemStack displayStack = new ItemStack(Material.POTION, 1);
        PotionMeta meta = (PotionMeta) displayStack.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(slot.type.getModelData());
            meta.setBasePotionType(PotionType.WATER);
            displayStack.setItemMeta(meta);
        }

        ItemDisplay display = center.getWorld().spawn(center, ItemDisplay.class, d -> {
            d.setItemStack(displayStack);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setPersistent(false);
            d.setInvulnerable(true);
            Transformation tr = d.getTransformation();
            d.setTransformation(tr);
        });

        slot.displayEntityId = display.getUniqueId();
        clusterByEntityId.put(display.getUniqueId(), cluster.id);
    }

    private void despawnSlotDisplay(DrinkCluster cluster, int q) {
        PlacedDrinkSlot slot = cluster.slots[q];
        if (slot == null || slot.displayEntityId == null) return;
        clusterByEntityId.remove(slot.displayEntityId);
        Entity ent = Bukkit.getEntity(slot.displayEntityId);
        if (ent != null) {
            ent.remove();
        }
        slot.displayEntityId = null;
    }

    public void dropAllAndRemoveCluster(DrinkCluster cluster) {
        if (cluster == null) return;
        Location loc = cluster.getBlockLocation();
        if (loc != null && loc.getWorld() != null) {
            Location dropLoc = loc.clone().add(0.5, 0.5, 0.5);
            for (int q = 0; q < 4; q++) {
                PlacedDrinkSlot s = cluster.slots[q];
                if (s != null) {
                    loc.getWorld().dropItemNaturally(dropLoc, s.type.createItemWithUses(s.usesLeft));
                }
            }
        }
        removeClusterCompletely(cluster);
    }

    public void removeClusterCompletely(DrinkCluster cluster) {
        if (cluster == null) return;
        for (int q = 0; q < 4; q++) {
            despawnSlotDisplay(cluster, q);
            cluster.slots[q] = null;
        }
        if (cluster.interactionEntityId != null) {
            clusterByEntityId.remove(cluster.interactionEntityId);
            Entity inter = Bukkit.getEntity(cluster.interactionEntityId);
            if (inter != null) inter.remove();
            cluster.interactionEntityId = null;
        }
        Location blockLoc = cluster.getBlockLocation();
        if (blockLoc != null && blockLoc.getBlock().getType() == Material.BARRIER) {
            blockLoc.getBlock().setType(Material.AIR);
        }
        clustersById.remove(cluster.id);
        clusterByBlockKey.remove(blockKey(cluster.worldName, cluster.blockX, cluster.blockY, cluster.blockZ));
        saveClustersAsync();
    }

    public synchronized void loadClusters() {
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Não foi possível criar placed_drinks.yml!");
            }
        }
        config = YamlConfiguration.loadConfiguration(file);
        clustersById.clear();
        clusterByBlockKey.clear();
        clusterByEntityId.clear();

        ConfigurationSection sec = config.getConfigurationSection("clusters");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    String path = "clusters." + key;
                    String world = config.getString(path + ".world", "world");
                    int x = config.getInt(path + ".x");
                    int y = config.getInt(path + ".y");
                    int z = config.getInt(path + ".z");

                    DrinkCluster cluster = new DrinkCluster(id, world, x, y, z);
                    for (int q = 0; q < 4; q++) {
                        String qPath = path + ".slots." + q;
                        if (config.contains(qPath + ".type")) {
                            DrinkType type = DrinkType.fromId(config.getString(qPath + ".type"));
                            int uses = config.getInt(qPath + ".uses", DrinkType.DRINK_MAX_USES);
                            float yaw = (float) config.getDouble(qPath + ".yaw", 0.0);
                            if (type != null) {
                                cluster.slots[q] = new PlacedDrinkSlot(type, uses, yaw);
                            }
                        }
                    }

                    if (!cluster.isEmpty()) {
                        clustersById.put(id, cluster);
                        clusterByBlockKey.put(blockKey(world, x, y, z), id);
                        setupClusterBarrierAndInteraction(cluster);
                        for (int q = 0; q < 4; q++) {
                            if (cluster.slots[q] != null) {
                                spawnSlotDisplay(cluster, q);
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    public synchronized void saveClusters() {
        if (config == null) {
            config = new YamlConfiguration();
        }
        config.set("clusters", null);
        for (DrinkCluster cluster : clustersById.values()) {
            if (cluster.isEmpty()) continue;
            String path = "clusters." + cluster.id.toString();
            config.set(path + ".world", cluster.worldName);
            config.set(path + ".x", cluster.blockX);
            config.set(path + ".y", cluster.blockY);
            config.set(path + ".z", cluster.blockZ);
            for (int q = 0; q < 4; q++) {
                PlacedDrinkSlot s = cluster.slots[q];
                if (s != null) {
                    String qPath = path + ".slots." + q;
                    config.set(qPath + ".type", s.type.getId());
                    config.set(qPath + ".uses", s.usesLeft);
                    config.set(qPath + ".yaw", s.yaw);
                }
            }
        }
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Não foi possível salvar placed_drinks.yml!");
        }
    }

    public void saveClustersAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::saveClusters);
    }

    public void cleanupOnDisable() {
        for (DrinkCluster cluster : clustersById.values()) {
            for (int q = 0; q < 4; q++) {
                despawnSlotDisplay(cluster, q);
            }
            if (cluster.interactionEntityId != null) {
                Entity inter = Bukkit.getEntity(cluster.interactionEntityId);
                if (inter != null) inter.remove();
            }
        }
        saveClusters();
    }
}
