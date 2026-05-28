package com.acesmp.combatdeathrelease;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CombatDeathRelease extends JavaPlugin implements Listener {

    private Plugin pvpBarrier;
    private Object combatManager;
    private Method removeTagMethod;

    // Track who is fighting who: playerUUID -> opponentUUID
    private final Map<UUID, UUID> combatOpponents = new HashMap<>();

    @Override
    public void onEnable() {
        pvpBarrier = Bukkit.getPluginManager().getPlugin("PvPBarrier");

        if (pvpBarrier == null) {
            getLogger().warning("PvPBarrier not found! Plugin will not function.");
            return;
        }

        try {
            Method getInstanceMethod = pvpBarrier.getClass().getMethod("getInstance");
            Object mainInstance = getInstanceMethod.invoke(null);

            Method getCombatManagerMethod = pvpBarrier.getClass().getMethod("getCombatManager");
            combatManager = getCombatManagerMethod.invoke(mainInstance);

            Class<?> combatManagerClass = combatManager.getClass();
            removeTagMethod = combatManagerClass.getMethod("removeTag", Player.class);

            getLogger().info("Successfully hooked into PvPBarrier!");
        } catch (Exception e) {
            getLogger().severe("Failed to hook into PvPBarrier: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("CombatDeathRelease enabled!");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        // Track who is hitting who so we know opponents when someone dies
        Entity damager = event.getDamager();
        Entity victim = event.getEntity();

        if (!(damager instanceof Player) || !(victim instanceof Player)) return;

        Player attackerPlayer = (Player) damager;
        Player victimPlayer = (Player) victim;

        combatOpponents.put(attackerPlayer.getUniqueId(), victimPlayer.getUniqueId());
        combatOpponents.put(victimPlayer.getUniqueId(), attackerPlayer.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (combatManager == null) return;

        Player deadPlayer = event.getEntity();
        UUID deadUUID = deadPlayer.getUniqueId();

        try {
            // Remove combat tag from the dead player
            removeTagMethod.invoke(combatManager, deadPlayer);

            // Find the opponent and remove their tag too
            UUID opponentUUID = combatOpponents.get(deadUUID);
            if (opponentUUID != null) {
                Player opponent = Bukkit.getPlayer(opponentUUID);
                if (opponent != null && opponent.isOnline()) {
                    removeTagMethod.invoke(combatManager, opponent);
                }
                // Clean up tracking
                combatOpponents.remove(opponentUUID);
            }

            combatOpponents.remove(deadUUID);

        } catch (Exception e) {
            getLogger().warning("Error removing combat tag: " + e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        combatOpponents.clear();
        getLogger().info("CombatDeathRelease disabled!");
    }
}
