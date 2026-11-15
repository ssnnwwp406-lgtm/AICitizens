
package com.aicitizens.listeners;

import com.aicitizens.citizens.CitizenManager;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;

import java.util.UUID;

public class CitizenProtection implements Listener {

    private final CitizenManager manager;

    public CitizenProtection(CitizenManager manager) {
        this.manager = manager;
    }

    private boolean isCitizen(Entity entity) {
        if (entity == null) return false;

        UUID id = entity.getUniqueId();

        return manager.getCitizen(id) != null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {

        if (isCitizen(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEntityEvent event) {

        if (isCitizen(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractAt(PlayerInteractAtEntityEvent event) {

        if (isCitizen(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCombust(EntityCombustEvent event) {

        if (isCitizen(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTarget(EntityTargetEvent event) {

        if (isCitizen(event.getEntity())) {
            event.setCancelled(true);
        }

        if (event.getTarget() != null &&
            isCitizen(event.getTarget())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPickup(EntityPickupItemEvent event) {

        if (isCitizen(event.getEntity())) {
            event.setCancelled(true);
        }
    }
}
