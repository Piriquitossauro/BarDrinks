package me.bardrinks.managers;

import org.bukkit.Bukkit;
import org.bukkit.boss.*;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BossBarManager {

    private final Map<UUID, BossBar> bars = new HashMap<>();

    public void updateBar(Player p, int birita) {

        BossBar bar = bars.computeIfAbsent(
                p.getUniqueId(),
                id -> Bukkit.createBossBar("§6Biritômetro", BarColor.YELLOW, BarStyle.SOLID)
        );

        bar.setProgress(birita / 100.0);

        if (!bar.getPlayers().contains(p))
            bar.addPlayer(p);
    }
}
