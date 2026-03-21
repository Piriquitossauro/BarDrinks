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
        
        // 🛡️ REGRA DE OURO: Se o player já estiver sentado, deitado ou em qualquer veículo,
        // o plugin PARA IMEDIATAMENTE. Não mexe em velocidade, não tenta sentar, não faz nada.
        // Isso evita o conflito com o GSit que causa o sumiço e o pisca-pisca.
        if (p.isInsideVehicle() || p.isSleeping()) {
            return; 
        }

        // Se a birita baixou da fase crítica, limpa as partículas de Sorte
        if (birita <= 75) {
            if (p.hasPotionEffect(PotionEffectType.LUCK)) p.removePotionEffect(PotionEffectType.LUCK);
            if (birita > 40) wobble(p, 1.8);
            return;
        }

        // --- FASE 4 (Birita > 75) ---
        // Só balança se NÃO estiver sentado (a trava lá em cima já garante isso)
        wobble(p, 3.0);
        
        // Náusea (Sem partículas próprias)
        p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 200, 0, false, false, false));

        // Partículas verdes constantes (LUCK)
        if (!p.hasPotionEffect(PotionEffectType.LUCK)) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, Integer.MAX_VALUE, 0, false, true, false));
        }

        trySitOrLay(p, birita);
    }

    private void trySitOrLay(Player p, int birita) {
        UUID uuid = p.getUniqueId();
        long now = System.currentTimeMillis();
        long last = lastAction.getOrDefault(uuid, 0L);

        // Cooldown de 20 segundos para ser bem conservador e evitar bugs de animação
        if (now - last < 20000) return;
        
        // SÓ tenta se estiver no chão, com birita alta e SEM estar em movimento brusco
        if (birita < 60 || !p.isOnGround()) return;

        double chance = Math.random();

        // 🛡️ Ação Silenciosa: Salvamos o tempo ANTES de rodar o comando
        if (chance < 0.04) { 
            lastAction.put(uuid, now); 
            p.performCommand("lay");
        } else if (chance < 0.05) { 
            lastAction.put(uuid, now); 
            p.performCommand("sit");
        }
    }

    private void wobble(Player p, double strength) {
        // Trava redundante: nunca mexer na velocidade de quem está sentado
        if (p.isInsideVehicle()) return;

        Vector direction = p.getLocation().getDirection().normalize();
        Vector sideways = new Vector(-direction.getZ(), 0, direction.getX());
        double random = (Math.random() - 0.5);
        
        if (Math.abs(random) < 0.2) return;
        
        // Aplica a força de balanço
        p.setVelocity(p.getVelocity().add(sideways.multiply(random * strength)));
    }
}
