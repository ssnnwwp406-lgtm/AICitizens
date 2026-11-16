package com.aicitizens.citizens;

import com.aicitizens.AICitizens;
import com.aicitizens.utils.SkinManager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CitizenManager {

    private final AICitizens plugin;
    private final Map<UUID, Citizen> citizens = new HashMap<>();
    private final Map<UUID, String> storageKeys = new HashMap<>();
    private final File dataFile;
    private final YamlConfiguration data;

    public CitizenManager(AICitizens plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "citizens.yml");
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.data = YamlConfiguration.loadConfiguration(dataFile);
    }

    public Citizen createCitizen(String name, String skinName, Location location) {
        if (!plugin.getConfig().getBoolean("citizens.spawn.enabled", true)) return null;

        int maxCount = Math.max(1, plugin.getConfig().getInt("citizens.max-count", 50));
        if (citizens.size() >= maxCount) return null;
        if (name == null || name.isBlank() || skinName == null || skinName.isBlank()) return null;
        if (location == null || location.getWorld() == null) return null;

        String entityType = plugin.getConfig().getString("citizens.appearance.type", "VILLAGER");
        if (!"VILLAGER".equalsIgnoreCase(entityType)) {
            plugin.getLogger().warning("Unsupported citizen type: " + entityType);
            return null;
        }

        Villager villager = (Villager) location.getWorld().spawnEntity(location, EntityType.VILLAGER);
        villager.setCustomName(name);
        villager.setCustomNameVisible(plugin.getConfig().getBoolean(
                "citizens.appearance.custom-name-visible", false));
        villager.setInvisible(true);
        // The Villager is only a hidden movement carrier; keep its sounds muted.
        villager.setSilent(true);
        villager.setInvulnerable(plugin.getConfig().getBoolean("citizens.appearance.invulnerable", true));
        villager.setCollidable(plugin.getConfig().getBoolean("citizens.appearance.collidable", false));
        villager.setPersistent(plugin.getConfig().getBoolean("citizens.appearance.persistent", true));
        villager.setAI(plugin.getConfig().getBoolean("citizens.appearance.ai-enabled", false));

        UUID id = villager.getUniqueId();
        Citizen citizen = new Citizen(id, name, skinName, villager, location);
        citizens.put(id, citizen);
        return citizen;
    }

    public void restoreSavedCitizens() {
        ConfigurationSection section = data.getConfigurationSection("citizens");
        if (section == null) return;

        int restored = 0;
        for (String key : section.getKeys(false)) {
            if (citizens.size() >= Math.max(1, plugin.getConfig().getInt("citizens.max-count", 50))) break;

            ConfigurationSection saved = section.getConfigurationSection(key);
            if (saved == null) continue;

            String name = saved.getString("name");
            String skinName = saved.getString("skin");
            String worldName = saved.getString("world");
            World world = worldName == null ? null : Bukkit.getWorld(worldName);
            if (name == null || skinName == null || world == null) {
                plugin.getLogger().warning("Skipping saved citizen " + key + ": missing name, skin, or world.");
                continue;
            }

            Location location = new Location(
                    world,
                    saved.getDouble("x"),
                    saved.getDouble("y"),
                    saved.getDouble("z"),
                    (float) saved.getDouble("yaw"),
                    (float) saved.getDouble("pitch")
            );

            plugin.getSkinManager().getSkin(skinName).whenComplete((skin, error) ->
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!plugin.isEnabled()) return;
                        if (error != null) {
                            plugin.getLogger().warning("Could not restore citizen " + name
                                    + " skin " + skinName + ": " + error.getMessage());
                            return;
                        }

                        Citizen citizen = createCitizen(name, skinName, location);
                        if (citizen == null) return;

                        storageKeys.put(citizen.getId(), key);
                        if (!plugin.getCitizenNpcManager().createNpc(citizen, skin)) {
                            Entity entity = citizen.getEntity();
                            if (entity != null && entity.isValid()) entity.remove();
                            citizens.remove(citizen.getId());
                            storageKeys.remove(citizen.getId());
                            plugin.getLogger().warning("Could not create NPC for restored citizen " + name);
                        }
                    })
            );
            restored++;
        }

        if (restored > 0) {
            plugin.getLogger().info("Queued " + restored + " saved citizens for restoration.");
        }
    }

    public void saveCitizens() {
        // Keep records that could not be restored (for example, while Mojang is unavailable).
        // A restored Villager receives a new runtime UUID, so replace its previous storage key
        // instead of leaving the old record behind and duplicating it on the next restart.
        for (Citizen citizen : citizens.values()) {
            if (!citizen.isValid()) continue;

            Location location = citizen.getSpawnLocation();
            World world = location.getWorld();
            if (world == null) continue;

            String currentKey = citizen.getId().toString();
            String previousKey = storageKeys.get(citizen.getId());
            if (previousKey != null && !previousKey.equals(currentKey)) {
                data.set("citizens." + previousKey, null);
            }

            String path = "citizens." + currentKey;
            storageKeys.put(citizen.getId(), currentKey);
            data.set(path + ".name", citizen.getName());
            data.set(path + ".skin", citizen.getSkinName());
            data.set(path + ".world", world.getName());
            data.set(path + ".x", location.getX());
            data.set(path + ".y", location.getY());
            data.set(path + ".z", location.getZ());
            data.set(path + ".yaw", location.getYaw());
            data.set(path + ".pitch", location.getPitch());
        }

        try {
            data.save(dataFile);
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save citizens.yml: " + exception.getMessage());
        }
    }

    public boolean removeCitizen(UUID id) {
        Citizen citizen = citizens.remove(id);
        if (citizen == null) return false;

        Entity entity = citizen.getEntity();
        if (entity != null && entity.isValid()) entity.remove();

        String storageKey = storageKeys.remove(id);
        data.set("citizens." + (storageKey == null ? id.toString() : storageKey), null);
        try {
            data.save(dataFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not update citizens.yml: " + exception.getMessage());
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
        saveCitizens();

        // Remove runtime entities after saving; they are recreated from citizens.yml next startup.
        for (Citizen citizen : citizens.values()) {
            Entity entity = citizen.getEntity();
            if (entity != null && entity.isValid()) entity.remove();
        }
        citizens.clear();
    }
}
