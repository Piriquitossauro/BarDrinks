package me.bardrinks.models;

import me.bardrinks.BarDrinks;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public enum DrinkType {

    CERVEJA(
            "cerveja",
            new String[]{"cerveja", "cerveja_caseira", "caseira"},
            "§6Cerveja Caseira",
            1001,
            24,   // 24% total (12% por gole em 2 usos)
            15.0, // 15 BC
            1000, // 1 Litro (1000 ml) -> metade = 500 ml
            Arrays.asList(
                    "§7Garrafa de cerveja artesanal,",
                    "§7encorpada e de receita tradicional.",
                    "",
                    "§7Teor Alcoólico Total: §6+24% §8(12% por gole)"
            )
    ),
    VERDE(
            "verde",
            new String[]{"verde", "baiser", "lata_verde"},
            "§aBaiser",
            1002,
            8,    // 8% total (4% por gole em 2 usos)
            6.0,  // 6 BC
            500,  // 500 ml -> metade = 250 ml
            Arrays.asList(
                    "§7Lata 3D verde refrescante e suave,",
                    "§7perfeita para começar a noite.",
                    "",
                    "§7Teor Alcoólico Total: §a+8% §8(4% por gole)"
            )
    ),
    LARANJA(
            "laranja",
            new String[]{"laranja", "imperio", "imperio_bronze", "lata_laranja"},
            "§cImpério Bronze",
            1003,
            14,   // 14% total (7% por gole em 2 usos)
            10.0, // 10 BC
            500,  // 500 ml -> metade = 250 ml
            Arrays.asList(
                    "§7Lata 3D laranja de malte tostado,",
                    "§7equilibrada e marcante.",
                    "",
                    "§7Teor Alcoólico Total: §c+14% §8(7% por gole)"
            )
    ),
    VERMELHO(
            "vermelho",
            new String[]{"vermelho", "spoll", "lata_vermelha", "lata"},
            "§eSpoll",
            1004,
            18,   // 18% total (9% por gole em 2 usos)
            12.0, // 12 BC
            500,  // 500 ml -> metade = 250 ml
            Arrays.asList(
                    "§7Lata 3D vermelha de alto teor,",
                    "§7intensa para noites agitadas.",
                    "",
                    "§7Teor Alcoólico Total: §e+18% §8(9% por gole)"
            )
    );

    // Cada bebida possui 2 goles antes de esvaziar
    public static final int DRINK_MAX_USES = 2;

    // IDs de CustomModelData do Barril e Mangueira
    public static final int CMD_BARRIL_SEM_MANGUEIRA = 1005;
    public static final int CMD_BARRIL_COM_MANGUEIRA = 1006;
    public static final int CMD_BARRIL_SENDO_USADO = 1007;
    public static final int CMD_MANGUEIRA = 1008;

    // Configurações do Barril e Mangueira
    public static final int BARRIL_MAX_USES = 20;
    public static final double BARRIL_BIRITA_PER_USE = 8.0;
    public static final double BARRIL_NEW_PRICE = 550.0;
    public static final double BARRIL_REFILL_PRICE_PER_USE = 5.0;

    public static final int MANGUEIRA_MAX_USES = 100;
    public static final double MANGUEIRA_PRICE = 50.0;

    private final String id;
    private final String[] aliases;
    private final String displayName;
    private final int modelData;
    private final int totalBirita;
    private final double priceBc;
    private final int maxVolumeMl;
    private final List<String> lore;

    DrinkType(String id, String[] aliases, String displayName, int modelData, int totalBirita, double priceBc, int maxVolumeMl, List<String> lore) {
        this.id = id;
        this.aliases = aliases;
        this.displayName = displayName;
        this.modelData = modelData;
        this.totalBirita = totalBirita;
        this.priceBc = priceBc;
        this.maxVolumeMl = maxVolumeMl;
        this.lore = lore;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getModelData() {
        return modelData;
    }

    /**
     * Retorna a birita total da bebida inteira (2 goles).
     */
    public int getBirita() {
        return totalBirita;
    }

    /**
     * Retorna quanto de birita cada gole (1 de 2 usos) adiciona na barra.
     */
    public double getBiritaPerSip() {
        return totalBirita / 2.0;
    }

    public double getPriceBc() {
        return priceBc;
    }

    public List<String> getLore() {
        return lore;
    }

    public static NamespacedKey keyDrinkUses() {
        return new NamespacedKey(BarDrinks.get(), "drink_uses");
    }

    public static NamespacedKey keyEmptyDrink() {
        return new NamespacedKey(BarDrinks.get(), "empty_drink");
    }

    public static NamespacedKey keyBarrelItem() {
        return new NamespacedKey(BarDrinks.get(), "barrel_item");
    }

    public static NamespacedKey keyBarrelUses() {
        return new NamespacedKey(BarDrinks.get(), "barrel_uses");
    }

    public static NamespacedKey keyHoseItem() {
        return new NamespacedKey(BarDrinks.get(), "hose_item");
    }

    public static NamespacedKey keyHoseUses() {
        return new NamespacedKey(BarDrinks.get(), "hose_uses");
    }

    public static NamespacedKey keyConnectedBarrelId() {
        return new NamespacedKey(BarDrinks.get(), "connected_barrel_id");
    }

    /**
     * Cria 1 unidade da bebida cheia (2/2 usos, não-empilhável).
     */
    public ItemStack createItem() {
        return createItemWithUses(DRINK_MAX_USES);
    }

    /**
     * Formata o volume atual da bebida de acordo com os goles restantes (ex: "1 Litro" / "500 ml" / "250 ml").
     */
    public String formatVolume(int usesLeft) {
        int currentMl = (maxVolumeMl * Math.max(0, Math.min(DRINK_MAX_USES, usesLeft))) / DRINK_MAX_USES;
        if (currentMl >= 1000 && currentMl % 1000 == 0) {
            int liters = currentMl / 1000;
            return liters + (liters == 1 ? " Litro" : " Litros");
        }
        return currentMl + " ml";
    }

    /**
     * Cria a bebida com a quantidade específica de goles restantes (1 ou 2).
     */
    public ItemStack createItemWithUses(int usesLeft) {
        int clampedUses = Math.max(1, Math.min(DRINK_MAX_USES, usesLeft));
        String volumeText = formatVolume(clampedUses);

        ItemStack drink = new ItemStack(Material.POTION, 1);
        PotionMeta meta = (PotionMeta) drink.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName + " §7(" + volumeText + ")");
            meta.setCustomModelData(modelData);
            meta.setBasePotionType(PotionType.WATER);

            List<String> itemLore = new ArrayList<>(lore);
            itemLore.add("§7Quantidade: §e" + volumeText);
            meta.setLore(itemLore);

            meta.getPersistentDataContainer().set(keyDrinkUses(), PersistentDataType.INTEGER, clampedUses);

            try {
                meta.setMaxStackSize(1);
            } catch (Throwable ignored) {}
            try {
                meta.addItemFlags(ItemFlag.values());
            } catch (Throwable ignored) {}
            drink.setItemMeta(meta);
        }
        return drink;
    }

    public ItemStack createItem(int amount) {
        ItemStack stack = createItem();
        stack.setAmount(Math.max(1, Math.min(64, amount)));
        return stack;
    }

    public static int getDrinkUses(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return DRINK_MAX_USES;
        Integer uses = item.getItemMeta().getPersistentDataContainer().get(keyDrinkUses(), PersistentDataType.INTEGER);
        return uses != null ? uses : DRINK_MAX_USES;
    }

    /**
     * Cria o recipiente vazio com o mesmo modelo 3D (impossibilitado de beber e que some em seguida).
     */
    public ItemStack createEmptyContainerItem() {
        ItemStack empty = new ItemStack(Material.POTION, 1);
        PotionMeta meta = (PotionMeta) empty.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§7" + ChatColor.stripColor(displayName) + " (Vazia)");
            meta.setCustomModelData(modelData);
            meta.setBasePotionType(PotionType.WATER);
            meta.setLore(Arrays.asList(
                    "§8Recipiente vazio.",
                    "§cNão é possível beber novamente."
            ));
            meta.getPersistentDataContainer().set(keyEmptyDrink(), PersistentDataType.BYTE, (byte) 1);
            try {
                meta.setMaxStackSize(1);
            } catch (Throwable ignored) {}
            try {
                meta.addItemFlags(ItemFlag.values());
            } catch (Throwable ignored) {}
            empty.setItemMeta(meta);
        }
        return empty;
    }

    public static boolean isEmptyContainer(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(keyEmptyDrink(), PersistentDataType.BYTE);
    }

    /**
     * Verifica se o item é uma das 4 bebidas consumíveis (e não está vazia).
     */
    public static DrinkType getDrinkTypeFromItem(ItemStack item) {
        if (item == null || item.getType() != Material.POTION) return null;
        if (!(item.getItemMeta() instanceof PotionMeta meta) || !meta.hasCustomModelData()) return null;
        if (isEmptyContainer(item) || isBarrelItem(item) || isHoseItem(item) || isConnectedMouthpiece(item)) {
            return null;
        }
        return fromModelData(meta.getCustomModelData());
    }

    /**
     * Cria o item do Barril de Chopp (CMD 1005) para o inventário do Barman.
     */
    public static ItemStack createBarrelItem(int usesLeft) {
        int clampedUses = Math.max(0, Math.min(BARRIL_MAX_USES, usesLeft));
        ItemStack item = new ItemStack(Material.POTION, 1);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6§lBarril de Chopp");
            meta.setCustomModelData(CMD_BARRIL_SEM_MANGUEIRA);
            meta.setBasePotionType(PotionType.WATER);
            List<String> lore = new ArrayList<>();
            lore.add("§7Barril de bebida para competições e festas.");
            lore.add("§7Posicione no chão e equipe uma §eMangueira§7.");
            lore.add("");
            lore.add("§7Teor por Gole: §6+8% de Birita");
            lore.add("§7Doses Restantes: §e" + clampedUses + "/" + BARRIL_MAX_USES);
            lore.add("");
            lore.add("§eClique Direito no chão para posicionar!");
            meta.setLore(lore);

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(keyBarrelItem(), PersistentDataType.BYTE, (byte) 1);
            pdc.set(keyBarrelUses(), PersistentDataType.INTEGER, clampedUses);

            try {
                meta.setMaxStackSize(1);
            } catch (Throwable ignored) {}
            try {
                meta.addItemFlags(ItemFlag.values());
            } catch (Throwable ignored) {}
            item.setItemMeta(meta);
        }
        return item;
    }

    public static boolean isBarrelItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(keyBarrelItem(), PersistentDataType.BYTE);
    }

    public static int getBarrelItemUses(ItemStack item) {
        if (!isBarrelItem(item)) return 0;
        Integer uses = item.getItemMeta().getPersistentDataContainer().get(keyBarrelUses(), PersistentDataType.INTEGER);
        return uses != null ? uses : BARRIL_MAX_USES;
    }

    public static void setBarrelItemUses(ItemStack item, int uses) {
        if (!isBarrelItem(item)) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        int clamped = Math.max(0, Math.min(BARRIL_MAX_USES, uses));
        meta.getPersistentDataContainer().set(keyBarrelUses(), PersistentDataType.INTEGER, clamped);
        List<String> lore = new ArrayList<>();
        lore.add("§7Barril de bebida para competições e festas.");
        lore.add("§7Posicione no chão e equipe uma §eMangueira§7.");
        lore.add("");
        lore.add("§7Teor por Gole: §6+8% de Birita");
        lore.add("§7Doses Restantes: §e" + clamped + "/" + BARRIL_MAX_USES);
        lore.add("");
        lore.add("§eClique Direito no chão para posicionar!");
        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    /**
     * Cria o item da Mangueira de Chopp Avulsa (CMD 1008) com barra de durabilidade de 100 usos.
     */
    public static ItemStack createHoseItem(int usesLeft) {
        int clampedUses = Math.max(0, Math.min(MANGUEIRA_MAX_USES, usesLeft));
        ItemStack item = new ItemStack(Material.POTION, 1);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        if (meta != null) {
            boolean broken = (clampedUses <= 0);
            meta.setDisplayName(broken ? "§cMangueira de Chopp (Inutilizável)" : "§eMangueira de Chopp");
            meta.setCustomModelData(CMD_MANGUEIRA);
            meta.setBasePotionType(PotionType.WATER);

            List<String> lore = new ArrayList<>();
            lore.add("§7Mangueira especial para extração de chopp.");
            lore.add("§7Uso: §f" + clampedUses + "/" + MANGUEIRA_MAX_USES + " doses");
            lore.add(buildDurabilityBar(clampedUses, MANGUEIRA_MAX_USES));
            lore.add("");
            if (broken) {
                lore.add("§cEsta mangueira está gasta e inutilizável!");
            } else {
                lore.add("§eDê 2 cliques rápidos no Barril para equipar!");
            }
            meta.setLore(lore);

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(keyHoseItem(), PersistentDataType.BYTE, (byte) 1);
            pdc.set(keyHoseUses(), PersistentDataType.INTEGER, clampedUses);

            if (meta instanceof Damageable damageable) {
                try {
                    damageable.setMaxDamage(MANGUEIRA_MAX_USES);
                    int dmg = Math.max(1, MANGUEIRA_MAX_USES - clampedUses);
                    if (clampedUses == MANGUEIRA_MAX_USES) {
                        damageable.setDamage(1);
                    } else {
                        damageable.setDamage(Math.min(MANGUEIRA_MAX_USES - 1, dmg));
                    }
                } catch (Throwable ignored) {}
            }

            try {
                meta.setMaxStackSize(1);
            } catch (Throwable ignored) {}
            try {
                meta.addItemFlags(ItemFlag.values());
            } catch (Throwable ignored) {}
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Cria o bocal da Mangueira Conectada ao Barril que vai para a Hotbar do player para ele beber.
     */
    public static ItemStack createConnectedMouthpiece(UUID barrelId, int barrelUsesLeft, int hoseUsesLeft) {
        int clampedHose = Math.max(0, Math.min(MANGUEIRA_MAX_USES, hoseUsesLeft));
        ItemStack item = new ItemStack(Material.POTION, 1);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6§lMangueira do Barril §7(Segure Botão Direito)");
            meta.setCustomModelData(CMD_MANGUEIRA);
            meta.setBasePotionType(PotionType.WATER);

            List<String> lore = new ArrayList<>();
            lore.add("§7Conectada ao barril de chopp!");
            lore.add("");
            lore.add("§aSegure Botão Direito para beber!");
            lore.add("§eClique no Barril para devolver a mangueira.");
            meta.setLore(lore);

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(keyConnectedBarrelId(), PersistentDataType.STRING, barrelId.toString());
            pdc.set(keyHoseUses(), PersistentDataType.INTEGER, clampedHose);

            if (meta instanceof Damageable damageable) {
                try {
                    damageable.setMaxDamage(MANGUEIRA_MAX_USES);
                    int dmg = Math.max(1, MANGUEIRA_MAX_USES - clampedHose);
                    damageable.setDamage(Math.min(MANGUEIRA_MAX_USES - 1, dmg));
                } catch (Throwable ignored) {}
            }

            try {
                meta.setMaxStackSize(1);
            } catch (Throwable ignored) {}
            try {
                meta.addItemFlags(ItemFlag.values());
            } catch (Throwable ignored) {}
            item.setItemMeta(meta);
        }
        return item;
    }

    public static boolean isHoseItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(keyHoseItem(), PersistentDataType.BYTE);
    }

    public static int getHoseItemUses(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        Integer uses = item.getItemMeta().getPersistentDataContainer().get(keyHoseUses(), PersistentDataType.INTEGER);
        return uses != null ? uses : 0;
    }

    public static boolean isConnectedMouthpiece(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(keyConnectedBarrelId(), PersistentDataType.STRING);
    }

    public static UUID getConnectedBarrelId(ItemStack item) {
        if (!isConnectedMouthpiece(item)) return null;
        String str = item.getItemMeta().getPersistentDataContainer().get(keyConnectedBarrelId(), PersistentDataType.STRING);
        if (str == null) return null;
        try {
            return UUID.fromString(str);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String buildDurabilityBar(int current, int max) {
        int bars = 20;
        int filled = (int) Math.round(((double) current / max) * bars);
        String color = current > 50 ? "§a" : (current > 20 ? "§e" : "§c");
        StringBuilder sb = new StringBuilder("§8[");
        sb.append(color);
        for (int i = 0; i < bars; i++) {
            if (i == filled) sb.append("§7");
            sb.append("|");
        }
        sb.append("§8]");
        return sb.toString();
    }

    public static DrinkType fromId(String id) {
        if (id == null) return null;
        for (DrinkType type : values()) {
            if (type.id.equalsIgnoreCase(id) || type.name().equalsIgnoreCase(id)) {
                return type;
            }
            for (String alias : type.aliases) {
                if (alias.equalsIgnoreCase(id)) {
                    return type;
                }
            }
        }
        return null;
    }

    public static DrinkType fromModelData(int modelData) {
        for (DrinkType type : values()) {
            if (type.modelData == modelData) {
                return type;
            }
        }
        return null;
    }
}
