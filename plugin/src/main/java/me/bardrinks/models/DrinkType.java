package me.bardrinks.models;

import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.Arrays;
import java.util.List;

public enum DrinkType {

    CERVEJA(
            "cerveja",
            "§6Cerveja do Anão",
            1001,
            20,
            Arrays.asList(
                    "§7Uma caneca espumante e encorpada,",
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
            Arrays.asList(
                    "§7Coquetel leve e refrescante de maçã",
                    "§7verde com um toque suave de álcool.",
                    "",
                    "§7Teor Alcoólico: §a+5% de Birita"
            )
    ),
    LARANJA(
            "laranja",
            "§eAbraço de Menina",
            1003,
            10,
            Arrays.asList(
                    "§7Mistura cítrica e adocicada que desce",
                    "§7macio, mas esquenta rápido.",
                    "",
                    "§7Teor Alcoólico: §e+10% de Birita"
            )
    ),
    VERMELHO(
            "vermelho",
            "§cRubra Noturna",
            1004,
            15,
            Arrays.asList(
                    "§7Bebida intensa de frutas vermelhas",
                    "§7destilada para noites longas.",
                    "",
                    "§7Teor Alcoólico: §c+15% de Birita"
            )
    );

    private final String id;
    private final String displayName;
    private final int modelData;
    private final int birita;
    private final List<String> lore;

    DrinkType(String id, String displayName, int modelData, int birita, List<String> lore) {
        this.id = id;
        this.displayName = displayName;
        this.modelData = modelData;
        this.birita = birita;
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

    public List<String> getLore() {
        return lore;
    }

    public ItemStack createItem(int amount) {
        ItemStack drink = new ItemStack(Material.POTION, Math.max(1, Math.min(64, amount)));
        PotionMeta meta = (PotionMeta) drink.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            meta.setCustomModelData(modelData);
            meta.setBasePotionType(PotionType.WATER);
            meta.setLore(lore);
            try {
                meta.addItemFlags(ItemFlag.values());
            } catch (Throwable ignored) {}
            drink.setItemMeta(meta);
        }
        return drink;
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
