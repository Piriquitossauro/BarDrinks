package me.bardrinks.managers;

import me.bardrinks.BarDrinks;
import me.bardrinks.models.DrunkStage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DrunkManager {

    // Duração de subida/descida de cada bloco: 1,5 segundos = 30 ticks
    private static final int GRADUAL_TICKS = 30;
    // Cooldown após terminar os 1,5s de subida: 20 segundos = 400 ticks (Total = 21,5s)
    private static final int COOLDOWN_AFTER_FILL_TICKS = 400;
    // Parada em cada marco (70%, 50%, 20%): 5 segundos = 100 ticks
    private static final int STAGE_PAUSE_TICKS = 100;
    // Taxa de descida: 10% a cada 1,5 segundo (30 ticks) -> 10.0 / 30.0 por tick
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
    }

    private final Map<UUID, PlayerDrunkState> states = new ConcurrentHashMap<>();

    public PlayerDrunkState getState(UUID uuid) {
        return states.computeIfAbsent(uuid, k -> new PlayerDrunkState());
    }

    public Map<UUID, PlayerDrunkState> getAllStates() {
        return states;
    }

    /**
     * Retorna a porcentagem inteira arredondada (0 a 100).
     */
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

    /**
     * Adiciona birita ao beber uma bebida alcoólica:
     * - Carrega gradativamente ao longo de 1,5s (30 ticks).
     * - Reseta qualquer descida/parada/cooldown e agenda 20s de espera após os 1,5s terminarem.
     */
    public void addBirita(Player p, double amount) {
        if (amount <= 0) return;
        PlayerDrunkState state = getState(p.getUniqueId());

        // Cancela descidas ou pausas em andamento
        state.stagePauseTicks = 0;
        state.waitBeforeDecayTicks = 0;
        state.forcedDrainRemaining = 0.0;
        state.forcedDrainTicksLeft = 0;

        double maxPossibleToAdd = Math.max(0.0, 100.0 - (state.currentBirita + state.fillRemaining));
        double actualAdd = Math.min(amount, maxPossibleToAdd);

        state.fillRemaining += actualAdd;
        state.fillTicksLeft = GRADUAL_TICKS; // 1,5 segundos
        state.fillRatePerTick = state.fillRemaining / GRADUAL_TICKS;

        // Se já estava em 100%, apenas reinicia o tempo de espera após os 1,5s
        if (state.fillRemaining <= 0.0001) {
            state.fillRemaining = 0.0;
            state.fillTicksLeft = GRADUAL_TICKS;
            state.fillRatePerTick = 0.0;
        }
    }

    /**
     * Remove birita por ação externa (ex: beber água -20% ou vomitar -50%).
     * Desce suavemente ao longo de 1,5s sem acionar a pausa de 5s dos marcos naturais.
     */
    public void removeBirita(Player p, double amount) {
        if (amount <= 0) return;
        PlayerDrunkState state = getState(p.getUniqueId());

        // Se estava subindo, abate primeiro da subida pendente
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

    /**
     * Define o valor exato da birita (para comando administrativo ou carregamento de dados).
     */
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

        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            BarDrinks.get().getBossBarManager().updateBar(online, state.currentBirita);
        }
    }

    /**
     * Processado a cada 1 tick (20x por segundo) para garantir animação fluida na BossBar.
     */
    public void tickPlayer(Player p) {
        PlayerDrunkState state = states.get(p.getUniqueId());
        if (state == null) return;

        boolean changed = false;

        // 1. Subida gradativa de bebida (1,5s = 30 ticks)
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
                // Inicia os 20 segundos de espera após os 1,5 segundos de enchimento
                state.waitBeforeDecayTicks = COOLDOWN_AFTER_FILL_TICKS;
            }
        }
        // 2. Descida forçada (Água -20% ou Vômito -50% em 1,5s)
        else if (state.forcedDrainTicksLeft > 0) {
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
        }
        // 3. Cooldown de 20 segundos antes de poder descer naturalmente
        else if (state.waitBeforeDecayTicks > 0) {
            if (state.currentBirita > 0.001) {
                state.waitBeforeDecayTicks--;
            } else {
                state.waitBeforeDecayTicks = 0;
            }
        }
        // 4. Parada de 5 segundos em cada marco (70%, 50%, 20%)
        else if (state.stagePauseTicks > 0) {
            if (state.currentBirita > 0.001) {
                state.stagePauseTicks--;
            } else {
                state.stagePauseTicks = 0;
            }
        }
        // 5. Descida natural: remove 10% a cada 1,5 segundo (gradativamente)
        else if (state.currentBirita > 0.0001) {
            double oldVal = state.currentBirita;
            double newVal = Math.max(0.0, oldVal - DECAY_PER_TICK);

            // Verifica se cruzou algum marco de mudança de fase (70%, 50%, 20%)
            for (double checkpoint : CHECKPOINTS) {
                if (oldVal > checkpoint + 0.0001 && newVal <= checkpoint + 0.0001) {
                    newVal = checkpoint;
                    state.stagePauseTicks = STAGE_PAUSE_TICKS; // Para por 5 segundos no marco
                    break;
                }
            }

            if (newVal < 0.01) {
                newVal = 0.0;
            }

            state.currentBirita = newVal;
            changed = true;
        }

        if (changed || state.currentBirita > 0.001) {
            BarDrinks.get().getBossBarManager().updateBar(p, state.currentBirita);
        }
    }
}
