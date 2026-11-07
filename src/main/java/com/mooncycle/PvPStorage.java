package com.mooncycle;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public class PvPStorage {

    private final MoonCyclePlugin plugin;
    private final ConfigManager config;
    private final File file;

    private final ConcurrentHashMap<UUID, Boolean> states = new ConcurrentHashMap<>();
    private final AtomicBoolean saveScheduled = new AtomicBoolean(false);
    private volatile boolean dirty = false;

    public PvPStorage(MoonCyclePlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    public void load() {
        states.clear();
        dirty = false;

        if (!file.exists()) return;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        var section = yaml.getConfigurationSection("player-pvp");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            try {
                states.put(UUID.fromString(key), section.getBoolean(key));
            } catch (IllegalArgumentException ignored) {
                // Malformed UUID entry in data.yml
            }
        }
    }

    /**
     * Synchronized disk flush to prevent interleaved writes.
     */
    public synchronized void save() {
        if (!config.isPersistPvp() || !dirty) return;

        YamlConfiguration yaml = new YamlConfiguration();
        states.forEach((uuid, state) -> yaml.set("player-pvp." + uuid, state));

        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();

        try {
            yaml.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save data.yml", e);
        }
    }

    private void scheduleSave() {
        if (!dirty) return;
        // Debounce: batch disk saves by 2 seconds (40 ticks) so rapid toggles don't thrash disk I/O
        if (saveScheduled.compareAndSet(false, true)) {
            plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                saveScheduled.set(false);
                save();
            }, 40L);
        }
    }

    public boolean getPlayerPvP(UUID playerId, boolean defaultValue) {
        return states.getOrDefault(playerId, defaultValue);
    }

    public void setPlayerPvP(UUID playerId, boolean enabled) {
        states.put(playerId, enabled);
        if (config.isPersistPvp()) {
            dirty = true;
            scheduleSave();
        }
    }
}
