package me.bardrinks.managers;

import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.UUID;

public class EffectManager {

    private final HashMap<UUID, Long> lastAction = new HashMap<>();

    public void applyEffects(Player p, int birita) {

        // --- FASE 4 (Birita > 75) ---
        if (birita > 75) {
            
            // 🚫 Se estiver sentado/deitado, remove as partículas pra não bugar a visão
            if (p.isInsideVehicle()) {
                p.removePotionEffect(PotionEffectType.LUCK); // Remove as partículas ao sentar
                return;
            }

            wobble(p, 3.0);

            // Náusea persistente por 200 ticks (10s)
            p.addPotionEffect(
                    new PotionEffect(
                            PotionEffectType.NAUSEA,
                            200, // Duração
                            0,   // Amplificador
                            false, // Ambiente
                            false, // Partículas
                            false  // Ícone
                    )
            );

            // AQUI O SEGREDO: Adiciona o efeito de SORTE para particulas contínuas
            // É leve, nativo e verde!
            p.addPotionEffect(
                    new PotionEffect(
                            PotionEffectType.LUCK, // Efeito de Sorte (é verde!)
                            Integer.MAX_VALUE,      // Duração quase infinita (será removido manualmente)
                            0,                      // Amplificador
                            false,                  // Ambiente (não é de farol/becon)
                            true,                   // SIM, MOSTRAR PARTÍCULAS
                            false                   // NÃO MOSTRAR ÍCONE
                    )
            );

            trySitOrLay(p, birita);
            return; // Interrompe aqui para não rodar a lógica das outras fases
        }

        // --- FASE 3 (40 < Birita <= 75) ---
        if (birita > 40) {
            
            // Remove as partículas da Fase 4
            p.removePotionEffect(PotionEffectType.LUCK);
            
            wobble(p, 1.8);
            return; // Interrompe
        }

        // --- FASE 1 ou 2 (Birita <= 40) ---
        // Garante que as partículas sejam removidas ao baixar a birita
        p.removePotionEffect(PotionEffectType.LUCK);
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
        if (p.isInsideVehicle()) return;

        long now = System.currentTimeMillis();
        long last = lastAction.getOrDefault(p.getUniqueId(), 0L);

        // Cooldown de 8 segundos
        if (now - last < 8000) return;
        if (birita < 60) return;

        double chance = Math.random();

        if (chance < 0.04) { // 4% de chance de deitar
            p.performCommand("lay");
            lastAction.put(p.getUniqueId(), now);
             // Remove as partículas ao deitar pra não atrapalhar
            return;
        }

        if (chance < 0.05) { // 5% de chance de sentar
            p.performCommand("sit");
            lastAction.put(p.getUniqueId(), now);
            
        }
    }
}
