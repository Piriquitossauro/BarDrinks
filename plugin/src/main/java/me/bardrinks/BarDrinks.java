package me.bardrinks;

import me.bardrinks.listeners.ChatListener;
import me.bardrinks.listeners.DrinkListener;
import me.bardrinks.managers.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class BarDrinks extends JavaPlugin {

    private static BarDrinks instance;

    private DrunkManager drunkManager;
    private BossBarManager bossBarManager;
    private EffectManager effectManager;
    private RankingManager rankingManager;

    public static BarDrinks get() {
        return instance;
    }

    public DrunkManager getDrunkManager() {
        return drunkManager;
    }

    public BossBarManager getBossBarManager() {
        return bossBarManager;
    }

    public RankingManager getRankingManager() {
        return rankingManager;
    }

    @Override
    public void onEnable() {

        instance = this;

        drunkManager = new DrunkManager();
        bossBarManager = new BossBarManager();
        effectManager = new EffectManager();
        rankingManager = new RankingManager();

        Bukkit.getPluginManager().registerEvents(new DrinkListener(), this);
        Bukkit.getPluginManager().registerEvents(new ChatListener(), this);

        startSobrietySystem();
        startEffectSystem();

        getLogger().info("BarDrinks iniciado!");
    }

    private void startSobrietySystem() {

        Bukkit.getScheduler().runTaskTimer(this, () -> {

            for (Player p : Bukkit.getOnlinePlayers()) {

                int birita = drunkManager.getBirita(p);

                if (birita <= 0) continue;

                long lastDrink = drunkManager.getLastDrink(p);

                if (System.currentTimeMillis() - lastDrink < 10000)
                    continue;

                drunkManager.removeBirita(p, 10);

                bossBarManager.updateBar(p, drunkManager.getBirita(p));
            }

        }, 200, 200);
    }

    private void startEffectSystem() {

        Bukkit.getScheduler().runTaskTimer(this, () -> {

            for (Player p : Bukkit.getOnlinePlayers()) {

                int birita = drunkManager.getBirita(p);

                effectManager.applyEffects(p, birita);
            }

        }, 40, 40);
    }
}
