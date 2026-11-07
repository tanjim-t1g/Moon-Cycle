package com.mooncycle.command;

import com.mooncycle.ConfigManager;
import com.mooncycle.MoonPhaseManager;
import com.mooncycle.Text;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class PvPCommand implements CommandExecutor, TabCompleter {

    private final MoonPhaseManager manager;
    private final ConfigManager config;

    public PvPCommand(MoonPhaseManager manager, ConfigManager config) {
        this.manager = manager;
        this.config = config;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase() : "status";

        if (sub.equals("players")) return handlePlayers(sender);

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Text.render("<red>This command must be run by a player.</red>"));
            return true;
        }

        return switch (sub) {
            case "status" -> handleStatus(player);
            case "on"     -> handleOn(player);
            case "off"    -> handleOff(player);
            default -> {
                player.sendMessage(Text.render("<red>Usage: /pvp [status|on|off|players]</red>"));
                yield true;
            }
        };
    }

    private boolean handleStatus(Player player) {
        if (!player.hasPermission("mooncycle.command.pvp.status")) {
            player.sendMessage(Text.render("<red>You don't have permission.</red>"));
            return true;
        }

        World world = player.getWorld();
        World.Environment env = world.getEnvironment();
        boolean pvp = manager.isPlayerPvPEnabled(player);
        String status = pvp ? config.messages().pvpStatusOn() : config.messages().pvpStatusOff();

        player.sendMessage(Text.render("<gold>--- PvP Status ---</gold>"));
        player.sendMessage(Text.render("<yellow>Your Personal PvP:</yellow> " + status));

        if (env == World.Environment.NETHER && config.nether().forcePvpAlwaysOn()) {
            player.sendMessage(Text.render("<red>Note: PvP is permanently enabled for everyone in the Nether.</red>"));
        } else if (env == World.Environment.THE_END && config.end().forcePvpAlwaysOn()) {
            player.sendMessage(Text.render("<red>Note: PvP is permanently enabled for everyone in The End.</red>"));
        } else if (manager.isForcedPvP(world) && env == World.Environment.NORMAL) {
            MoonPhaseManager.WorldState state = manager.getState(world);
            ConfigManager.PhaseRule rule = config.getRule(state.phase());
            player.sendMessage(Text.render("<red>Note: PvP is currently forced ON due to " + rule.displayName() + "<red>!</red>"));
        } else {
            player.sendMessage(Text.render("<gray>Use <yellow>/pvp on</yellow> or <yellow>/pvp off</yellow> to change your preference.</gray>"));
        }
        return true;
    }

    private boolean handlePlayers(CommandSender sender) {
        if (!sender.hasPermission("mooncycle.command.pvp.status")) {
            sender.sendMessage(Text.render("<red>You don't have permission.</red>"));
            return true;
        }

        List<String> on = new ArrayList<>();
        List<String> off = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            (manager.isPlayerPvPEnabled(p) ? on : off).add(p.getName());
        }

        sender.sendMessage(Text.render("<gold>--- Online PvP Status ---</gold>"));
        sender.sendMessage(Text.render("<yellow>PvP ON (" + on.size() + "):</yellow> "
                + (on.isEmpty() ? "<gray>None</gray>" : "<green>" + String.join("<gray>, </gray><green>", on) + "</green>")));
        sender.sendMessage(Text.render("<yellow>PvP OFF (" + off.size() + "):</yellow> "
                + (off.isEmpty() ? "<gray>None</gray>" : "<red>" + String.join("<gray>, </gray><red>", off) + "</red>")));

        if (sender instanceof Player player) {
            World world = player.getWorld();
            if (manager.isForcedPvP(world) && world.getEnvironment() == World.Environment.NORMAL) {
                MoonPhaseManager.WorldState state = manager.getState(world);
                ConfigManager.PhaseRule rule = config.getRule(state.phase());
                sender.sendMessage(Text.render("<red>\u26a0 Hard Mode active ("
                        + rule.displayName() + "<red>) \u2014 PvP is forced ON for everyone!</red>"));
            }
        }
        return true;
    }

    private boolean handleOn(Player player) {
        if (!player.hasPermission("mooncycle.command.pvp.toggle")) {
            player.sendMessage(Text.render("<red>You don't have permission to toggle PvP.</red>"));
            return true;
        }
        manager.setPlayerPvP(player, true);
        player.sendMessage(Text.render(config.messages().pvpEnabled()));
        return true;
    }

    private boolean handleOff(Player player) {
        if (!player.hasPermission("mooncycle.command.pvp.toggle")) {
            player.sendMessage(Text.render("<red>You don't have permission to toggle PvP.</red>"));
            return true;
        }

        World world = player.getWorld();
        World.Environment env = world.getEnvironment();

        if (env == World.Environment.NETHER && config.nether().forcePvpAlwaysOn()) {
            player.sendMessage(Text.render(config.messages().lockedNether()));
            return true;
        }
        if (env == World.Environment.THE_END && config.end().forcePvpAlwaysOn()) {
            player.sendMessage(Text.render(config.messages().lockedEnd()));
            return true;
        }
        if (env == World.Environment.NORMAL && manager.isForcedPvP(world)) {
            MoonPhaseManager.WorldState state = manager.getState(world);
            ConfigManager.PhaseRule rule = config.getRule(state.phase());
            String msg = config.messages().lockedForcedPhase().replace("<phase>", rule.displayName());
            player.sendMessage(Text.render(msg));
            return true;
        }

        manager.setPlayerPvP(player, false);
        player.sendMessage(Text.render(config.messages().pvpDisabled()));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            if (sender.hasPermission("mooncycle.command.pvp.status")) subs.add("status");
            if (sender.hasPermission("mooncycle.command.pvp.toggle")) { subs.add("on"); subs.add("off"); }
            if (sender.hasPermission("mooncycle.command.pvp.status")) subs.add("players");
            return subs.stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        return List.of();
    }
}
