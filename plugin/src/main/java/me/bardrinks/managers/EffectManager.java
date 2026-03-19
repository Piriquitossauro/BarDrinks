package me.bardrinks.managers;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public class EffectManager {

    public void applyEffects(Player p, int birita) {

        if (birita <= 40)
            return;

        if (birita <= 75) {

            wobble(p, 1.8);

        } else {

            wobble(p, 3.0);

            p.addPotionEffect(
                    new PotionEffect(
                            PotionEffectType.NAUSEA,
                            200,
                            0,
                            false,
                            false
                    )
            );
          // 🍺 chance de sentar
    if (Math.random() < 0.05) { // 5%
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "sit " + p.getName());
    }

    // 🍺 chance de deitar (mais raro)
    if (Math.random() < 0.02) { // 2%
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lay " + p.getName());
    }
}

    private void wobble(Player p, double strength) {

        Vector direction = p.getLocation().getDirection().normalize();

        Vector sideways = new Vector(-direction.getZ(), 0, direction.getX());

        double random = (Math.random() - 0.5);

        Vector push = sideways.multiply(random * strength);

        p.setVelocity(p.getVelocity().add(push));
    }
}
