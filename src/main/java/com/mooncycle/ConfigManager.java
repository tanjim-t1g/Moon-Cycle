package com.mooncycle;

import io.papermc.paper.world.MoonPhase;
import org.bukkit.Difficulty;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ConfigManager {

    public record PhaseRule(String displayName, Difficulty difficulty, boolean forcedPvp) {
        public static final PhaseRule DEFAULT = new PhaseRule("<gray>Unknown Phase</gray>", Difficulty.NORMAL, false);
    }

    public record NetherSettings(boolean affectDifficulty, boolean forcePvpAlwaysOn, Difficulty defaultDifficulty, boolean allowPvpToggle) {}

    public record EndSettings(boolean followOverworldRules, boolean affectDifficulty, Difficulty defaultDifficulty, boolean forcePvpAlwaysOn, boolean defaultPvp, boolean allowPvpToggle) {}

    public record Announcements(boolean enabled, boolean chatBroadcast, String message, Sound sound, float volume, float pitch) {}

    public record JoinSettings(boolean enabled, long delayTicks, String message) {}

    public record Messages(
            String pvpEnabled,
            String pvpDisabled,
            String pvpStatusOn,
            String pvpStatusOff,
            String attackerDisabled,
            String victimDisabled,
            String lockedForcedPhase,
            String lockedNether,
            String lockedEnd
    ) {}

    private final MoonCyclePlugin plugin;
    private final Map<MoonPhase, PhaseRule> rules = new EnumMap<>(MoonPhase.class);
    private final Set<String> enabledWorlds = new HashSet<>();

    private int checkIntervalTicks = 20;
    private boolean persistPvp = true;
    private boolean defaultPlayerPvp = false;

    private NetherSettings nether;
    private EndSettings end;
    private Announcements announcements;
    private JoinSettings firstJoin;
    private JoinSettings hardModeWarning;
    private Messages messages;

    public ConfigManager(MoonCyclePlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();

        checkIntervalTicks = Math.max(1, cfg.getInt("check-interval-ticks", 20));
        persistPvp = cfg.getBoolean("persist-normal-phase-pvp", true);
        defaultPlayerPvp = cfg.getBoolean("player-pvp-default", false);

        enabledWorlds.clear();
        enabledWorlds.addAll(cfg.getStringList("enabled-worlds"));

        loadDimensions(cfg);
        loadAnnouncements(cfg);
        loadJoinMessages(cfg);
        loadPvpMessages(cfg);
        loadPhaseRules(cfg);
    }

    private void loadDimensions(FileConfiguration cfg) {
        var n = cfg.getConfigurationSection("nether");
        nether = n != null
                ? new NetherSettings(
                        n.getBoolean("affect-difficulty", false),
                        n.getBoolean("force-pvp-always-on", true),
                        parseDifficulty(n.getString("default-difficulty", "HARD")),
                        n.getBoolean("allow-pvp-toggle", false))
                : new NetherSettings(false, true, Difficulty.HARD, false);

        var e = cfg.getConfigurationSection("end");
        end = e != null
                ? new EndSettings(
                        e.getBoolean("follow-overworld-rules", false),
                        e.getBoolean("affect-difficulty", false),
                        parseDifficulty(e.getString("default-difficulty", "HARD")),
                        e.getBoolean("force-pvp-always-on", false),
                        e.getBoolean("default-pvp", false),
                        e.getBoolean("allow-pvp-toggle", false))
                : new EndSettings(false, false, Difficulty.HARD, false, false, false);
    }

    private void loadAnnouncements(FileConfiguration cfg) {
        var ann = cfg.getConfigurationSection("announcements");
        if (ann != null) {
            announcements = new Announcements(
                    ann.getBoolean("enabled", true),
                    ann.getBoolean("chat-broadcast", true),
                    ann.getString("phase-change-message", "&8[&bMoonCycle&8] &fPhase: <phase> &8| &fDifficulty: &e<difficulty> &8| &fPvP: <pvp>"),
                    parseSound(ann.getString("sound", "")),
                    (float) ann.getDouble("sound-volume", 1.0),
                    (float) ann.getDouble("sound-pitch", 1.0)
            );
        } else {
            announcements = new Announcements(true, true, "&8[&bMoonCycle&8] &fPhase: <phase> &8| &fPvP: <pvp>", null, 1.0f, 1.0f);
        }
    }

    private void loadJoinMessages(FileConfiguration cfg) {
        var fj = cfg.getConfigurationSection("first-join");
        firstJoin = fj != null
                ? new JoinSettings(
                        fj.getBoolean("enabled", true),
                        fj.getLong("delay-ticks", 40L),
                        fj.getString("message", "&8[&bMoonCycle&8] &fWelcome &b<player>&f! Phase: <phase> &8| &fDifficulty: &e<difficulty> &8| &fPvP: <pvp>"))
                : new JoinSettings(true, 40L, "&8[&bMoonCycle&8] &fWelcome &b<player>&f! Phase: <phase> &8| &fDifficulty: &e<difficulty> &8| &fPvP: <pvp>");

        var hm = cfg.getConfigurationSection("hard-mode-join-warning");
        hardModeWarning = hm != null
                ? new JoinSettings(
                        hm.getBoolean("enabled", true),
                        hm.getLong("delay-ticks", 30L),
                        hm.getString("message", "&8[&bMoonCycle&8] &c\u26a0 Warning: &fActive phase is <phase> &8| &cDifficulty: Hard &8| &cPvP: Forced ON"))
                : new JoinSettings(true, 30L, "&8[&bMoonCycle&8] &c\u26a0 Warning: &fActive phase is <phase> &8| &cDifficulty: Hard &8| &cPvP: Forced ON");
    }

    private void loadPvpMessages(FileConfiguration cfg) {
        var p = cfg.getConfigurationSection("pvp-messages");
        if (p != null) {
            messages = new Messages(
                    p.getString("enabled", "&8[&bMoonCycle&8] &aYour personal PvP has been turned ON."),
                    p.getString("disabled", "&8[&bMoonCycle&8] &eYour personal PvP has been turned OFF."),
                    p.getString("status-on", "&aON"),
                    p.getString("status-off", "&cOFF"),
                    p.getString("attacker-disabled", "&cYou have PvP disabled! Use /pvp on to fight."),
                    p.getString("victim-disabled", "&c<victim> has PvP disabled!"),
                    p.getString("locked-forced-phase", "&cYou cannot disable PvP during the <phase> phase."),
                    p.getString("locked-nether", "&cPvP is permanently enabled in the Nether."),
                    p.getString("locked-end", "&cPvP is permanently enabled in The End.")
            );
        } else {
            messages = new Messages(
                    "&8[&bMoonCycle&8] &aYour personal PvP has been turned ON.",
                    "&8[&bMoonCycle&8] &eYour personal PvP has been turned OFF.",
                    "&aON",
                    "&cOFF",
                    "&cYou have PvP disabled! Use /pvp on to fight.",
                    "&c<victim> has PvP disabled!",
                    "&cYou cannot disable PvP during the <phase> phase.",
                    "&cPvP is permanently enabled in the Nether.",
                    "&cPvP is permanently enabled in The End."
            );
        }
    }

    private void loadPhaseRules(FileConfiguration cfg) {
        rules.clear();
        ConfigurationSection phases = cfg.getConfigurationSection("phases");
        if (phases != null) {
            for (String key : phases.getKeys(false)) {
                String normalized = key.toUpperCase().replace("-", "_");
                List<MoonPhase> targets = switch (normalized) {
                    case "THIRD_QUARTER"  -> List.of(MoonPhase.LAST_QUARTER);
                    case "NO_MOON"        -> List.of(MoonPhase.NEW_MOON);
                    case "LARGE_MOON"     -> List.of(MoonPhase.WAXING_GIBBOUS, MoonPhase.WANING_GIBBOUS);
                    case "HALF_MOON"      -> List.of(MoonPhase.FIRST_QUARTER, MoonPhase.LAST_QUARTER);
                    case "SMALL_MOON"     -> List.of(MoonPhase.WAXING_CRESCENT, MoonPhase.WANING_CRESCENT);
                    default -> {
                        try {
                            yield List.of(MoonPhase.valueOf(normalized));
                        } catch (IllegalArgumentException e) {
                            yield List.of();
                        }
                    }
                };

                ConfigurationSection ruleSec = phases.getConfigurationSection(key);
                if (ruleSec == null) continue;

                for (MoonPhase phase : targets) {
                    rules.put(phase, new PhaseRule(
                            ruleSec.getString("display-name", defaultDisplayName(phase)),
                            parseDifficulty(ruleSec.getString("difficulty", "NORMAL")),
                            ruleSec.getBoolean("forced-pvp", false)
                    ));
                }
            }
        }

        // Fill unconfigured phases with vanilla-friendly defaults
        for (MoonPhase p : MoonPhase.values()) {
            boolean hard = p == MoonPhase.FULL_MOON || p == MoonPhase.WAXING_GIBBOUS || p == MoonPhase.WANING_GIBBOUS;
            rules.putIfAbsent(p, new PhaseRule(defaultDisplayName(p), hard ? Difficulty.HARD : Difficulty.NORMAL, hard));
        }
    }

    public static String defaultDisplayName(MoonPhase phase) {
        return switch (phase) {
            case FULL_MOON                            -> "&cFull Moon";
            case WAXING_GIBBOUS, WANING_GIBBOUS       -> "&cLarge Moon";
            case FIRST_QUARTER, LAST_QUARTER          -> "&eHalf Moon";
            case WAXING_CRESCENT, WANING_CRESCENT     -> "&eSmall Moon";
            case NEW_MOON                             -> "&eNo Moon";
        };
    }

    private Difficulty parseDifficulty(String str) {
        if (str == null || str.isBlank()) return Difficulty.NORMAL;
        try {
            return Difficulty.valueOf(str.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Unknown difficulty '" + str + "' — defaulting to NORMAL.");
            return Difficulty.NORMAL;
        }
    }

    private Sound parseSound(String str) {
        if (str == null || str.isBlank()) return null;
        NamespacedKey key = NamespacedKey.minecraft(str.toLowerCase().replace(".", "_"));
        Sound sound = Registry.SOUNDS.get(key);
        if (sound == null) {
            plugin.getLogger().warning("Unknown sound key '" + str + "' in config — sound disabled.");
        }
        return sound;
    }

    public PhaseRule getRule(MoonPhase phase) {
        return rules.getOrDefault(phase, PhaseRule.DEFAULT);
    }

    public int getCheckIntervalTicks()       { return checkIntervalTicks; }
    public boolean isPersistPvp()            { return persistPvp; }
    public boolean getDefaultPlayerPvp()     { return defaultPlayerPvp; }
    public Set<String> getEnabledWorlds()    { return enabledWorlds; }

    public NetherSettings nether()           { return nether; }
    public EndSettings end()                 { return end; }
    public Announcements announcements()     { return announcements; }
    public JoinSettings firstJoin()          { return firstJoin; }
    public JoinSettings hardModeWarning()    { return hardModeWarning; }
    public Messages messages()               { return messages; }
}
