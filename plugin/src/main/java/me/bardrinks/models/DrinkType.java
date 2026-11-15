package me.bardrinks.models;

import me.bardrinks.BarDrinks;
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
            "§6Cerveja do Anão",
            1001,
            20,
            12.0, // 3 BC a cada 5% -> 4 * 3 = 12 BC
            Arrays.asList(
                    "§7Caneca 3D espumante e encorpada,",
                    "§7fermentada nas profundezas da montanha.",
                    "",
                    "§7Teor Alcoólico: §6+20% de Birita"
            )
    ),
    VERDE(
            "verde",
            "§aDoce de Criança",
            1002,
            5,
            3.0, // 3 BC a cada 5% -> 1 * 3 = 3 BC
            Arrays.asList(
                    "§7Lata 3D verde refrescante de maçã",
                    "§7com um toque suave de álcool.",
                    "",
                    "§7Teor Alcoólico: §a+5% de Birita"
            )
    ),
    LARANJA(
            "laranja",
            "§eAbraço de Menina",
            1003,
            10,
            6.0, // 3 BC a cada 5% -> 2 * 3 = 6 BC
            Arrays.asList(
                    "§7Lata 3D laranja cítrica e adocicada",
                    "§7que desce macio, mas esquenta rápido.",
                    "",
                    "§7Teor Alcoólico: §e+10% de Birita"
            )
    ),
    VERMELHO(
            "vermelho",
            "§cRubra Noturna",
            1004,
            15,
            9.0, // 3 BC a cada 5% -> 3 * 3 = 9 BC
            Arrays.asList(
                    "§7Lata 3D vermelha de frutas intensas",
                    "§7destilada para noites longas.",
                    "",
                    "§7Teor Alcoólico: §c+15% de Birita"
            )
    );

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
    private final String displayName;
    private final int modelData;
    private final int birita;
    private final double priceBc;
    private final List<String> lore;

    DrinkType(String id, String displayName, int modelData, int birita, double priceBc, List<String> lore) {
        this.id = id;
        this.displayName = displayName;
        this.modelData = modelData;
        this.birita = birita;
        this.priceBc = priceBc;
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

    public int getBirita() {
        return birita;
    }

    public double getPriceBc() {
        return priceBc;
    }

    public List<String> getLore() {
        return lore;
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
     * Cria 1 unidade da bebida (não-empilhável).
     */
    public ItemStack createItem() {
        ItemStack drink = new ItemStack(Material.POTION, 1);
        PotionMeta meta = (PotionMeta) drink.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            meta.setCustomModelData(modelData);
            meta.setBasePotionType(PotionType.WATER);
            meta.setLore(new ArrayList<>(lore));
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

    /**
     * Mantém compatibilidade entregando N itens individuais (não-empilháveis).
     */
    public ItemStack createItem(int amount) {
        ItemStack stack = createItem();
        stack.setAmount(Math.max(1, Math.min(64, amount)));
        return stack;
    }

    /**
     * Cria o recipiente vazio com o mesmo modelo 3D (impossibilitado de beber e que some em seguida).
     */
    public ItemStack createEmptyContainerItem() {
        ItemStack empty = new ItemStack(Material.POTION, 1);
        PotionMeta meta = (PotionMeta) empty.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§7" + org.bukkit.ChatColor.stripColor(displayName) + " (Vazia)");
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

            // Barra de uso nativa abaixo do item (Paper 1.21 Damageable API)
            if (meta instanceof Damageable damageable) {
                try {
                    damageable.setMaxDamage(MANGUEIRA_MAX_USES);
                    int dmg = Math.max(1, MANGUEIRA_MAX_USES - clampedUses);
                    if (clampedUses == MANGUEIRA_MAX_USES) {
                        damageable.setDamage(1); // Exibe a barra cheia logo ao comprar
                    } else {
                        damageable.setDamage( Math.min(MANGUEIRA_MAX_USES - 1, dmg) );
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
            lore.add("§7Doses no Barril: §e" + barrelUsesLeft + "/" + BARRIL_MAX_USES);
            lore.add("§7Durabilidade da Mangueira: §f" + clampedHose + "/" + MANGUEIRA_MAX_USES);
            lore.add(buildDurabilityBar(clampedHose, MANGUEIRA_MAX_USES));
            lore.add("");
            lore.add("§aSegure Botão Direito para beber (+8% Birita)!");
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
