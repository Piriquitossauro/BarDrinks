package me.bardrinks.models;

import org.bukkit.boss.BarColor;

public enum DrunkStage {

    SOBRIO("§aSóbrio", 0, 20, BarColor.GREEN),
    BEBO_1("§eBebo 1", 21, 50, BarColor.YELLOW),
    BEBO_2("§6Bebo 2", 51, 70, BarColor.PINK),
    BEBO_3("§cBebo 3", 71, 100, BarColor.RED);

    private final String displayName;
    private final int minPercent;
    private final int maxPercent;
    private final BarColor barColor;

    DrunkStage(String displayName, int minPercent, int maxPercent, BarColor barColor) {
        this.displayName = displayName;
        this.minPercent = minPercent;
        this.maxPercent = maxPercent;
        this.barColor = barColor;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getMinPercent() {
        return minPercent;
    }

    public int getMaxPercent() {
        return maxPercent;
    }

    public BarColor getBarColor() {
        return barColor;
    }

    /**
     * Determina o marco atual baseado na porcentagem (0.0 a 100.0).
     * Marcos:
     * - Sóbrio: 0% a 20%
     * - Bebo 1: 21% a 50% (acima de 20% até 50%)
     * - Bebo 2: 51% a 70% (acima de 50% até 70%)
     * - Bebo 3: 71% a 100% (acima de 70% até 100%)
     */
    public static DrunkStage fromPercentage(double percentage) {
        if (percentage <= 20.001) {
            return SOBRIO;
        } else if (percentage <= 50.001) {
            return BEBO_1;
        } else if (percentage <= 70.001) {
            return BEBO_2;
        } else {
            return BEBO_3;
        }
    }
}
