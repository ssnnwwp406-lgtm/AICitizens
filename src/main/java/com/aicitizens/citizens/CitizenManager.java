
package com.aicitizens.citizens;

import com.aicitizens.AICitizens;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;

import java.util.Collection;
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

        if (citizens.size() >= plugin.getConfig()
                .getInt("citizens.max-count", 50)) {
            return null;
        }

        if (location == null || location.getWorld() == null) {
            return null;
        }

        Villager villager = (Villager) location.getWorld()
                .spawnEntity(location, EntityType.VILLAGER);

        villager.setCustomName(name);
        villager.setCustomNameVisible(true);

        villager.setAI(false);
        villager.setInvulnerable(true);
        villager.setCollidable(false);
        villager.setPersistent(true);

        UUID id = UUID.randomUUID();

        Citizen citizen = new Citizen(
                id,
                name,
                villager,
                location
        );

        citizens.put(id, citizen);

        plugin.getLogger().info(
                "Created citizen: " + name
        );

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
        return citizens.values();
    }

    public int getCitizenCount() {
        return citizens.size();
    }

    public void shutdown() {

        for (Citizen citizen : citizens.values()) {

            Entity entity = citizen.getEntity();

            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        }

        citizens.clear();
    }
}
