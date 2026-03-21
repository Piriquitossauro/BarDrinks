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
        
        // 🛡️ Se já estiver no chão ou sentado, não faz NADA (evita o loop do fantasma)
        if (p.isInsideVehicle() || p.isSleeping()) {
            return; 
        }

        if (birita <= 75) {
            if (p.hasPotionEffect(PotionEffectType.LUCK)) p.removePotionEffect(PotionEffectType.LUCK);
            if (birita > 40) wobble(p, 1.8);
            return;
        }

        // --- FASE 4 (Birita > 75) ---
        wobble(p, 3.0);
        
        // Náusea (Sem ícone)
        p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 200, 0, false, false, false));

        // Partículas verdes constantes
        if (!p.hasPotionEffect(PotionEffectType.LUCK)) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, Integer.MAX_VALUE, 0, false, true, false));
        }

        trySitOrLay(p, birita);
    }

    private void trySitOrLay(Player p, int birita) {
        UUID uuid = p.getUniqueId();
        long now = System.currentTimeMillis();
        long last = lastAction.getOrDefault(uuid, 0L);

        // ⏱️ Cooldown de 15 segundos: O segredo para parar o "pisca-pisca"
        if (now - last < 15000) return;
        
        // Só tenta se estiver no chão e com a birita alta (> 60 para deitar/sentar)
        if (birita < 60 || !p.isOnGround()) return;

        double chance = Math.random();

        // Salva o tempo ANTES de executar o comando para travar o próximo loop imediatamente
        if (chance < 0.04) { 
            lastAction.put(uuid, now); 
            p.performCommand("lay");
        } else if (chance < 0.05) { 
            lastAction.put(uuid, now); 
            p.performCommand("sit");
        }
    }

    private void wobble(Player p, double strength) {
        // Se estiver montado em algo, o velocity buga a renderização da skin
        if (p.isInsideVehicle()) return;

        Vector direction = p.getLocation().getDirection().normalize();
        Vector sideways = new Vector(-direction.getZ(), 0, direction.getX());
        double random = (Math.random() - 0.5);
        if (Math.abs(random) < 0.2) return;
        
        p.setVelocity(p.getVelocity().add(sideways.multiply(random * strength)));
    }
}
