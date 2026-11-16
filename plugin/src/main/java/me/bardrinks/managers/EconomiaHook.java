package me.bardrinks.managers;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.UUID;

public class EconomiaHook {

    private Object economyManagerInstance;
    private Method getBalanceMethod;
    private Method hasBalanceMethod;
    private Method removeBalanceMethod;

    public EconomiaHook() {
        setupHook();
    }

    public boolean setupHook() {
        if (economyManagerInstance != null) return true;
        try {
            Plugin ecoPlugin = Bukkit.getPluginManager().getPlugin("Economia");
            if (ecoPlugin == null || !ecoPlugin.isEnabled()) {
                return false;
            }
            Method getEcoMgr = ecoPlugin.getClass().getMethod("getEconomyManager");
            economyManagerInstance = getEcoMgr.invoke(ecoPlugin);
            if (economyManagerInstance == null) return false;

            Class<?> mgrClass = economyManagerInstance.getClass();
            getBalanceMethod = mgrClass.getMethod("getBalance", UUID.class);
            hasBalanceMethod = mgrClass.getMethod("hasBalance", UUID.class, double.class);
            removeBalanceMethod = mgrClass.getMethod("removeBalance", UUID.class, double.class);
            return true;
        } catch (Throwable t) {
            economyManagerInstance = null;
            return false;
        }
    }

    public boolean isAvailable() {
        return setupHook();
    }

    public double getBalance(UUID uuid) {
        if (!setupHook()) return 0.0;
        try {
            Object res = getBalanceMethod.invoke(economyManagerInstance, uuid);
            if (res instanceof Number num) {
                return num.doubleValue();
            }
        } catch (Throwable ignored) {}
        return 0.0;
    }

    public boolean hasBalance(UUID uuid, double amount) {
        if (!setupHook()) return false;
        try {
            Object res = hasBalanceMethod.invoke(economyManagerInstance, uuid, amount);
            if (res instanceof Boolean b) {
                return b;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public boolean removeBalance(UUID uuid, double amount) {
        if (!setupHook()) return false;
        try {
            Object res = removeBalanceMethod.invoke(economyManagerInstance, uuid, amount);
            if (res instanceof Boolean b) {
                return b;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static String formatBc(double amount) {
        if (amount == Math.floor(amount)) {
            return String.format(Locale.ROOT, "%.0f BC", amount);
        }
        return String.format(Locale.ROOT, "%.2f BC", amount).replace('.', ',');
    }
}
