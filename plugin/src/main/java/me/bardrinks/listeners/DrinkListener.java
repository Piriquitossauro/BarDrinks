package me.bardrinks.listeners;

import me.bardrinks.BarDrinks;
import me.bardrinks.managers.BarShopManager;
import me.bardrinks.managers.BarrelManager;
import me.bardrinks.managers.EffectManager;
import me.bardrinks.models.DrinkType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;

public class DrinkListener implements Listener {

    public static final String GUI_TITLE = "§6§lBarDrinks - Menu de Bebidas";

    /**
     * Intercepta o comando "/loja bar" para abrir a Loja do Bar integrada ao plugin de Economia.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent e) {
        String msg = e.getMessage().trim().toLowerCase();
        if (msg.equals("/loja bar") || msg.startsWith("/loja bar ")) {
            e.setCancelled(true);
            BarDrinks.get().getBarShopManager().openMainBarShop(e.getPlayer());
        }
    }

    /**
     * Interação com a entidade Interaction / ItemDisplay do Barril.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        BarrelManager.PlacedBarrel barrel = BarDrinks.get().getBarrelManager().getBarrelByEntity(e.getRightClicked());
        if (barrel != null) {
            e.setCancelled(true);
            if (e.getHand() == EquipmentSlot.MAIN_HAND) {
                BarDrinks.get().getBarrelManager().handleBarrelRightClick(e.getPlayer(), barrel);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent e) {
        BarrelManager.PlacedBarrel barrel = BarDrinks.get().getBarrelManager().getBarrelByEntity(e.getRightClicked());
        if (barrel != null) {
            e.setCancelled(true);
            if (e.getHand() == EquipmentSlot.MAIN_HAND) {
                BarDrinks.get().getBarrelManager().handleBarrelRightClick(e.getPlayer(), barrel);
            }
        }
    }

    /**
     * Interações com itens (Posicionar Barril, Clicar no bloco do Barril, Bloquear uso de recipiente vazio/mangueira avulsa/banidos).
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        Action action = e.getAction();

        // 1. Se clicou com botão direito diretamente no bloco de BARRIER de um barril posicionado
        if (action == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null) {
            BarrelManager.PlacedBarrel clickedBarrel = BarDrinks.get().getBarrelManager().getBarrelAtBlock(e.getClickedBlock());
            if (clickedBarrel != null) {
                e.setCancelled(true);
                if (e.getHand() == EquipmentSlot.MAIN_HAND) {
                    BarDrinks.get().getBarrelManager().handleBarrelRightClick(p, clickedBarrel);
                }
                return;
            }
        }

        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = e.getItem();
        if (item == null) return;

        // 2. Se for um Recipiente Vazio (Caneca/Lata já tomada), bloqueia beber
        if (DrinkType.isEmptyContainer(item)) {
            e.setCancelled(true);
            return;
        }

        // 3. Se for a Mangueira Avulsa (não conectada ao barril), impede de beber diretamente
        if (DrinkType.isHoseItem(item) && !DrinkType.isConnectedMouthpiece(item)) {
            e.setCancelled(true);
            return;
        }

        // 4. Se for o item do Barril (CMD 1005) -> Posiciona no bloco de ar para onde o player está olhando
        if (DrinkType.isBarrelItem(item)) {
            e.setCancelled(true);
            if (e.getHand() != EquipmentSlot.MAIN_HAND) return;

            Block clickedBlock = e.getClickedBlock();
            org.bukkit.block.BlockFace face = e.getBlockFace();

            // Caso o cliente envie RIGHT_CLICK_AIR por ser uma poção, faz raytrace de até 5 blocos para achar onde ele olha
            if (clickedBlock == null) {
                org.bukkit.util.RayTraceResult ray = p.rayTraceBlocks(5.0);
                if (ray != null && ray.getHitBlock() != null && ray.getHitBlockFace() != null) {
                    clickedBlock = ray.getHitBlock();
                    face = ray.getHitBlockFace();
                }
            }

            if (clickedBlock != null && face != null) {
                // Se clicou num barril existente, não tenta posicionar outro em cima
                if (BarDrinks.get().getBarrelManager().getBarrelAtBlock(clickedBlock) != null) {
                    return;
                }

                Block target = clickedBlock.getRelative(face);
                if (!target.getType().isAir() && !target.isReplaceable()) {
                    p.sendMessage("§cNão há espaço livre neste bloco para posicionar o barril!");
                    return;
                }
                if (BarDrinks.get().getBarrelManager().getBarrelAtBlock(target) != null) {
                    p.sendMessage("§cJá existe um barril neste local!");
                    return;
                }

                // Evita posicionar o bloco de barreira exatamente onde o corpo do próprio player está
                Location pLoc = p.getLocation();
                if (pLoc.getBlockX() == target.getX() && pLoc.getBlockZ() == target.getZ()
                        && (pLoc.getBlockY() == target.getY() || pLoc.getBlockY() + 1 == target.getY())) {
                    p.sendMessage("§cAfaste-se um pouco para não ficar preso dentro do barril!");
                    return;
                }

                int uses = DrinkType.getBarrelItemUses(item);
                item.setAmount(item.getAmount() - 1);
                BarDrinks.get().getBarrelManager().placeBarrel(target, p.getLocation().getYaw(), uses);
                p.playSound(target.getLocation(), Sound.BLOCK_WOOD_PLACE, 1.0f, 0.8f);
                p.sendMessage("§aBarril de Chopp posicionado! (§f" + uses + "/20 doses§a)");
            }
            return;
        }

        // 5. Se o player estiver banido pelo barman (/bardrink ban) e tentar beber uma bebida do bar -> Dropa no chão!
        if (item.getType() == Material.POTION && item.getItemMeta() instanceof PotionMeta meta && meta.hasCustomModelData()) {
            DrinkType drinkType = DrinkType.fromModelData(meta.getCustomModelData());
            if (drinkType != null || DrinkType.isConnectedMouthpiece(item)) {
                if (BarDrinks.get().getRankingManager().isBanned(p.getUniqueId())) {
                    e.setCancelled(true);
                    if (DrinkType.isConnectedMouthpiece(item)) {
                        BarrelManager.PlacedBarrel b = BarDrinks.get().getBarrelManager().getBarrelHeldBy(p.getUniqueId());
                        if (b != null) {
                            BarDrinks.get().getBarrelManager().disconnectCurrentHolder(b, false);
                        } else {
                            BarDrinks.get().getBarrelManager().removeConnectedMouthpiecesFromPlayer(p);
                        }
                    } else {
                        ItemStack dropped = item.clone();
                        if (e.getHand() == EquipmentSlot.OFF_HAND) {
                            p.getInventory().setItemInOffHand(null);
                        } else {
                            p.getInventory().setItemInMainHand(null);
                        }
                        p.getWorld().dropItemNaturally(p.getLocation(), dropped);
                    }
                    p.sendMessage("§cVocê está banido pelo barman de consumir bebidas do bar!");
                    p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                }
            }
        }
    }

    /**
     * Se um player banido no /bardrink ban tentar pegar bebida do chão, bloqueia ou dropa.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBannedPickupDrink(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (!BarDrinks.get().getRankingManager().isBanned(p.getUniqueId())) return;

        ItemStack stack = e.getItem().getItemStack();
        if (stack.getType() == Material.POTION && stack.getItemMeta() instanceof PotionMeta meta && meta.hasCustomModelData()) {
            if (DrinkType.fromModelData(meta.getCustomModelData()) != null) {
                e.setCancelled(true);
            }
        }
    }

    /**
     * Consumo de bebidas (Garrafas/Latas 1001-1004, Mangueira Conectada 1008 e Água comum).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrink(PlayerItemConsumeEvent e) {
        Player p = e.getPlayer();
        ItemStack item = e.getItem();

        if (item.getType() != Material.POTION) return;
        if (!(item.getItemMeta() instanceof PotionMeta meta)) return;

        // Bloqueia recipientes vazios, barris ou mangueiras avulsas
        if (DrinkType.isEmptyContainer(item) || DrinkType.isBarrelItem(item)
                || (DrinkType.isHoseItem(item) && !DrinkType.isConnectedMouthpiece(item))) {
            e.setCancelled(true);
            return;
        }

        // 1. Gole na Mangueira Conectada ao Barril (CMD 1008)
        if (DrinkType.isConnectedMouthpiece(item)) {
            e.setCancelled(true); // Mantém a mangueira na mão sem virar garrafa vazia!
            BarDrinks.get().getBarrelManager().handleSipFromMouthpiece(p, item);
            return;
        }

        // 2. Água comum reduz 20% da birita (gradativamente em 1,5s)
        if (!meta.hasCustomModelData()) {
            double atual = BarDrinks.get().getDrunkManager().getBiritaExact(p.getUniqueId());
            if (atual > 0.05) {
                BarDrinks.get().getDrunkManager().removeBirita(p, 20.0);
                p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_DRINK, 1.0f, 1.2f);
            }
            return;
        }

        // 3. Bebidas Alcoólicas (Caneca 1001 e Latas 1002, 1003, 1004)
        int model = meta.getCustomModelData();
        DrinkType type = DrinkType.fromModelData(model);
        if (type != null) {
            if (BarDrinks.get().getRankingManager().isBanned(p.getUniqueId())) {
                e.setCancelled(true);
                ItemStack dropped = item.clone();
                p.getInventory().removeItem(item);
                p.getWorld().dropItemNaturally(p.getLocation(), dropped);
                p.sendMessage("§cVocê está banido pelo barman de beber!");
                return;
            }

            BarDrinks.get().getDrunkManager().addBirita(p, type.getBirita());
            BarDrinks.get().getRankingManager().addDrink(p);
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_BURP, 0.75f, 1.1f);

            // Em vez de virar garrafa de vidro, vira o recipiente vazio com o mesmo modelo 3D (impossibilitado de beber) e some logo depois!
            ItemStack emptyContainer = type.createEmptyContainerItem();
            e.setReplacement(emptyContainer);

            Bukkit.getScheduler().runTaskLater(BarDrinks.get(), () -> {
                if (!p.isOnline()) return;
                PlayerInventory inv = p.getInventory();
                for (int i = 0; i < inv.getSize(); i++) {
                    ItemStack slotItem = inv.getItem(i);
                    if (DrinkType.isEmptyContainer(slotItem)) {
                        inv.setItem(i, null);
                        break;
                    }
                }
                if (DrinkType.isEmptyContainer(inv.getItemInOffHand())) {
                    inv.setItemInOffHand(null);
                }
            }, 30L); // Exibe a lata/caneca vazia por 1,5s e depois some
        }
    }

    /**
     * Impede quebrar o bloco de BARRIER interno do barril no criativo sem recolher pelo duplo clique.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreakBarrelBarrier(BlockBreakEvent e) {
        BarrelManager.PlacedBarrel barrel = BarDrinks.get().getBarrelManager().getBarrelAtBlock(e.getBlock());
        if (barrel != null) {
            e.setCancelled(true);
            e.getPlayer().sendMessage("§cPara recolher o barril, retire a mangueira (se houver) e dê 2 cliques com o botão direito nele!");
        }
    }

    /**
     * Impede dropar o bocal da mangueira conectada no chão (e remove recipientes vazios se tentarem dropar).
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDropItem(PlayerDropItemEvent e) {
        ItemStack dropped = e.getItemDrop().getItemStack();
        if (DrinkType.isConnectedMouthpiece(dropped)) {
            e.setCancelled(true);
            e.getPlayer().sendMessage("§cVocê não pode jogar a mangueira no chão! Clique no barril para devolvê-la.");
            return;
        }
        if (DrinkType.isEmptyContainer(dropped)) {
            e.getItemDrop().remove();
        }
    }

    /**
     * Impede trocar a mangueira conectada para a segunda mão ou tirá-la da hotbar.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent e) {
        if (DrinkType.isConnectedMouthpiece(e.getMainHandItem()) || DrinkType.isConnectedMouthpiece(e.getOffHandItem())) {
            e.setCancelled(true);
        }
    }

    /**
     * Quando a corda (Leash) entre o barril e o player rompe, nunca dropa laço no chão,
     * remove a mangueira da hotbar do player e volta o barril para o modelo 1006.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityUnleash(EntityUnleashEvent e) {
        if (BarDrinks.get().getBarrelManager().isBarrelLeashEntity(e.getEntity())) {
            e.setDropLeash(false);
            BarrelManager.PlacedBarrel barrel = BarDrinks.get().getBarrelManager().getBarrelByEntity(e.getEntity());
            if (barrel != null && barrel.currentHolder != null) {
                Bukkit.getScheduler().runTask(BarDrinks.get(), () ->
                        BarDrinks.get().getBarrelManager().disconnectCurrentHolder(barrel, true));
            }
        }
    }

    /**
     * Controle de movimento durante o Apagão (Bebo 3):
     * - Enquanto estiver nos 8s de cegueira: completamente imóvel.
     * - Quando acabar a cegueira: levanta assim que se mover!
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        EffectManager.LyingSession session = BarDrinks.get().getEffectManager().getLyingSession(p.getUniqueId());
        if (session == null) return;

        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null) return;

        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double dy = to.getY() - from.getY();

        if (session.isLockedMovement()) {
            if ((dx * dx + dz * dz) > 0.001 || dy > 0.01) {
                Location lockLoc = session.getStartLoc().clone();
                lockLoc.setYaw(to.getYaw());
                lockLoc.setPitch(to.getPitch());
                e.setTo(lockLoc);
            }
        } else if (session.isWakeOnMove()) {
            if ((dx * dx + dz * dz) > 0.015 || dy > 0.05) {
                BarDrinks.get().getEffectManager().stopLying(p.getUniqueId());
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        BarDrinks.get().getBarrelManager().removeConnectedMouthpiecesFromPlayer(p);
        double birita = BarDrinks.get().getDrunkManager().getBiritaExact(p.getUniqueId());
        BarDrinks.get().getBossBarManager().handlePlayerJoin(p, birita);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        BarrelManager.PlacedBarrel held = BarDrinks.get().getBarrelManager().getBarrelHeldBy(p.getUniqueId());
        if (held != null) {
            BarDrinks.get().getBarrelManager().disconnectCurrentHolder(held, false);
        }
        BarDrinks.get().getBarrelManager().removeConnectedMouthpiecesFromPlayer(p);
        BarDrinks.get().getEffectManager().cleanupPlayer(p);
        BarDrinks.get().getRankingManager().saveDataAsync();
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        e.getDrops().removeIf(item -> DrinkType.isConnectedMouthpiece(item) || DrinkType.isEmptyContainer(item));
        BarrelManager.PlacedBarrel held = BarDrinks.get().getBarrelManager().getBarrelHeldBy(p.getUniqueId());
        if (held != null) {
            BarDrinks.get().getBarrelManager().disconnectCurrentHolder(held, false);
        }
        BarDrinks.get().getEffectManager().cleanupPlayer(p);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityToggleSwim(EntityToggleSwimEvent e) {
        if (e.getEntity() instanceof Player p) {
            if (BarDrinks.get().getEffectManager().isLyingDown(p.getUniqueId())) {
                if (!e.isSwimming()) {
                    e.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onDismount(EntityDismountEvent e) {
        if (e.getEntity() instanceof Player p) {
            if (BarDrinks.get().getEffectManager().isSitting(p.getUniqueId())) {
                BarDrinks.get().getEffectManager().stopSitting(p.getUniqueId());
            } else if (BarDrinks.get().getEffectManager().isSeatEntity(e.getDismounted())) {
                e.getDismounted().remove();
            }
        }
    }

    /**
     * Menus GUI (/bardrink menu, /loja bar e Sub-HUD do Barril) + proteção da mangueira conectada no inventário.
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;

        // Bloqueia mover o bocal da mangueira conectada no inventário
        if (DrinkType.isConnectedMouthpiece(e.getCurrentItem()) || DrinkType.isConnectedMouthpiece(e.getCursor())) {
            e.setCancelled(true);
            return;
        }
        if (e.getHotbarButton() >= 0) {
            ItemStack hotbarItem = p.getInventory().getItem(e.getHotbarButton());
            if (DrinkType.isConnectedMouthpiece(hotbarItem)) {
                e.setCancelled(true);
                return;
            }
        }

        String title = e.getView().getTitle();

        // 1. HUD Principal da /loja bar
        if (BarShopManager.MAIN_SHOP_TITLE.equals(title)) {
            e.setCancelled(true);
            if (e.getRawSlot() >= 0 && e.getRawSlot() < 27) {
                BarDrinks.get().getBarShopManager().handleMainShopClick(p, e.getRawSlot());
            }
            return;
        }

        // 2. Sub-HUD do Barril na /loja bar
        if (BarShopManager.BARREL_SUB_TITLE.equals(title)) {
            e.setCancelled(true);
            if (e.getRawSlot() >= 0 && e.getRawSlot() < 27) {
                BarDrinks.get().getBarShopManager().handleBarrelSubMenuClick(p, e.getRawSlot());
            }
            return;
        }

        // 3. Menu administrativo do /bardrink menu
        if (GUI_TITLE.equals(title)) {
            e.setCancelled(true);
            ItemStack clicked = e.getCurrentItem();
            if (clicked == null || clicked.getType() != Material.POTION) return;
            if (!(clicked.getItemMeta() instanceof PotionMeta meta) || !meta.hasCustomModelData()) return;

            if (DrinkType.isBarrelItem(clicked)) {
                if (!BarDrinks.get().getBarrelManager().hasInventorySpace(p)) {
                    p.sendMessage("§cSem espaço necessário no inventário");
                    return;
                }
                p.getInventory().addItem(DrinkType.createBarrelItem(DrinkType.BARRIL_MAX_USES));
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
                p.sendMessage("§aVocê pegou §f1x §6Barril de Chopp§a!");
                return;
            }

            if (DrinkType.isHoseItem(clicked)) {
                if (!BarDrinks.get().getBarrelManager().hasInventorySpace(p)) {
                    p.sendMessage("§cSem espaço necessário no inventário");
                    return;
                }
                p.getInventory().addItem(DrinkType.createHoseItem(DrinkType.MANGUEIRA_MAX_USES));
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
                p.sendMessage("§aVocê pegou §f1x §eMangueira de Chopp§a!");
                return;
            }

            DrinkType type = DrinkType.fromModelData(meta.getCustomModelData());
            if (type == null) return;

            if (!BarDrinks.get().getBarrelManager().hasInventorySpace(p)) {
                p.sendMessage("§cSem espaço necessário no inventário");
                return;
            }
            p.getInventory().addItem(type.createItem());
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
            p.sendMessage("§aVocê pegou §f1x " + type.getDisplayName() + "§a!");
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent e) {
        if (DrinkType.isConnectedMouthpiece(e.getOldCursor()) || DrinkType.isConnectedMouthpiece(e.getCursor())) {
            e.setCancelled(true);
            return;
        }
        String title = e.getView().getTitle();
        if (BarShopManager.MAIN_SHOP_TITLE.equals(title)
                || BarShopManager.BARREL_SUB_TITLE.equals(title)
                || GUI_TITLE.equals(title)) {
            e.setCancelled(true);
        }
    }
}
