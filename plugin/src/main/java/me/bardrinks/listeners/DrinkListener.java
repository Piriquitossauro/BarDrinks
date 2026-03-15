package me.bardrinks.listeners;

import me.bardrinks.BarDrinks;
import me.bardrinks.models.DrinkType;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.meta.PotionMeta;

public class DrinkListener implements Listener {

    @EventHandler
    public void onDrink(PlayerItemConsumeEvent e) {

        Player p = e.getPlayer();

        // 💧 Água reduz birita
        if (e.getItem().getType() == Material.POTION) {

            if (e.getItem().getItemMeta() instanceof PotionMeta meta) {

                if (!meta.hasCustomModelData()) {

                    BarDrinks.get().getDrunkManager().removeBirita(p, 20);

                    int birita = BarDrinks.get().getDrunkManager().getBirita(p);

                    BarDrinks.get().getBossBarManager().updateBar(p, birita);

                    return;
                }
            }
        }

        // 🍺 Bebidas alcoólicas
        if (!(e.getItem().getItemMeta() instanceof PotionMeta))
            return;

        PotionMeta meta = (PotionMeta) e.getItem().getItemMeta();

        if (!meta.hasCustomModelData())
            return;

        int model = meta.getCustomModelData();

        for (DrinkType type : DrinkType.values()) {

            if (type.getModelData() == model) {

                BarDrinks.get().getDrunkManager().addBirita(p, type.getBirita());

                int birita = BarDrinks.get().getDrunkManager().getBirita(p);

                BarDrinks.get().getBossBarManager().updateBar(p, birita);

                BarDrinks.get().getRankingManager().addDrink(p);

                return;
            }
        }
    }
}
