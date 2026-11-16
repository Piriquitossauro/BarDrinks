package me.bardrinks.managers;

import me.bardrinks.BarDrinks;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RankingManager {

    private final Map<UUID, Integer> drinks = new ConcurrentHashMap<>();
    private final Set<UUID> bannedPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> barmans = ConcurrentHashMap.newKeySet();
    private final File dataFile;
    private FileConfiguration dataConfig;

    public RankingManager(BarDrinks plugin) {
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
        loadData();
    }

    public void addDrink(Player p) {
        drinks.put(p.getUniqueId(), drinks.getOrDefault(p.getUniqueId(), 0) + 1);
        saveDataAsync();
    }

    public int getDrinks(UUID uuid) {
        return drinks.getOrDefault(uuid, 0);
    }

    public List<Map.Entry<UUID, Integer>> getTop() {
        List<Map.Entry<UUID, Integer>> list = new ArrayList<>(drinks.entrySet());
        list.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        return list;
    }

    // --- Sistema de Banimento do Bar (/bardrink ban | desban | list) ---

    public boolean isBanned(UUID uuid) {
        return bannedPlayers.contains(uuid);
    }

    public boolean banPlayer(UUID uuid) {
        boolean added = bannedPlayers.add(uuid);
        if (added) {
            saveDataAsync();
        }
        return added;
    }

    public boolean unbanPlayer(UUID uuid) {
        boolean removed = bannedPlayers.remove(uuid);
        if (removed) {
            saveDataAsync();
        }
        return removed;
    }

    public Set<UUID> getBannedPlayers() {
        return Collections.unmodifiableSet(bannedPlayers);
    }

    // --- Sistema de Barmans (/bar add <player>) ---

    public boolean isBarman(UUID uuid) {
        return barmans.contains(uuid);
    }

    public boolean addBarman(UUID uuid) {
        boolean added = barmans.add(uuid);
        if (added) {
            saveDataAsync();
        }
        return added;
    }

    public boolean removeBarman(UUID uuid) {
        boolean removed = barmans.remove(uuid);
        if (removed) {
            saveDataAsync();
        }
        return removed;
    }

    public Set<UUID> getBarmans() {
        return Collections.unmodifiableSet(barmans);
    }

    public synchronized void loadData() {
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                BarDrinks.get().getLogger().severe("Não foi possível criar data.yml!");
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        drinks.clear();
        bannedPlayers.clear();
        barmans.clear();

        ConfigurationSection rankSec = dataConfig.getConfigurationSection("ranking");
        if (rankSec != null) {
            for (String key : rankSec.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    drinks.put(uuid, rankSec.getInt(key, 0));
                } catch (IllegalArgumentException ignored) {}
            }
        }

        List<String> bannedList = dataConfig.getStringList("banned-players");
        for (String s : bannedList) {
            try {
                bannedPlayers.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {}
        }

        List<String> barmanList = dataConfig.getStringList("barmans");
        for (String s : barmanList) {
            try {
                barmans.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {}
        }

        ConfigurationSection biritaSec = dataConfig.getConfigurationSection("birita");
        if (biritaSec != null && BarDrinks.get().getDrunkManager() != null) {
            for (String key : biritaSec.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    double val = biritaSec.getDouble(key, 0.0);
                    if (val > 0.05) {
                        BarDrinks.get().getDrunkManager().setBiritaInstant(uuid, val);
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    public synchronized void saveData() {
        if (dataConfig == null) {
            dataConfig = new YamlConfiguration();
        }

        for (Map.Entry<UUID, Integer> entry : drinks.entrySet()) {
            dataConfig.set("ranking." + entry.getKey().toString(), entry.getValue());
        }

        List<String> bannedStrs = new ArrayList<>();
        for (UUID uuid : bannedPlayers) {
            bannedStrs.add(uuid.toString());
        }
        dataConfig.set("banned-players", bannedStrs);

        List<String> barmanStrs = new ArrayList<>();
        for (UUID uuid : barmans) {
            barmanStrs.add(uuid.toString());
        }
        dataConfig.set("barmans", barmanStrs);

        if (BarDrinks.get().getDrunkManager() != null) {
            dataConfig.set("birita", null);
            for (Map.Entry<UUID, DrunkManager.PlayerDrunkState> entry : BarDrinks.get().getDrunkManager().getAllStates().entrySet()) {
                double val = entry.getValue().currentBirita + entry.getValue().fillRemaining;
                if (val > 0.05) {
                    dataConfig.set("birita." + entry.getKey().toString(), Math.min(100.0, val));
                }
            }
        }

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            BarDrinks.get().getLogger().severe("Não foi possível salvar data.yml!");
        }
    }

    public void saveDataAsync() {
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(BarDrinks.get(), this::saveData);
    }
}
