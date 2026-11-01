package me.bardrinks.managers;

import me.bardrinks.models.DrunkStage;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BossBarManager {

    private final Map<UUID, BossBar> bars = new HashMap<>();

    public void updateBar(Player p, double birita) {
        if (p == null || !p.isOnline()) return;

        if (birita <= 0.05) {
            removeBar(p.getUniqueId());
            return;
        }

        double clamped = Math.max(0.0, Math.min(100.0, birita));
        int roundedPct = (int) Math.round(clamped);
        DrunkStage stage = DrunkStage.fromPercentage(clamped);

        String title = "§6Biritômetro: §f" + roundedPct + "% §7(" + stage.getDisplayName() + "§7)";

        BossBar bar = bars.computeIfAbsent(
                p.getUniqueId(),
                id -> Bukkit.createBossBar(title, stage.getBarColor(), BarStyle.SOLID)
        );

        bar.setTitle(title);
        if (bar.getColor() != stage.getBarColor()) {
            bar.setColor(stage.getBarColor());
        }
        bar.setProgress(Math.max(0.0, Math.min(1.0, clamped / 100.0)));

        if (!bar.getPlayers().contains(p)) {
            bar.removeAll();
            bar.addPlayer(p);
        }
    }

    public void handlePlayerJoin(Player p, double birita) {
        BossBar existing = bars.get(p.getUniqueId());
        if (existing != null) {
            existing.removeAll();
        }
        if (birita > 0.05) {
            updateBar(p, birita);
        }
    }

    public void removeBar(UUID uuid) {
        BossBar bar = bars.remove(uuid);
        if (bar != null) {
            bar.removeAll();
        }
    }

    public void removeAll() {
        for (BossBar bar : bars.values()) {
            bar.removeAll();
        }
        bars.clear();
    }
}
