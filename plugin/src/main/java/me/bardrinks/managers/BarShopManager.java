package me.bardrinks.managers;

import me.bardrinks.BarDrinks;
import me.bardrinks.models.DrinkType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BarShopManager {

    public static final String MAIN_SHOP_TITLE = "§6§lLoja do Bar §8(Bird Coins)";
    public static final String BARREL_SUB_TITLE = "§6§lLoja do Bar - Barril de Chopp";

    private static final long CONFIRM_TIMEOUT_MS = 10_000L;

    private final BarDrinks plugin;
    private final Map<UUID, Long> newBarrelConfirmTime = new ConcurrentHashMap<>();
    private final Map<UUID, Long> refillBarrelConfirmTime = new ConcurrentHashMap<>();

    public BarShopManager(BarDrinks plugin) {
        this.plugin = plugin;
    }

    /**
     * Abre o HUD Principal da /loja bar com as 4 bebidas, Barril, Mangueira, Cadeira e Mesa.
     */
    public void openMainBarShop(Player player) {
        if (!plugin.isBarman(player)) {
            player.sendMessage("§cApenas barmans possuem acesso à Loja do Bar!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        newBarrelConfirmTime.remove(player.getUniqueId());
        refillBarrelConfirmTime.remove(player.getUniqueId());

        Inventory gui = Bukkit.createInventory(null, 27, MAIN_SHOP_TITLE);
        ItemStack glass = createGlass(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < 27; i++) {
            gui.setItem(i, glass);
        }

        // As 4 Bebidas (Slots 10, 11, 12, 13)
        gui.setItem(10, createDrinkShopIcon(DrinkType.CERVEJA));
        gui.setItem(11, createDrinkShopIcon(DrinkType.VERDE));
        gui.setItem(12, createDrinkShopIcon(DrinkType.LARANJA));
        gui.setItem(13, createDrinkShopIcon(DrinkType.VERMELHO));

        // Barril de Chopp (Slot 15) -> Abre o Sub-HUD de 3 ícones
        gui.setItem(15, createBarrelMainMenuIcon());

        // Mangueira de Chopp (Slot 16) -> 50 BC (100 usos)
        gui.setItem(16, createHoseShopIcon());

        // Móveis do Bar: Cadeira (Slot 21 -> 8 BC) e Mesa (Slot 23 -> 15 BC)
        gui.setItem(21, createChairShopIcon());
        gui.setItem(23, createTableShopIcon());

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_BARREL_OPEN, 0.9f, 1.1f);
    }

    /**
     * Abre o Sub-HUD do Barril com os 3 ícones:
     * - Slot 11: Comprar Barril Novo (550 BC, 2 cliques confirma)
     * - Slot 13: Refill de Barril (5 BC por dose usada, 1º clique fala preço no chat, 2º clique compra)
     * - Slot 15: Barreira para voltar ao HUD anterior
     */
    public void openBarrelSubMenu(Player player) {
        newBarrelConfirmTime.remove(player.getUniqueId());
        refillBarrelConfirmTime.remove(player.getUniqueId());

        Inventory gui = Bukkit.createInventory(null, 27, BARREL_SUB_TITLE);
        ItemStack glass = createGlass(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < 27; i++) {
            gui.setItem(i, glass);
        }

        // Ícone 1: Comprar Barril Novo (CMD 1005)
        ItemStack buyNew = new ItemStack(Material.POTION, 1);
        PotionMeta metaNew = (PotionMeta) buyNew.getItemMeta();
        if (metaNew != null) {
            metaNew.setDisplayName("§6§lComprar Barril Novo");
            metaNew.setCustomModelData(DrinkType.CMD_BARRIL_SEM_MANGUEIRA);
            metaNew.setBasePotionType(PotionType.WATER);
            metaNew.setLore(Arrays.asList(
                    "§7Adquira um barril de chopp lacrado",
                    "§7com capacidade para §e20 doses§7.",
                    "§7Cada dose enche §6+8% §7na barra.",
                    "",
                    "§7Preço Fixo: §6550 BC",
                    "",
                    "§eClique 2 vezes para confirmar a compra!"
            ));
            try { metaNew.addItemFlags(ItemFlag.values()); } catch (Throwable ignored) {}
            buyNew.setItemMeta(metaNew);
        }
        gui.setItem(11, buyNew);

        // Ícone 2: Refill de Barril (Mesmo modelo CMD 1005, descrição de Refill)
        ItemStack refill = new ItemStack(Material.POTION, 1);
        PotionMeta metaRefill = (PotionMeta) refill.getItemMeta();
        if (metaRefill != null) {
            metaRefill.setDisplayName("§a§lRefill de Barril");
            metaRefill.setCustomModelData(DrinkType.CMD_BARRIL_SEM_MANGUEIRA);
            metaRefill.setBasePotionType(PotionType.WATER);
            metaRefill.setLore(Arrays.asList(
                    "§7Reabastece um barril no seu inventário",
                    "§7ou posicionado próximo a você.",
                    "",
                    "§7Custo: §65 BC §7por dose consumida",
                    "§8(Ex: 18/20 doses = 2 usadas = 10 BC)",
                    "",
                    "§e1º Clique: §fVer preço no chat",
                    "§a2º Clique: §fConfirmar o Refill"
            ));
            try { metaRefill.addItemFlags(ItemFlag.values()); } catch (Throwable ignored) {}
            refill.setItemMeta(metaRefill);
        }
        gui.setItem(13, refill);

        // Ícone 3: Botão de Barreira (Voltar para o HUD anterior)
        ItemStack back = new ItemStack(Material.BARRIER, 1);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName("§c§lVoltar para a Loja do Bar");
            backMeta.setLore(Collections.singletonList("§7Clique para retornar ao menu anterior."));
            back.setItemMeta(backMeta);
        }
        gui.setItem(15, back);

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
    }

    public void handleMainShopClick(Player player, int slot) {
        DrinkType selectedDrink = null;
        if (slot == 10) selectedDrink = DrinkType.CERVEJA;
        else if (slot == 11) selectedDrink = DrinkType.VERDE;
        else if (slot == 12) selectedDrink = DrinkType.LARANJA;
        else if (slot == 13) selectedDrink = DrinkType.VERMELHO;

        if (selectedDrink != null) {
            buyDrink(player, selectedDrink);
            return;
        }

        if (slot == 15) {
            openBarrelSubMenu(player);
            return;
        }

        if (slot == 16) {
            buyHose(player);
            return;
        }

        if (slot == 21) {
            buyChair(player);
            return;
        }

        if (slot == 23) {
            buyTable(player);
        }
    }

    public void handleBarrelSubMenuClick(Player player, int slot) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        if (slot == 15) {
            openMainBarShop(player);
            return;
        }

        if (slot == 11) {
            refillBarrelConfirmTime.remove(uuid);
            Long firstClick = newBarrelConfirmTime.get(uuid);

            if (firstClick == null || (now - firstClick) > CONFIRM_TIMEOUT_MS) {
                newBarrelConfirmTime.put(uuid, now);
                player.sendMessage("§e[Loja do Bar] Clique novamente para confirmar a compra de um §6Barril de Chopp Novo §epor §6550 BC§e!");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
                return;
            }

            newBarrelConfirmTime.remove(uuid);
            if (!plugin.getBarrelManager().hasInventorySpace(player)) {
                player.sendMessage("§cSem espaço necessário no inventário");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            if (!chargePlayer(player, DrinkType.BARRIL_NEW_PRICE)) {
                return;
            }

            player.getInventory().addItem(DrinkType.createBarrelItem(DrinkType.BARRIL_MAX_USES));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.3f);
            player.sendMessage("§a[Loja do Bar] Você comprou um §6Barril de Chopp Novo (20/20 doses) §apor §6550 BC§a!");
            return;
        }

        if (slot == 13) {
            newBarrelConfirmTime.remove(uuid);

            RefillTarget target = findRefillTarget(player);
            if (target == null) {
                refillBarrelConfirmTime.remove(uuid);
                player.sendMessage("§c[Loja do Bar] Nenhum barril precisando de refill foi encontrado no seu inventário ou próximo a você!");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            int usedDoses = DrinkType.BARRIL_MAX_USES - target.currentUses;
            double totalCost = usedDoses * DrinkType.BARRIL_REFILL_PRICE_PER_USE;

            Long firstClick = refillBarrelConfirmTime.get(uuid);
            if (firstClick == null || (now - firstClick) > CONFIRM_TIMEOUT_MS) {
                refillBarrelConfirmTime.put(uuid, now);
                player.sendMessage("§e[Loja do Bar] Seu barril está com §f" + target.currentUses + "/20 doses §e(foram usados §f"
                        + usedDoses + "§e). O Refill custará §6" + EconomiaHook.formatBc(totalCost)
                        + "§e. Clique novamente para comprar!");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.3f);
                return;
            }

            refillBarrelConfirmTime.remove(uuid);
            if (!chargePlayer(player, totalCost)) {
                return;
            }

            target.applyRefill();
            player.playSound(player.getLocation(), Sound.ITEM_BUCKET_FILL, 1.0f, 1.1f);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.3f);
            player.sendMessage("§a[Loja do Bar] Refill concluído com sucesso por §6" + EconomiaHook.formatBc(totalCost)
                    + "§a! O barril voltou para §e20/20 doses§a.");
        }
    }

    private void buyDrink(Player player, DrinkType type) {
        if (!plugin.getBarrelManager().hasInventorySpace(player)) {
            player.sendMessage("§cSem espaço necessário no inventário");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        double price = type.getPriceBc();
        if (!chargePlayer(player, price)) {
            return;
        }

        player.getInventory().addItem(type.createItem());
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
        player.sendMessage("§a[Loja do Bar] Você comprou 1x " + type.getDisplayName()
                + " §apor §6" + EconomiaHook.formatBc(price) + "§a!");
    }

    private void buyHose(Player player) {
        if (!plugin.getBarrelManager().hasInventorySpace(player)) {
            player.sendMessage("§cSem espaço necessário no inventário");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        if (!chargePlayer(player, DrinkType.MANGUEIRA_PRICE)) {
            return;
        }

        player.getInventory().addItem(DrinkType.createHoseItem(DrinkType.MANGUEIRA_MAX_USES));
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.9f, 1.1f);
        player.sendMessage("§a[Loja do Bar] Você comprou 1x §eMangueira de Chopp (100 usos) §apor §650 BC§a!");
    }

    private void buyChair(Player player) {
        ItemStack chair = DrinkType.createChairItem(1);
        if (!plugin.getFurnitureManager().canFitItem(player, chair)) {
            player.sendMessage("§cSem espaço necessário no inventário");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        if (!chargePlayer(player, DrinkType.CADEIRA_PRICE)) {
            return;
        }

        player.getInventory().addItem(chair);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.9f, 1.1f);
        player.sendMessage("§a[Loja do Bar] Você comprou 1x §6Cadeira de Madeira §apor §6"
                + EconomiaHook.formatBc(DrinkType.CADEIRA_PRICE) + "§a!");
    }

    private void buyTable(Player player) {
        ItemStack table = DrinkType.createTableItem(1);
        if (!plugin.getFurnitureManager().canFitItem(player, table)) {
            player.sendMessage("§cSem espaço necessário no inventário");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        if (!chargePlayer(player, DrinkType.MESA_PRICE)) {
            return;
        }

        player.getInventory().addItem(table);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.9f, 1.1f);
        player.sendMessage("§a[Loja do Bar] Você comprou 1x §6Mesa de Madeira §apor §6"
                + EconomiaHook.formatBc(DrinkType.MESA_PRICE) + "§a!");
    }

    private boolean chargePlayer(Player player, double price) {
        EconomiaHook eco = plugin.getEconomiaHook();
        if (!eco.isAvailable()) {
            player.sendMessage("§cO sistema de Economia (Bird Coin) não está ativo no momento!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }

        if (!eco.removeBalance(player.getUniqueId(), price)) {
            player.sendMessage("§cSaldo insuficiente! Você precisa de §6" + EconomiaHook.formatBc(price)
                    + " §c(Seu saldo: §f" + EconomiaHook.formatBc(eco.getBalance(player.getUniqueId())) + "§c).");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }
        return true;
    }

    private static class RefillTarget {
        final int currentUses;
        final Runnable refillAction;

        RefillTarget(int currentUses, Runnable refillAction) {
            this.currentUses = currentUses;
            this.refillAction = refillAction;
        }

        void applyRefill() {
            refillAction.run();
        }
    }

    private RefillTarget findRefillTarget(Player player) {
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (DrinkType.isBarrelItem(item)) {
                int uses = DrinkType.getBarrelItemUses(item);
                if (uses < DrinkType.BARRIL_MAX_USES) {
                    final int slot = i;
                    return new RefillTarget(uses, () -> {
                        ItemStack current = inv.getItem(slot);
                        if (DrinkType.isBarrelItem(current)) {
                            DrinkType.setBarrelItemUses(current, DrinkType.BARRIL_MAX_USES);
                            inv.setItem(slot, current);
                        }
                    });
                }
            }
        }

        BarrelManager.PlacedBarrel placed = plugin.getBarrelManager().findNearestRefillableBarrel(player, 8.0);
        if (placed != null) {
            return new RefillTarget(placed.barrelUses, () -> {
                placed.barrelUses = DrinkType.BARRIL_MAX_USES;
                plugin.getBarrelManager().saveBarrelsAsync();
            });
        }

        return null;
    }

    private ItemStack createDrinkShopIcon(DrinkType type) {
        ItemStack icon = type.createItem();
        ItemMeta meta = icon.getItemMeta();
        if (meta != null && meta.getLore() != null) {
            List<String> lore = new ArrayList<>(meta.getLore());
            lore.add("");
            lore.add("§7Preço: §6" + EconomiaHook.formatBc(type.getPriceBc()));
            lore.add("");
            lore.add("§eClique para comprar!");
            meta.setLore(lore);
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private ItemStack createBarrelMainMenuIcon() {
        ItemStack icon = new ItemStack(Material.POTION, 1);
        PotionMeta meta = (PotionMeta) icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6§lBarril de Chopp §7(Comprar / Refill)");
            meta.setCustomModelData(DrinkType.CMD_BARRIL_SEM_MANGUEIRA);
            meta.setBasePotionType(PotionType.WATER);
            meta.setLore(Arrays.asList(
                    "§7Barril de Chopp com §e20 doses§7.",
                    "§7Cada dose tomada na mangueira enche §6+8%§7.",
                    "",
                    "§7• Barril Novo: §6550 BC",
                    "§7• Refill: §65 BC §7por dose usada",
                    "",
                    "§eClique para abrir as opções do Barril!"
            ));
            try { meta.addItemFlags(ItemFlag.values()); } catch (Throwable ignored) {}
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private ItemStack createHoseShopIcon() {
        ItemStack icon = DrinkType.createHoseItem(DrinkType.MANGUEIRA_MAX_USES);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setLore(Arrays.asList(
                    "§7Mangueira com torneira para o Barril.",
                    "§7Durabilidade: §f100 usos",
                    "",
                    "§7Preço: §650 BC",
                    "",
                    "§eClique para comprar!"
            ));
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private ItemStack createChairShopIcon() {
        ItemStack icon = DrinkType.createChairItem(1);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setLore(Arrays.asList(
                    "§7Cadeira de madeira 3D estofada.",
                    "§7Item empilhável (até 64x).",
                    "",
                    "§7Preço: §68 BC",
                    "",
                    "§eClique para comprar!"
            ));
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private ItemStack createTableShopIcon() {
        ItemStack icon = DrinkType.createTableItem(1);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setLore(Arrays.asList(
                    "§7Mesa de madeira 3D de 1 bloco.",
                    "§7Permite apoiar bebidas e barris em cima.",
                    "§7Item empilhável (até 64x).",
                    "",
                    "§7Preço: §615 BC",
                    "",
                    "§eClique para comprar!"
            ));
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private ItemStack createGlass(Material mat) {
        ItemStack glass = new ItemStack(mat, 1);
        ItemMeta meta = glass.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            glass.setItemMeta(meta);
        }
        return glass;
    }
}
