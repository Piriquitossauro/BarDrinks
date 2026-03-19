package me.bardrinks.managers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.UUID;

public class EffectManager {

    // ⏱ cooldown pra evitar spam de sit/lay
    private final HashMap<UUID, Long> lastAction = new HashMap<>();

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

            trySitOrLay(p, birita);
        }
    }

    private void wobble(Player p, double strength) {

        Vector direction = p.getLocation().getDirection().normalize();

        Vector sideways = new Vector(-direction.getZ(), 0, direction.getX());

        double random = (Math.random() - 0.5);

        // evita micro tremida fraca
        if (Math.abs(random) < 0.2)
            return;

        Vector push = sideways.multiply(random * strength);

        p.setVelocity(p.getVelocity().add(push));
    }

    private void trySitOrLay(Player p, int birita) {

        // não tenta se já está sentado/montado
        if (p.isInsideVehicle())
            return;

        long now = System.currentTimeMillis();

        long last = lastAction.getOrDefault(p.getUniqueId(), 0L);

        // ⏱ cooldown de 5 segundos
        if (now - last < 5000)
            return;

        // 🍺 só ativa quando MUITO bêbado
        if (birita < 60)
            return;

        double chance = Math.random();

        if (chance < 0.10) { // 5% sentar

            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "sit " + p.getName());
            lastAction.put(p.getUniqueId(), now);

        } else if (chance < 0.15) { // 2% deitar

            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lay " + p.getName());
            lastAction.put(p.getUniqueId(), now);
        }
    }
}
