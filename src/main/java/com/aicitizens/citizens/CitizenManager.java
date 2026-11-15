
package com.aicitizens.citizens;

import com.aicitizens.AICitizens;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CitizenManager {

    private final AICitizens plugin;

    private final Map<UUID, Citizen> citizens = new HashMap<>();

    public CitizenManager(AICitizens plugin) {
        this.plugin = plugin;
    }

    public Citizen createCitizen(String name, Location location) {

        if (!plugin.getConfig().getBoolean("citizens.spawn.enabled", true)) {
            return null;
        }

        int maxCount = plugin.getConfig().getInt("citizens.max-count", 50);

        if (citizens.size() >= maxCount) {
            return null;
        }

        if (name == null || name.isBlank()) {
            return null;
        }

        if (location == null || location.getWorld() == null) {
            return null;
        }

        String entityType = plugin.getConfig()
                .getString("citizens.appearance.type", "VILLAGER");

        if (!"VILLAGER".equalsIgnoreCase(entityType)) {
            plugin.getLogger().warning(
                    "Unsupported citizen type: " + entityType
            );
            return null;
        }

        Villager villager = (Villager) location.getWorld()
                .spawnEntity(location, EntityType.VILLAGER);

        villager.setCustomName(name);
        villager.setCustomNameVisible(false);
        villager.setInvisible(true);
        villager.setSilent(false);

        villager.setInvulnerable(
                plugin.getConfig().getBoolean(
                        "citizens.appearance.invulnerable", true
                )
        );

        villager.setCollidable(
                plugin.getConfig().getBoolean(
                        "citizens.appearance.collidable", false
                )
        );

        villager.setPersistent(
                plugin.getConfig().getBoolean(
                        "citizens.appearance.persistent", true
                )
        );

        villager.setAI(false);

        UUID id = villager.getUniqueId();

        Citizen citizen = new Citizen(
                id,
                name,
                villager,
                location
        );

        citizens.put(id, citizen);

        return citizen;
    }

    public boolean removeCitizen(UUID id) {

        Citizen citizen = citizens.remove(id);

        if (citizen == null) {
            return false;
        }

        Entity entity = citizen.getEntity();

        if (entity != null && entity.isValid()) {
            entity.remove();
        }

        return true;
    }

    public Citizen getCitizen(UUID id) {
        return citizens.get(id);
    }

    public Collection<Citizen> getCitizens() {
        return Collections.unmodifiableCollection(citizens.values());
    }

    public int getCitizenCount() {
        return citizens.size();
    }

    public void shutdown() {

        boolean removeOnDisable = plugin.getConfig()
                .getBoolean("citizens.spawn.remove-on-disable", true);

        if (removeOnDisable) {
            for (Citizen citizen : citizens.values()) {

                Entity entity = citizen.getEntity();

                if (entity != null && entity.isValid()) {
                    entity.remove();
                }
            }
        }

        citizens.clear();
    }
}
