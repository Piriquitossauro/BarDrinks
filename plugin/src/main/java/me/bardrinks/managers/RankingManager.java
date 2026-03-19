package me.bardrinks.managers;

import org.bukkit.entity.Player;

import java.util.*;

public class RankingManager {

    private final HashMap<UUID, Integer> drinks = new HashMap<>();

    public void addDrink(Player p) {

        drinks.put(p.getUniqueId(),
                drinks.getOrDefault(p.getUniqueId(), 0) + 1
        );
    }

    public List<Map.Entry<UUID, Integer>> getTop() {

        List<Map.Entry<UUID, Integer>> list = new ArrayList<>(drinks.entrySet());

        list.sort((a, b) -> b.getValue() - a.getValue());

        return list;
    }
}
