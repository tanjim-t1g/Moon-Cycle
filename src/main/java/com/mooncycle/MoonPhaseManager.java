package com.mooncycle;

import io.papermc.paper.world.MoonPhase;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MoonPhaseManager {

    public enum CombatResult {
        ALLOWED,
        ATTACKER_DISABLED,
        VICTIM_DISABLED
    }

    public static class WorldState {
        private MoonPhase phase;
        private Difficulty lastDifficulty;
        private boolean forcedPvP;
        private boolean pvpApplied;
        private boolean initialized;

        public WorldState(MoonPhase phase, Difficulty initialDifficulty, boolean forcedPvP) {
            this.phase = phase;
            this.lastDifficulty = initialDifficulty;
            this.forcedPvP = forcedPvP;
        }

        public MoonPhase phase()               { return phase; }
        public void setPhase(MoonPhase phase)  { this.phase = phase; }
        public boolean forcedPvP()             { return forcedPvP; }
        public void setForcedPvP(boolean pvp)  { this.forcedPvP = pvp; }
        public Difficulty lastDifficulty()     { return lastDifficulty; }
        public void setLastDifficulty(Difficulty d) { this.lastDifficulty = d; }
        public boolean isPvpApplied()          { return pvpApplied; }
        public void setPvpApplied(boolean a)   { this.pvpApplied = a; }
        public boolean isInitialized()         { return initialized; }
        public void setInitialized(boolean i)  { this.initialized = i; }
    }

    private final MoonCyclePlugin plugin;
    private final ConfigManager config;
    private final PvPStorage storage;
    private final Map<UUID, WorldState> overworldStates = new HashMap<>();

    public MoonPhaseManager(MoonCyclePlugin plugin, ConfigManager config, PvPStorage storage) {
        this.plugin = plugin;
        this.config = config;
        this.storage = storage;
    }

    /**
     * Checks all enabled Overworld worlds and updates difficulty/PvP.
     * Linked Nether and End dimensions are synchronized from their respective Overworld.
     */
    public void checkAllWorlds() {
        for (String name : config.getEnabledWorlds()) {
            World overworld = Bukkit.getWorld(name);
            if (overworld == null) continue;

            // Moon phases only exist in the Overworld (Environment.NORMAL)
            if (overworld.getEnvironment() == World.Environment.NORMAL) {
                checkOverworld(overworld);
            }
        }
    }

    public void checkOverworld(World world) {
        WorldState state = getOrCreateState(world);
        MoonPhase livePhase = world.getMoonPhase();

        if (state.phase() != livePhase) {
            state.setPhase(livePhase);
            ConfigManager.PhaseRule rule = config.getRule(livePhase);
            state.setForcedPvP(rule.forcedPvp());

            if (config.announcements().enabled() && config.announcements().chatBroadcast()) {
                broadcastPhaseChange(world, rule);
            }
        }

        ConfigManager.PhaseRule rule = config.getRule(state.phase());

        // Bukkit's world-level PvP flag gates the EntityDamageByEntity event entirely.
        // We keep it ON so CombatListener can intercept and apply per-player rules.
        if (!state.isPvpApplied() || !world.getPVP()) {
            world.setPVP(true);
            state.setPvpApplied(true);
        }

        if (!state.isInitialized() || state.lastDifficulty() != rule.difficulty()) {
            world.setDifficulty(rule.difficulty());
            state.setLastDifficulty(rule.difficulty());
            state.setInitialized(true);
        }

        // Synchronize linked Nether and End dimensions
        syncLinkedDimensions(world, rule);
    }

    private void syncLinkedDimensions(World overworld, ConfigManager.PhaseRule rule) {
        // Nether
        World nether = Bukkit.getWorld(overworld.getName() + "_nether");
        if (nether != null && nether.getEnvironment() == World.Environment.NETHER) {
            if (!nether.getPVP()) nether.setPVP(true);
            Difficulty target = config.nether().affectDifficulty()
                    ? rule.difficulty()
                    : config.nether().defaultDifficulty();
            if (nether.getDifficulty() != target) {
                nether.setDifficulty(target);
            }
        }

        // The End
        World end = Bukkit.getWorld(overworld.getName() + "_the_end");
        if (end != null && end.getEnvironment() == World.Environment.THE_END) {
            if (!end.getPVP()) end.setPVP(true);
            Difficulty target;
            if (config.end().followOverworldRules() || config.end().affectDifficulty()) {
                target = rule.difficulty();
            } else {
                target = config.end().defaultDifficulty();
            }
            if (end.getDifficulty() != target) {
                end.setDifficulty(target);
            }
        }
    }

    public CombatResult canFight(Player attacker, Player victim) {
        World world = victim.getWorld();
        World.Environment env = world.getEnvironment();

        if (env == World.Environment.NETHER && config.nether().forcePvpAlwaysOn()) {
            return CombatResult.ALLOWED;
        }
        if (env == World.Environment.THE_END && config.end().forcePvpAlwaysOn()) {
            return CombatResult.ALLOWED;
        }

        // During forced PvP phases (Full Moon, Large Moon), everyone fights regardless of toggle
        WorldState state = getState(world);
        if (state != null && state.forcedPvP() && env == World.Environment.NORMAL) {
            return CombatResult.ALLOWED;
        }

        if (!isPlayerPvPEnabled(attacker)) return CombatResult.ATTACKER_DISABLED;
        if (!isPlayerPvPEnabled(victim))   return CombatResult.VICTIM_DISABLED;

        return CombatResult.ALLOWED;
    }

    public boolean isPlayerPvPEnabled(Player player) {
        return storage.getPlayerPvP(player.getUniqueId(), config.getDefaultPlayerPvp());
    }

    public void setPlayerPvP(Player player, boolean enabled) {
        storage.setPlayerPvP(player.getUniqueId(), enabled);
    }

    private void broadcastPhaseChange(World world, ConfigManager.PhaseRule rule) {
        String pvpStatus = rule.forcedPvp() ? "&cForced ON" : "&ePlayer Toggle";
        String msg = config.announcements().message()
                .replace("<phase>", rule.displayName())
                .replace("<difficulty>", Text.formatDifficulty(rule.difficulty()))
                .replace("<pvp>", pvpStatus);

        Component parsed = Text.render(msg);
        Sound sound = config.announcements().sound();
        float vol = config.announcements().volume();
        float pitch = config.announcements().pitch();

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(parsed);
            if (sound != null) {
                p.playSound(p.getLocation(), sound, vol, pitch);
            }
        }
    }

    public boolean isForcedPvP(World world) {
        WorldState state = getState(world);
        return state != null && state.forcedPvP();
    }

    public WorldState getState(World world) {
        return overworldStates.get(world.getUID());
    }

    public WorldState getOrCreateState(World world) {
        return overworldStates.computeIfAbsent(world.getUID(), k -> {
            MoonPhase phase = world.getEnvironment() == World.Environment.NORMAL
                    ? world.getMoonPhase()
                    : MoonPhase.FULL_MOON;
            ConfigManager.PhaseRule rule = config.getRule(phase);
            return new WorldState(phase, world.getDifficulty(), rule.forcedPvp());
        });
    }

    public void clearStates() {
        overworldStates.clear();
    }
}
