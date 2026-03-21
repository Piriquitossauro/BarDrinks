package me.bardrinks.managers;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import java.util.HashMap;
import java.util.UUID;

public class EffectManager {

    private final HashMap<UUID, Long> lastAction = new HashMap<>();

    public void applyEffects(Player p, int birita) {

        // Se a birita baixou da fase 4, remove as partículas de "Sorte"
        if (birita <= 75) {
            p.removePotionEffect(PotionEffectType.LUCK);
            if (birita > 40) {
                wobble(p, 1.8);
            }
            return;
        }

        // --- FASE 4 (Birita > 75) ---
        wobble(p, 3.0);

        // Efeito de Náusea (Sem partículas próprias pra não poluir)
        p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 200, 0, false, false, false));

        // Efeito de PARTÍCULAS constantes (Sorte = Verde)
        // Mantemos mesmo se estiver sentado/deitado conforme seu pedido
        if (!p.hasPotionEffect(PotionEffectType.LUCK)) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, Integer.MAX_VALUE, 0, false, true, false));
        }

        trySitOrLay(p, birita);
    }

    private void trySitOrLay(Player p, int birita) {
        // Se já estiver sentado, não tenta sentar de novo
        if (p.isInsideVehicle()) return;

        long now = System.currentTimeMillis();
        long last = lastAction.getOrDefault(p.getUniqueId(), 0L);

        if (now - last < 8000) return;
        if (birita < 60) return;

        double chance = Math.random();

        if (chance < 0.04) {
            p.performCommand("lay");
            lastAction.put(p.getUniqueId(), now);
            fixInvisibility(p); // Tenta forçar o player a aparecer
            return;
        }

        if (chance < 0.05) {
            p.performCommand("sit");
            lastAction.put(p.getUniqueId(), now);
            fixInvisibility(p); // Tenta forçar o player a aparecer
        }
    }

    // Método para tentar mitigar o bug de ficar invisível no GSit
    private void fixInvisibility(Player p) {
        // Teleportar o player para a própria localização dele 1 tick depois
        // Isso costuma forçar o refresh do modelo do player para os outros e para si mesmo
        org.bukkit.Bukkit.getScheduler().runTaskLater(
            org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(EffectManager.class), 
            () -> {
                if (p.isOnline()) {
                    p.teleport(p.getLocation());
                }
            }, 1L); 
    }

    private void wobble(Player p, double strength) {
        Vector direction = p.getLocation().getDirection().normalize();
        Vector sideways = new Vector(-direction.getZ(), 0, direction.getX());
        double random = (Math.random() - 0.5);
        if (Math.abs(random) < 0.2) return;
        Vector push = sideways.multiply(random * strength);
        p.setVelocity(p.getVelocity().add(push));
    }
}
