package com.mooncycle;

import com.mooncycle.command.MoonCommand;
import com.mooncycle.command.PvPCommand;
import com.mooncycle.listener.CombatListener;
import com.mooncycle.listener.WorldListener;
import org.bstats.bukkit.Metrics;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

public class MoonCyclePlugin extends JavaPlugin {

    private static final int BSTATS_ID = 34141;

    private ConfigManager config;
    private PvPStorage storage;
    private MoonPhaseManager phaseManager;

    @Override
    public void onEnable() {
        new Metrics(this, BSTATS_ID);

        this.config = new ConfigManager(this);
        config.load();

        this.storage = new PvPStorage(this, config);
        storage.load();

        this.phaseManager = new MoonPhaseManager(this, config, storage);

        var pm = getServer().getPluginManager();
        pm.registerEvents(new CombatListener(phaseManager, config), this);
        pm.registerEvents(new WorldListener(this, phaseManager, config), this);

        registerCommand("moon", new MoonCommand(this, phaseManager, config));
        registerCommand("pvp",  new PvPCommand(phaseManager, config));

        long interval = config.getCheckIntervalTicks();
        getServer().getScheduler().runTaskTimer(this, phaseManager::checkAllWorlds, interval, interval);
        phaseManager.checkAllWorlds();

        getLogger().info("MoonCycle v" + getPluginMeta().getVersion() + " enabled. Managing "
                + config.getEnabledWorlds().size() + " world(s).");
    }

    @Override
    public void onDisable() {
        if (storage != null) {
            storage.save();
        }
        getServer().getScheduler().cancelTasks(this);
        getLogger().info("MoonCycle disabled.");
    }

    private <T extends CommandExecutor & TabCompleter> void registerCommand(String name, T handler) {
        PluginCommand cmd = getCommand(name);
        if (cmd != null) {
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }
    }

    public ConfigManager config()       { return config; }
    public PvPStorage storage()         { return storage; }
    public MoonPhaseManager manager()   { return phaseManager; }
}
