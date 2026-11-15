package me.bardrinks.managers;

import me.bardrinks.BarDrinks;
import me.bardrinks.models.DrunkStage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DrunkManager {

    private static final int GRADUAL_TICKS = 30;
    private static final int COOLDOWN_AFTER_FILL_TICKS = 400;
    private static final int STAGE_PAUSE_TICKS = 100;
    private static final double DECAY_PER_TICK = 10.0 / GRADUAL_TICKS;

    private static final double[] CHECKPOINTS = {70.0, 50.0, 20.0};

    public static class PlayerDrunkState {
        public double currentBirita = 0.0;

        // Subida gradativa ao beber (1,5s)
        public double fillRemaining = 0.0;
        public double fillRatePerTick = 0.0;
        public int fillTicksLeft = 0;

        // Descida rápida forçada (ex: Água -20% ou Vômito -50% em 1,5s)
        public double forcedDrainRemaining = 0.0;
        public double forcedDrainRatePerTick = 0.0;
        public int forcedDrainTicksLeft = 0;

        // Espera de 20s após terminar de encher antes de começar a descer
        public int waitBeforeDecayTicks = 0;

        // Parada de 5s ao atingir um marco (70%, 50%, 20%) na descida natural
        public int stagePauseTicks = 0;

        // Probabilidade do Apagão/Teleporte na Fase Bebo 3: começa em 1/100 e cai de 10 em 10 até 1/40 ao beber na fase 3
        public int blackoutDenominator = 100;
    }

    private final Map<UUID, PlayerDrunkState> states = new ConcurrentHashMap<>();

    public PlayerDrunkState getState(UUID uuid) {
        return states.computeIfAbsent(uuid, k -> new PlayerDrunkState());
    }

    public Map<UUID, PlayerDrunkState> getAllStates() {
        return states;
    }

    public int getBirita(Player p) {
        return (int) Math.round(getBiritaExact(p.getUniqueId()));
    }

    public double getBiritaExact(UUID uuid) {
        PlayerDrunkState state = states.get(uuid);
        return state != null ? state.currentBirita : 0.0;
    }

    public DrunkStage getStage(Player p) {
        return DrunkStage.fromPercentage(getBiritaExact(p.getUniqueId()));
    }

    public int getBlackoutDenominator(UUID uuid) {
        PlayerDrunkState state = states.get(uuid);
        return state != null ? state.blackoutDenominator : 100;
    }

    public void resetBlackoutDenominator(UUID uuid) {
        PlayerDrunkState state = getState(uuid);
        state.blackoutDenominator = 100;
    }

    /**
     * Adiciona birita ao beber uma bebida alcoólica:
     * - Carrega gradativamente ao longo de 1,5s (30 ticks).
     * - Se o player já estava na fase Bebo 3 (> 70%), aumenta a chance de apagão (1/100 -> 1/90 -> ... -> 1/40).
     */
    public void addBirita(Player p, double amount) {
        if (amount <= 0) return;
        PlayerDrunkState state = getState(p.getUniqueId());

        // Se já está no Bebo 3 e continua bebendo, reduz o denominador em 10 até o mínimo de 40 (1/40)
        if (DrunkStage.fromPercentage(state.currentBirita) == DrunkStage.BEBO_3) {
            state.blackoutDenominator = Math.max(40, state.blackoutDenominator - 10);
        }

        state.stagePauseTicks = 0;
        state.waitBeforeDecayTicks = 0;
        state.forcedDrainRemaining = 0.0;
        state.forcedDrainTicksLeft = 0;

        double maxPossibleToAdd = Math.max(0.0, 100.0 - (state.currentBirita + state.fillRemaining));
        double actualAdd = Math.min(amount, maxPossibleToAdd);

        state.fillRemaining += actualAdd;
        state.fillTicksLeft = GRADUAL_TICKS;
        state.fillRatePerTick = state.fillRemaining / GRADUAL_TICKS;

        if (state.fillRemaining <= 0.0001) {
            state.fillRemaining = 0.0;
            state.fillTicksLeft = GRADUAL_TICKS;
            state.fillRatePerTick = 0.0;
        }
    }

    public void removeBirita(Player p, double amount) {
        if (amount <= 0) return;
        PlayerDrunkState state = getState(p.getUniqueId());

        if (state.fillRemaining > 0) {
            double cancelFill = Math.min(state.fillRemaining, amount);
            state.fillRemaining -= cancelFill;
            amount -= cancelFill;
            if (state.fillRemaining <= 0.001) {
                state.fillRemaining = 0.0;
                state.fillTicksLeft = 0;
                state.fillRatePerTick = 0.0;
                state.waitBeforeDecayTicks = COOLDOWN_AFTER_FILL_TICKS;
            } else if (state.fillTicksLeft > 0) {
                state.fillRatePerTick = state.fillRemaining / state.fillTicksLeft;
            }
        }

        if (amount > 0) {
            double maxDrain = Math.max(0.0, state.currentBirita - state.forcedDrainRemaining);
            double actualDrain = Math.min(amount, maxDrain);
            if (actualDrain > 0) {
                state.forcedDrainRemaining += actualDrain;
                state.forcedDrainTicksLeft = GRADUAL_TICKS;
                state.forcedDrainRatePerTick = state.forcedDrainRemaining / GRADUAL_TICKS;
            }
        }
    }

    public void setBiritaInstant(UUID uuid, double value) {
        PlayerDrunkState state = getState(uuid);
        state.currentBirita = Math.max(0.0, Math.min(100.0, value));
        state.fillRemaining = 0.0;
        state.fillTicksLeft = 0;
        state.fillRatePerTick = 0.0;
        state.forcedDrainRemaining = 0.0;
        state.forcedDrainTicksLeft = 0;
        state.forcedDrainRatePerTick = 0.0;
        state.stagePauseTicks = 0;
        state.waitBeforeDecayTicks = state.currentBirita > 0 ? COOLDOWN_AFTER_FILL_TICKS : 0;
        if (state.currentBirita <= 0.01) {
            state.blackoutDenominator = 100;
        }

        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            BarDrinks.get().getBossBarManager().updateBar(online, state.currentBirita);
        }
    }

    public void tickPlayer(Player p) {
        PlayerDrunkState state = states.get(p.getUniqueId());
        if (state == null) return;

        boolean changed = false;

        if (state.fillTicksLeft > 0) {
            state.fillTicksLeft--;
            if (state.fillRemaining > 0) {
                double step = Math.min(state.fillRemaining, state.fillRatePerTick);
                state.currentBirita = Math.min(100.0, state.currentBirita + step);
                state.fillRemaining = Math.max(0.0, state.fillRemaining - step);
                changed = true;
            }

            if (state.fillTicksLeft <= 0) {
                if (state.fillRemaining > 0.0001) {
                    state.currentBirita = Math.min(100.0, state.currentBirita + state.fillRemaining);
                    state.fillRemaining = 0.0;
                    changed = true;
                }
                state.fillRatePerTick = 0.0;
                state.waitBeforeDecayTicks = COOLDOWN_AFTER_FILL_TICKS;
            }
        } else if (state.forcedDrainTicksLeft > 0) {
            state.forcedDrainTicksLeft--;
            if (state.forcedDrainRemaining > 0) {
                double step = Math.min(state.forcedDrainRemaining, state.forcedDrainRatePerTick);
                state.currentBirita = Math.max(0.0, state.currentBirita - step);
                state.forcedDrainRemaining = Math.max(0.0, state.forcedDrainRemaining - step);
                changed = true;
            }
            if (state.forcedDrainTicksLeft <= 0) {
                if (state.forcedDrainRemaining > 0.0001) {
                    state.currentBirita = Math.max(0.0, state.currentBirita - state.forcedDrainRemaining);
                    state.forcedDrainRemaining = 0.0;
                    changed = true;
                }
                state.forcedDrainRatePerTick = 0.0;
            }
        } else if (state.waitBeforeDecayTicks > 0) {
            if (state.currentBirita > 0.001) {
                state.waitBeforeDecayTicks--;
            } else {
                state.waitBeforeDecayTicks = 0;
            }
        } else if (state.stagePauseTicks > 0) {
            if (state.currentBirita > 0.001) {
                state.stagePauseTicks--;
            } else {
                state.stagePauseTicks = 0;
            }
        } else if (state.currentBirita > 0.0001) {
            double oldVal = state.currentBirita;
            double newVal = Math.max(0.0, oldVal - DECAY_PER_TICK);

            for (double checkpoint : CHECKPOINTS) {
                if (oldVal > checkpoint + 0.0001 && newVal <= checkpoint + 0.0001) {
                    newVal = checkpoint;
                    state.stagePauseTicks = STAGE_PAUSE_TICKS;
                    break;
                }
            }

            if (newVal < 0.01) {
                newVal = 0.0;
                state.blackoutDenominator = 100;
            }

            state.currentBirita = newVal;
            changed = true;
        }

        if (changed || state.currentBirita > 0.001) {
            BarDrinks.get().getBossBarManager().updateBar(p, state.currentBirita);
        }
    }
}
