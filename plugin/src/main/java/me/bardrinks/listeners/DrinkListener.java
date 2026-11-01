package me.bardrinks.listeners;

import me.bardrinks.BarDrinks;
import me.bardrinks.models.DrinkType;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityToggleSwimEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;

public class DrinkListener implements Listener {

    public static final String GUI_TITLE = "§6§lBarDrinks - Menu de Bebidas";

    @EventHandler
    public void onDrink(PlayerItemConsumeEvent e) {
        Player p = e.getPlayer();
        ItemStack item = e.getItem();

        if (item.getType() != Material.POTION) return;
        if (!(item.getItemMeta() instanceof PotionMeta meta)) return;

        // 💧 Garrafa de Água comum reduz 20% da birita (gradativamente em 1,5s)
        if (!meta.hasCustomModelData()) {
            double atual = BarDrinks.get().getDrunkManager().getBiritaExact(p.getUniqueId());
            if (atual > 0.05) {
                BarDrinks.get().getDrunkManager().removeBirita(p, 20.0);
                p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_DRINK, 1.0f, 1.2f);
            }
            return;
        }

        // 🍺 Bebidas Alcoólicas do BarDrinks
        int model = meta.getCustomModelData();
        DrinkType type = DrinkType.fromModelData(model);
        if (type != null) {
            BarDrinks.get().getDrunkManager().addBirita(p, type.getBirita());
            BarDrinks.get().getRankingManager().addDrink(p);
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_BURP, 0.75f, 1.1f);
        }
    }

    /**
     * Mantém a BossBar e o efeito quando o jogador desloga e loga novamente.
     */
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        double birita = BarDrinks.get().getDrunkManager().getBiritaExact(p.getUniqueId());
        BarDrinks.get().getBossBarManager().handlePlayerJoin(p, birita);
    }

    /**
     * Ao sair do servidor, limpa entidades temporárias (assento/barreira) mas preserva a birita e a BossBar para quando voltar.
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        BarDrinks.get().getEffectManager().cleanupPlayer(p);
        BarDrinks.get().getRankingManager().saveDataAsync();
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        BarDrinks.get().getEffectManager().cleanupPlayer(p);
    }

    /**
     * Trava a pose deitada no chão (mesma lógica do Pecado da Preguiça).
     */
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

    /**
     * Remove imediatamente o ArmorStand invisível quando o player levanta de estar sentado.
     */
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
     * Menu GUI interativo do comando /bardrink menu.
     */
    @EventHandler
    public void onMenuClick(InventoryClickEvent e) {
        if (!GUI_TITLE.equals(e.getView().getTitle())) return;
        e.setCancelled(true);

        if (!(e.getWhoClicked() instanceof Player p)) return;
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() != Material.POTION) return;
        if (!(clicked.getItemMeta() instanceof PotionMeta meta) || !meta.hasCustomModelData()) return;

        DrinkType type = DrinkType.fromModelData(meta.getCustomModelData());
        if (type == null) return;

        int amount = e.isShiftClick() ? 16 : 1;
        p.getInventory().addItem(type.createItem(amount));
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
        p.sendMessage("§aVocê pegou §f" + amount + "x " + type.getDisplayName() + "§a!");
    }
}
