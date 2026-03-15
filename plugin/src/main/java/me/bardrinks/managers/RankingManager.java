package me.bardrinks.managers;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RankingManager {

    private final Map<UUID, Integer> drinks = new HashMap<>();

    public void addDrink(Player p) {

        drinks.put(
                p.getUniqueId(),
                drinks.getOrDefault(p.getUniqueId(), 0) + 1
        );
    }
}
