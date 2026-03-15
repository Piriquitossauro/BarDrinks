package me.bardrinks.listeners;

import me.bardrinks.BarDrinks;
import me.bardrinks.models.DrinkType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.meta.PotionMeta;

public class DrinkListener implements Listener {

    @EventHandler
    public void onDrink(PlayerItemConsumeEvent e) {

        if (!(e.getItem().getItemMeta() instanceof PotionMeta))
            return;

        PotionMeta meta = (PotionMeta) e.getItem().getItemMeta();

        if (!meta.hasCustomModelData())
            return;

        int model = meta.getCustomModelData();

        Player p = e.getPlayer();

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
