
package com.aicitizens.citizens;

import com.aicitizens.AICitizens;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
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

        locationsFile = new File(plugin.getDataFolder(), "locations.yml");
        locationsConfig = YamlConfiguration.loadConfiguration(locationsFile);

        loadDestinations();
    }

    // ==========================================
    //              CITIZEN CREATION
    // ==========================================

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

        String entityType = plugin.getConfig().getString(
                "citizens.appearance.type", "VILLAGER"
        );

        if (!"VILLAGER".equalsIgnoreCase(entityType)) {
            plugin.getLogger().warning(
                    "Unsupported citizen type: " + entityType
            );
            return null;
        }

        Villager villager = (Villager) location.getWorld().spawnEntity(
                location,
                EntityType.VILLAGER
        );

        villager.setCustomName(name);

        villager.setCustomNameVisible(
                plugin.getConfig().getBoolean(
                        "citizens.appearance.custom-name-visible", true
                )
        );

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

        villager.setAI(
                plugin.getConfig().getBoolean(
                        "citizens.appearance.ai-enabled", false
                )
        );

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

    // ==========================================
    //              CITIZEN REMOVAL
    // ==========================================

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

    // ==========================================
    //              DESTINATIONS
    // ==========================================

    public boolean addDestination(String name, Location location) {

        if (!plugin.getConfig().getBoolean("destinations.enabled", true)) {
            return false;
        }

        if (name == null || name.isBlank()) {
            return false;
        }

        if (location == null || location.getWorld() == null) {
            return false;
        }

        String key = name.toLowerCase(Locale.ROOT);

        destinations.put(key, location.clone());

        if (plugin.getConfig().getBoolean(
                "destinations.remember-locations", true
        )) {
            saveDestination(key, location);
        }

        return true;
    }

    public Location getDestination(String name) {

        if (name == null) {
            return null;
        }

        Location location = destinations.get(
                name.toLowerCase(Locale.ROOT)
        );

        return location == null ? null : location.clone();
    }

    public Set<String> getDestinationNames() {
        return Collections.unmodifiableSet(
                new HashSet<>(destinations.keySet())
        );
    }

    public boolean removeDestination(String name) {

        if (name == null) {
            return false;
        }

        String key = name.toLowerCase(Locale.ROOT);

        if (destinations.remove(key) == null) {
            return false;
        }

        locationsConfig.set("locations." + key, null);
        saveLocations();

        return true;
    }

    // ==========================================
    //              LOCATION STORAGE
    // ==========================================

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

        if (!plugin.getConfig().getBoolean(
                "destinations.remember-locations", true
        )) {
            return;
        }

        ConfigurationSection section =
                locationsConfig.getConfigurationSection("locations");

        if (section == null) {
            return;
        }

        for (String name : section.getKeys(false)) {

            String path = "locations." + name;

            String worldName = locationsConfig.getString(
                    path + ".world"
            );

            if (worldName == null) {
                continue;
            }

            World world = plugin.getServer().getWorld(worldName);

            if (world == null) {
                plugin.getLogger().warning(
                        "Could not load destination: " + name
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

            destinations.put(name.toLowerCase(Locale.ROOT), location);
        }

        plugin.getLogger().info(
                "Loaded " + destinations.size() + " destinations."
        );
    }

    private void saveLocations() {

        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }

            locationsConfig.save(locationsFile);

        } catch (IOException exception) {

            plugin.getLogger().severe(
                    "Could not save locations.yml: "
                            + exception.getMessage()
            );
        }
    }

    // ==========================================
    //              SHUTDOWN
    // ==========================================

    public void shutdown() {

        boolean removeOnDisable = plugin.getConfig().getBoolean(
                "citizens.spawn.remove-on-disable", true
        );

        if (removeOnDisable) {

            for (Citizen citizen : citizens.values()) {

                Entity entity = citizen.getEntity();

                if (entity != null && entity.isValid()) {
                    entity.remove();
                }
            }
        }

        citizens.clear();
        saveLocations();
    }
}
