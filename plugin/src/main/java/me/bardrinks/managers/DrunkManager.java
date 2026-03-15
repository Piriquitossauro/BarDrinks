package me.bardrinks.managers;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DrunkManager {

    private final Map<UUID, Integer> birita = new HashMap<>();
    private final Map<UUID, Long> lastDrink = new HashMap<>();

    public int getBirita(Player p) {
        return birita.getOrDefault(p.getUniqueId(), 0);
    }

    public void addBirita(Player p, int amount) {

        int atual = getBirita(p);

        int novo = Math.min(100, atual + amount);

        birita.put(p.getUniqueId(), novo);
        lastDrink.put(p.getUniqueId(), System.currentTimeMillis());
    }

    public void removeBirita(Player p, int amount) {

        int atual = getBirita(p);

        int novo = Math.max(0, atual - amount);

        birita.put(p.getUniqueId(), novo);
    }

    public long getLastDrink(Player p) {

        return lastDrink.getOrDefault(p.getUniqueId(), 0L);
    }
}
