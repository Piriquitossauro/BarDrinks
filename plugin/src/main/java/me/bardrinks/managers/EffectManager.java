package me.bardrinks.managers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import java.util.HashMap;
import java.util.UUID;

public class EffectManager {

    private final HashMap<UUID, Long> lastAction = new HashMap<>();

    public void applyEffects(Player p, int birita) {
        // Se baixar de 75, limpa as partículas de Sorte
        if (birita <= 75) {
            if (p.hasPotionEffect(PotionEffectType.LUCK)) p.removePotionEffect(PotionEffectType.LUCK);
            if (birita > 40) wobble(p, 1.8);
            return;
        }

        // --- FASE 4 (Birita > 75) ---
        wobble(p, 3.0);
        
        // Náusea (Sem partículas próprias)
        p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 200, 0, false, false, false));

        // Efeito de Partículas (Sorte = Verde poção)
        if (!p.hasPotionEffect(PotionEffectType.LUCK)) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, Integer.MAX_VALUE, 0, false, true, false));
        }

        trySitOrLay(p, birita);
    }

    private void trySitOrLay(Player p, int birita) {
        // 🛡️ TRAVA 1: Se já estiver sentado ou deitado, não faz nada
        if (p.isInsideVehicle() || p.isSleeping()) return;

        // 🛡️ TRAVA 2: Se estiver no AR, não tenta sentar (evita erro do GSit)
        if (!p.isOnGround()) return;

        long now = System.currentTimeMillis();
        long last = lastAction.getOrDefault(p.getUniqueId(), 0L);

        if (now - last < 10000) return;
        if (birita < 60) return;

        double chance = Math.random();

        // O /lay é mais instável, então damos um tempo maior para ele processar
        if (chance < 0.04) { 
            lastAction.put(p.getUniqueId(), now);
            p.performCommand("lay");
            forceUpdate(p); // Tenta evitar que o player suma
        } else if (chance < 0.05) {
            lastAction.put(p.getUniqueId(), now);
            p.performCommand("sit");
            forceUpdate(p);
        }
    }

    private void forceUpdate(Player p) {
        // Espera 2 ticks para o GSit terminar de colocar o player na entidade
        // e aí dá um "refresh" na skin dele
        Bukkit.getScheduler().runTaskLater(
            Bukkit.getPluginManager().getPlugin("BarDrinks"), 
            () -> {
                if (p.isOnline() && p.isInsideVehicle()) {
                    // Teleporte milimétrico para forçar o servidor a reenviar o modelo do player
                    p.teleport(p.getLocation().add(0, 0.01, 0));
                }
            }, 2L);
    }

    private void wobble(Player p, double strength) {
        Vector direction = p.getLocation().getDirection().normalize();
        Vector sideways = new Vector(-direction.getZ(), 0, direction.getX());
        double random = (Math.random() - 0.5);
        if (Math.abs(random) < 0.2) return;
        p.setVelocity(p.getVelocity().add(sideways.multiply(random * strength)));
    }
}
