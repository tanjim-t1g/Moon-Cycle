package com.mooncycle.command;

import com.mooncycle.ConfigManager;
import com.mooncycle.MoonCyclePlugin;
import com.mooncycle.MoonPhaseManager;
import com.mooncycle.Text;
import io.papermc.paper.world.MoonPhase;
import org.bukkit.Difficulty;
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

public class MoonCommand implements CommandExecutor, TabCompleter {

    private static final List<String> PREVIEW_PHASES = List.of(
            "full_moon", "large_moon", "half_moon", "small_moon", "no_moon"
    );

    private final MoonCyclePlugin plugin;
    private final MoonPhaseManager manager;
    private final ConfigManager config;

    public MoonCommand(MoonCyclePlugin plugin, MoonPhaseManager manager, ConfigManager config) {
        this.plugin = plugin;
        this.manager = manager;
        this.config = config;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase() : "status";
        return switch (sub) {
            case "status"  -> handleStatus(sender);
            case "preview" -> handlePreview(sender, args);
            case "reload"  -> handleReload(sender);
            default -> {
                sender.sendMessage(Text.render("<red>Usage: /moon [status|preview <phase>|reload]</red>"));
                yield true;
            }
        };
    }

    private boolean handleStatus(CommandSender sender) {
        if (!sender.hasPermission("mooncycle.command.status")) {
            sender.sendMessage(Text.render("<red>You don't have permission to use this command.</red>"));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Text.render("<red>This command must be run by a player.</red>"));
            return true;
        }

        World world = player.getWorld();
        MoonPhase phase = world.getEnvironment() == World.Environment.NORMAL
                ? world.getMoonPhase()
                : MoonPhase.FULL_MOON;
        ConfigManager.PhaseRule rule = config.getRule(phase);
        Difficulty diff = world.getDifficulty();

        String pvpStatus;
        if (world.getEnvironment() == World.Environment.NETHER && config.nether().forcePvpAlwaysOn()) {
            pvpStatus = "<green>Permanently ON (Nether)</green>";
        } else if (rule.forcedPvp() && world.getEnvironment() == World.Environment.NORMAL) {
            pvpStatus = "<red>FORCED ON (All Players)</red>";
        } else {
            boolean on = manager.isPlayerPvPEnabled(player);
            pvpStatus = on
                    ? "<green>Player Toggle</green> <gray>(Yours: <green>ON</green>)</gray>"
                    : "<green>Player Toggle</green> <gray>(Yours: <red>OFF</red>)</gray>";
        }

        sender.sendMessage(Text.render("<gold>--- MoonCycle Status ---</gold>"));
        sender.sendMessage(Text.render("<yellow>World:</yellow> <white>" + world.getName() + "</white>"));
        sender.sendMessage(Text.render("<yellow>Dimension:</yellow> <white>" + world.getEnvironment().name() + "</white>"));
        sender.sendMessage(Text.render("<yellow>Moon Phase:</yellow> " + rule.displayName()));
        sender.sendMessage(Text.render("<yellow>Difficulty:</yellow> <white>" + Text.formatDifficulty(diff) + "</white>"));
        sender.sendMessage(Text.render("<yellow>PvP Rule:</yellow> " + pvpStatus));

        String netherPvP = config.nether().forcePvpAlwaysOn()
                ? "<green>Permanently ON</green>" : "<gray>Configurable</gray>";
        sender.sendMessage(Text.render("<yellow>Nether PvP:</yellow> " + netherPvP));
        return true;
    }

    private boolean handlePreview(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mooncycle.command.preview")) {
            sender.sendMessage(Text.render("<red>You don't have permission to use this command.</red>"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Text.render("<red>Usage: /moon preview <phase></red>"));
            return true;
        }

        MoonPhase phase = parsePhase(args[1]);
        if (phase == null) {
            sender.sendMessage(Text.render("<red>Invalid phase. Try: full_moon, large_moon, half_moon, small_moon, no_moon</red>"));
            return true;
        }

        ConfigManager.PhaseRule rule = config.getRule(phase);
        String pvpState = rule.forcedPvp()
                ? "<red>FORCED ON</red> <gray>(Cannot be disabled by players)</gray>"
                : "<green>Player Toggle</green> <gray>(Players can use /pvp on|off)</gray>";

        sender.sendMessage(Text.render("<gold>--- Moon Phase Preview: " + rule.displayName() + " <gold>---</gold>"));
        sender.sendMessage(Text.render("<yellow>Difficulty:</yellow> <white>" + Text.formatDifficulty(rule.difficulty()) + "</white>"));
        sender.sendMessage(Text.render("<yellow>PvP Rule:</yellow> " + pvpState));
        sender.sendMessage(Text.render("<yellow>Applies to:</yellow> <gray>Overworld"
                + " (Nether: " + (config.nether().affectDifficulty() ? "Follows" : "Isolated")
                + ", End: " + (config.end().followOverworldRules() ? "Follows" : "Static") + ")</gray>"));
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("mooncycle.command.reload")) {
            sender.sendMessage(Text.render("<red>You don't have permission to use this command.</red>"));
            return true;
        }
        config.load();
        manager.clearStates();
        manager.checkAllWorlds();
        sender.sendMessage(Text.render("<green>MoonCycle configuration reloaded.</green>"));
        return true;
    }

    private @Nullable MoonPhase parsePhase(String input) {
        return switch (input.toUpperCase().replace("-", "_").trim()) {
            case "FULL_MOON", "FULL"           -> MoonPhase.FULL_MOON;
            case "LARGE_MOON", "LARGE",
                 "WAXING_GIBBOUS"              -> MoonPhase.WAXING_GIBBOUS;
            case "WANING_GIBBOUS"              -> MoonPhase.WANING_GIBBOUS;
            case "HALF_MOON", "HALF",
                 "FIRST_QUARTER"               -> MoonPhase.FIRST_QUARTER;
            case "LAST_QUARTER", "THIRD_QUARTER" -> MoonPhase.LAST_QUARTER;
            case "SMALL_MOON", "SMALL",
                 "WAXING_CRESCENT"             -> MoonPhase.WAXING_CRESCENT;
            case "WANING_CRESCENT"             -> MoonPhase.WANING_CRESCENT;
            case "NO_MOON", "NEW_MOON", "NONE" -> MoonPhase.NEW_MOON;
            default -> null;
        };
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            if (sender.hasPermission("mooncycle.command.status"))  subs.add("status");
            if (sender.hasPermission("mooncycle.command.preview")) subs.add("preview");
            if (sender.hasPermission("mooncycle.command.reload"))  subs.add("reload");
            return subs.stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("preview")) {
            return PREVIEW_PHASES.stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
