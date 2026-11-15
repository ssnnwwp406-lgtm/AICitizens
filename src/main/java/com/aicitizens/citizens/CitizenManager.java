
package com.aicitizens.citizens;

import com.aicitizens.AICitizens;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class CitizenManager {

    private final AICitizens plugin;

    private final Map<UUID, Citizen> citizens = new HashMap<>();
    private final Map<String, Location> destinations = new HashMap<>();

    private final File locationsFile;
    private final YamlConfiguration locationsConfig;

    public CitizenManager(AICitizens plugin) {

        this.plugin = plugin;

        locationsFile = new File(
                plugin.getDataFolder(),
                "locations.yml"
        );

        locationsConfig = YamlConfiguration.loadConfiguration(
                locationsFile
        );

        loadDestinations();
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

        // Use the entity UUID for correct protection checks.
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

    public boolean addDestination(String name, Location location) {

        if (name == null || name.isBlank()
                || location == null || location.getWorld() == null) {
            return false;
        }

        destinations.put(name.toLowerCase(), location.clone());

        saveDestination(name.toLowerCase(), location);

        return true;
    }

    public Location getDestination(String name) {

        Location location = destinations.get(name.toLowerCase());

        return location == null ? null : location.clone();
    }

    public Set<String> getDestinationNames() {
        return Collections.unmodifiableSet(destinations.keySet());
    }

    public boolean removeDestination(String name) {

        String key = name.toLowerCase();

        if (destinations.remove(key) == null) {
            return false;
        }

        locationsConfig.set("locations." + key, null);
        saveLocations();

        return true;
    }

    private void saveDestination(String name, Location location) {

        String path = "locations." + name;

        locationsConfig.set(path + ".world",
                location.getWorld().getName());

        locationsConfig.set(path + ".x", location.getX());
        locationsConfig.set(path + ".y", location.getY());
        locationsConfig.set(path + ".z", location.getZ());

        locationsConfig.set(path + ".yaw", location.getYaw());
        locationsConfig.set(path + ".pitch", location.getPitch());

        saveLocations();
    }

    private void loadDestinations() {

        if (!locationsConfig.contains("locations")) {
            return;
        }

        for (String name : locationsConfig
                .getConfigurationSection("locations")
                .getKeys(false)) {

            String path = "locations." + name;

            String worldName = locationsConfig.getString(
                    path + ".world"
            );

            World world = plugin.getServer().getWorld(worldName);

            if (world == null) {
                plugin.getLogger().warning(
                        "Could not load location: " + name
                                + " (world not found)"
                );
                continue;
            }

            Location location = new Location(
                    world,
                    locationsConfig.getDouble(path + ".x"),
                    locationsConfig.getDouble(path + ".y"),
                    locationsConfig.getDouble(path + ".z"),
                    (float) locationsConfig.getDouble(path + ".yaw"),
                    (float) locationsConfig.getDouble(path + ".pitch")
            );

            destinations.put(name.toLowerCase(), location);
        }

        plugin.getLogger().info(
                "Loaded " + destinations.size() + " destinations."
        );
    }

    private void saveLocations() {

        try {
            locationsConfig.save(locationsFile);
        } catch (IOException exception) {
            plugin.getLogger().severe(
                    "Could not save locations.yml: "
                            + exception.getMessage()
            );
        }
    }

    public Collection<Citizen> getCitizens() {
        return citizens.values();
    }

    public int getCitizenCount() {
        return citizens.size();
    }

    public void shutdown() {

        saveLocations();

        for (Citizen citizen : citizens.values()) {

            Entity entity = citizen.getEntity();

            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        }

        citizens.clear();
    }
}
