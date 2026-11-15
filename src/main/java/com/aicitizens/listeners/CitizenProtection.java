
package com.aicitizens.listeners;

import com.aicitizens.AICitizens;
import com.aicitizens.citizens.CitizenManager;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityKnockbackEvent;

import java.util.UUID;

public class CitizenProtection implements Listener {

    private final AICitizens plugin;
    private final CitizenManager manager;

    public CitizenProtection(
            AICitizens plugin,
            CitizenManager manager
    ) {
        this.plugin = plugin;
        this.manager = manager;
    }

    private boolean isCitizen(Entity entity) {

        if (entity == null) {
            return false;
        }

        UUID id = entity.getUniqueId();

        return manager.getCitizen(id) != null;
    }

    // ==========================================
    //              DAMAGE PROTECTION
    // ==========================================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {

        if (plugin.getConfig().getBoolean(
                "protection.damage", true
        ) && isCitizen(event.getEntity())) {

            event.setCancelled(true);
        }
    }

    // ==========================================
    //              INTERACTION PROTECTION
    // ==========================================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEntityEvent event) {

        if (plugin.getConfig().getBoolean(
                "protection.interaction", true
        ) && isCitizen(event.getRightClicked())) {

            event.setCancelled(true);
        }
    }

    // ==========================================
    //              FIRE PROTECTION
    // ==========================================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCombust(EntityCombustEvent event) {

        if (plugin.getConfig().getBoolean(
                "protection.fire", true
        ) && isCitizen(event.getEntity())) {

            event.setCancelled(true);
        }
    }

    // ==========================================
    //              TARGET PROTECTION
    // ==========================================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTarget(EntityTargetEvent event) {

        if (!plugin.getConfig().getBoolean(
                "protection.targeting", true
        )) {
            return;
        }

        if (isCitizen(event.getEntity())) {
            event.setCancelled(true);
            return;
        }

        if (event.getTarget() != null
                && isCitizen(event.getTarget())) {

            event.setCancelled(true);
        }
    }

    // ==========================================
    //              ITEM PICKUP PROTECTION
    // ==========================================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPickup(EntityPickupItemEvent event) {

        if (plugin.getConfig().getBoolean(
                "protection.item-pickup", true
        ) && isCitizen(event.getEntity())) {

            event.setCancelled(true);
        }
    }

    // ==========================================
    //              KNOCKBACK PROTECTION
    // ==========================================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onKnockback(EntityKnockbackEvent event) {

        if (plugin.getConfig().getBoolean(
                "protection.knockback", true
        ) && isCitizen(event.getEntity())) {

            event.setCancelled(true);
        }
    }

    // ==========================================
    //              BLOCK PUSH PROTECTION
    // ==========================================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChangeBlock(EntityChangeBlockEvent event) {

        if (plugin.getConfig().getBoolean(
                "protection.pushing", true
        ) && isCitizen(event.getEntity())) {

            event.setCancelled(true);
        }
    }
}
