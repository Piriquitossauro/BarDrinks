package me.bardrinks.managers;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.UUID;

public class EffectManager {

    private final HashMap<UUID, Long> lastAction = new HashMap<>();
    // Guarda a nuvem de partículas para cada jogador
    private final HashMap<UUID, AreaEffectCloud> activeClouds = new HashMap<>();

    public void applyEffects(Player p, int birita) {

        // 🚫 Se estiver sentado/deitado, remove as partículas pra não bugar a visão
        if (p.isInsideVehicle()) {
            removeParticles(p);
            return;
        }

        // Se a birita baixou da fase crítica, limpa o efeito visual
        if (birita <= 75) {
            removeParticles(p);
            if (birita > 40) {
                wobble(p, 1.8);
            }
            return;
        }

        // --- FASE 4 (Birita > 75) ---
        wobble(p, 3.0);

        p.addPotionEffect(
                new PotionEffect(
                        PotionEffectType.NAUSEA,
                        200,
                        0,
                        false,
                        false,
                        false
                )
        );

        // Aplica o efeito visual contínuo
        startPersistentParticles(p);

        trySitOrLay(p, birita);
    }

    private void startPersistentParticles(Player p) {
        UUID id = p.getUniqueId();

        // Se já tem uma nuvem ativa e válida, não precisa criar outra
        if (activeClouds.containsKey(id)) {
            if (activeClouds.get(id).isValid()) return;
            else activeClouds.remove(id);
        }

        Color poisonGreen = Color.fromRGB(79, 147, 36);
        
        // Cria a nuvem de efeito de área (invisível, só solta partícula)
        AreaEffectCloud cloud = p.getWorld().spawn(p.getLocation(), AreaEffectCloud.class);
        
        cloud.setParticle(Particle.ENTITY_EFFECT);
        cloud.setColor(poisonGreen);
        cloud.setRadius(0.2f);           // Raio pequeno para as partículas saírem do corpo
        cloud.setDuration(1200);         // Dura 1 minuto (será renovado ou removido pelo plugin)
        cloud.setWaitTime(0);
        
        // Faz a nuvem seguir o player (anexa como passageiro invisível)
        p.addPassenger(cloud); 
        
        activeClouds.put(id, cloud);
    }

    private void removeParticles(Player p) {
        AreaEffectCloud cloud = activeClouds.remove(p.getUniqueId());
        if (cloud != null && cloud.isValid()) {
            cloud.remove();
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
        if (p.isInsideVehicle()) return;

        long now = System.currentTimeMillis();
        long last = lastAction.getOrDefault(p.getUniqueId(), 0L);

        if (now - last < 8000) return;
        if (birita < 60) return;

        double chance = Math.random();

        if (chance < 0.04) { // 4% de chance de deitar
            p.performCommand("lay");
            lastAction.put(p.getUniqueId(), now);
            removeParticles(p); // Remove as partículas ao deitar pra não atrapalhar
            return;
        }

        if (chance < 0.05) { // 5% de chance de sentar
            p.performCommand("sit");
            lastAction.put(p.getUniqueId(), now);
            removeParticles(p);
        }
    }
}
