package me.bardrinks.models;

import org.bukkit.boss.BarColor;

public enum DrunkStage {

    SOBRIO("§aSóbrio", 0, 20, BarColor.GREEN),
    BEBO_1("§eLevemente Bêbado", 21, 50, BarColor.YELLOW),
    BEBO_2("§6Grogue", 51, 70, BarColor.PINK),
    BEBO_3("§cBêbado", 71, 100, BarColor.RED);

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
