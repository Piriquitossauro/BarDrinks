package me.bardrinks;

import org.bukkit.plugin.java.JavaPlugin;

public class BarDrinks extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("BarDrinks ligado!");
    }

    @Override
    public void onDisable() {
        getLogger().info("BarDrinks desligado!");
    }

}
