package com.mooncycle.listener;

import com.mooncycle.ConfigManager;
import com.mooncycle.MoonPhaseManager;
import com.mooncycle.Text;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CombatListener implements Listener {

    private final MoonPhaseManager manager;
    private final ConfigManager config;

    // Cooldown to avoid spamming the action bar when a player clicks repeatedly
    private final Map<UUID, Long> warnCooldown = new HashMap<>();
    private static final long WARN_COOLDOWN_MS = 1200;

    public CombatListener(MoonPhaseManager manager, ConfigManager config) {
        this.manager = manager;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null || attacker.equals(victim)) return;

        MoonPhaseManager.CombatResult result = manager.canFight(attacker, victim);
        if (result == MoonPhaseManager.CombatResult.ALLOWED) return;

        event.setCancelled(true);
        sendCombatWarning(attacker, victim, result);
    }

    /**
     * Resolves the Player responsible for a damage event regardless of whether
     * they dealt it directly or through an indirect source (arrows, splash potions,
     * lingering clouds, primed TNT, or tamed wolves).
     */
    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player p) {
            return p;
        }
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player shooter) {
            return shooter;
        }
        if (damager instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Player caster) {
            return caster;
        }
        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player igniter) {
            return igniter;
        }
        if (damager instanceof Tameable pet && pet.getOwner() instanceof Player owner) {
            return owner;
        }
        return null;
    }

    private void sendCombatWarning(Player attacker, Player victim, MoonPhaseManager.CombatResult result) {
        long now = System.currentTimeMillis();
        if (now - warnCooldown.getOrDefault(attacker.getUniqueId(), 0L) < WARN_COOLDOWN_MS) return;
        warnCooldown.put(attacker.getUniqueId(), now);

        if (result == MoonPhaseManager.CombatResult.ATTACKER_DISABLED) {
            attacker.sendActionBar(Text.render(config.messages().attackerDisabled()));
        } else if (result == MoonPhaseManager.CombatResult.VICTIM_DISABLED) {
            String msg = config.messages().victimDisabled().replace("<victim>", victim.getName());
            attacker.sendActionBar(Text.render(msg));
        }
    }
}
