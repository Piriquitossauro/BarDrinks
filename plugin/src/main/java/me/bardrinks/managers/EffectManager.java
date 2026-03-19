package me.bardrinks.managers;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.bukkit.Particle;
import java.util.HashMap;
import java.util.UUID;

public class EffectManager {

    private final HashMap<UUID, Long> lastAction = new HashMap<>();

    public void applyEffects(Player p, int birita) {

        // 🚫 não aplica efeito se estiver sentado/deitado
        if (p.isInsideVehicle())
            return;

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
             if (Math.random() < 0.25) {

        p.getWorld().spawnParticle(
                Particle.ENTITY_EFFECT,
                p.getLocation().add(0, 1, 0),
                3,
                0.2, 0.4, 0.2,
                0
             trySitOrLay(p, birita);
        );
    }
}



    private void wobble(Player p, double strength) {

        Vector direction = p.getLocation().getDirection().normalize();

        Vector sideways = new Vector(-direction.getZ(), 0, direction.getX());

        double random = (Math.random() - 0.5);

        if (Math.abs(random) < 0.2)
            return;

        Vector push = sideways.multiply(random * strength);

        p.setVelocity(p.getVelocity().add(push));
    }

    private void trySitOrLay(Player p, int birita) {

        if (p.isInsideVehicle())
            return;

        long now = System.currentTimeMillis();
        long last = lastAction.getOrDefault(p.getUniqueId(), 0L);

        // ⏱ cooldown maior
        if (now - last < 8000)
            return;

        if (birita < 60)
            return;

        double chance = Math.random();

        // deitar (bem raro)
        if (chance < 0.08) {

            p.performCommand("lay");
            lastAction.put(p.getUniqueId(), now);
            return;
        }

        // sentar
        if (chance < 0.10) {

            p.performCommand("sit");
            lastAction.put(p.getUniqueId(), now);
        }
    }
}
