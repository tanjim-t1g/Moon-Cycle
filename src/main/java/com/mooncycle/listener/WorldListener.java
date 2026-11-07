package com.mooncycle.listener;

import com.mooncycle.ConfigManager;
import com.mooncycle.MoonCyclePlugin;
import com.mooncycle.MoonPhaseManager;
import com.mooncycle.Text;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.TimeSkipEvent;

public class WorldListener implements Listener {

    private final MoonCyclePlugin plugin;
    private final MoonPhaseManager manager;
    private final ConfigManager config;

    public WorldListener(MoonCyclePlugin plugin, MoonPhaseManager manager, ConfigManager config) {
        this.plugin = plugin;
        this.manager = manager;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTimeSkip(TimeSkipEvent event) {
        if (event.getWorld().getEnvironment() == World.Environment.NORMAL) {
            plugin.getServer().getScheduler().runTask(plugin, () -> manager.checkOverworld(event.getWorld()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (!player.hasPlayedBefore() && config.firstJoin().enabled()) {
            sendFirstJoin(player);
        } else if (config.hardModeWarning().enabled()) {
            sendHardModeWarning(player);
        }
    }

    private void sendFirstJoin(Player player) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            World world = player.getWorld();
            MoonPhaseManager.WorldState state = manager.getOrCreateState(world);
            ConfigManager.PhaseRule rule = config.getRule(state.phase());

            String pvpDisplay = rule.forcedPvp() ? "&cForced ON"
                    : (manager.isPlayerPvPEnabled(player) ? "&aON" : "&ePlayer Toggle");

            String msg = config.firstJoin().message()
                    .replace("<player>", player.getName())
                    .replace("<phase>", rule.displayName())
                    .replace("<difficulty>", Text.formatDifficulty(world.getDifficulty()))
                    .replace("<pvp>", pvpDisplay);

            player.sendMessage(Text.render(msg));
        }, config.firstJoin().delayTicks());
    }

    private void sendHardModeWarning(Player player) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            World world = player.getWorld();
            MoonPhaseManager.WorldState state = manager.getOrCreateState(world);
            ConfigManager.PhaseRule rule = config.getRule(state.phase());

            if (!rule.forcedPvp()) return;

            String msg = config.hardModeWarning().message()
                    .replace("<player>", player.getName())
                    .replace("<phase>", rule.displayName())
                    .replace("<difficulty>", Text.formatDifficulty(rule.difficulty()))
                    .replace("<pvp>", "&cForced ON");

            player.sendMessage(Text.render(msg));
        }, config.hardModeWarning().delayTicks());
    }
}
