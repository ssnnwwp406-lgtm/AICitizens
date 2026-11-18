package com.aicitizens.citizens;

import com.aicitizens.AICitizens;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class CitizenPathManager {
    private final AICitizens plugin;
    private final CitizenManager citizens;
    private final CitizenNpcManager npcs;
    private final File file;
    private final YamlConfiguration data;
    private final Map<UUID, List<Location>> routes = new HashMap<>();
    private final Map<UUID, Integer> indexes = new HashMap<>();
    private final Map<UUID, Integer> directions = new HashMap<>();
    private final Map<UUID, List<Location>> recordings = new HashMap<>();
    private final Map<UUID, BukkitTask> recordingTasks = new HashMap<>();
    private final Set<UUID> paused = new HashSet<>();

    public CitizenPathManager(AICitizens plugin, CitizenManager citizens, CitizenNpcManager npcs) {
        this.plugin = plugin;
        this.citizens = citizens;
        this.npcs = npcs;
        this.file = new File(plugin.getDataFolder(), "paths.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        loadRoutes();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickPatrols, 1L, 2L);
    }

    public boolean startRecording(org.bukkit.entity.Player player, Citizen citizen) {
        if (player == null || citizen == null || !citizen.isValid()) return false;
        UUID id = citizen.getId();
        stopRecordingTask(id);
        paused.add(id);
        List<Location> points = new ArrayList<>();
        points.add(player.getLocation().clone());
        recordings.put(id, points);
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || !recordings.containsKey(id)) {
                stopRecordingTask(id);
                return;
            }
            Location now = player.getLocation().clone();
            List<Location> list = recordings.get(id);
            if (list.isEmpty()) {
                list.add(now);
                return;
            }
            Location last = list.get(list.size() - 1);
            if (!last.getWorld().equals(now.getWorld())) return;
            if (last.distanceSquared(now) >= 0.36 || Math.abs(last.getYaw() - now.getYaw()) >= 12.0f) {
                list.add(now);
            }
        }, 1L, 2L);
        recordingTasks.put(id, task);
        return true;
    }

    public boolean stopRecording(UUID id) {
        stopRecordingTask(id);
        return recordings.containsKey(id);
    }

    public boolean saveAndStart(Citizen citizen) {
        if (citizen == null) return false;
        List<Location> points = recordings.remove(citizen.getId());
        if (points == null || points.size() < 2) return false;
        Location last = points.get(points.size() - 1);
        if (points.get(0).getWorld() == null || !points.get(0).getWorld().equals(last.getWorld())) return false;
        routes.put(citizen.getId(), cloneLocations(points));
        indexes.put(citizen.getId(), 0);
        directions.put(citizen.getId(), 1);
        paused.remove(citizen.getId());
        saveRoute(citizen);
        return true;
    }

    public boolean pause(Citizen citizen) {
        if (citizen == null || !routes.containsKey(citizen.getId())) return false;
        paused.add(citizen.getId());
        return true;
    }

    public boolean resume(Citizen citizen) {
        if (citizen == null) return false;
        UUID id = citizen.getId();
        if (!routes.containsKey(id)) {
            ConfigurationSection route = data.getConfigurationSection("routes." + key(citizen));
            if (route == null) return false;
            List<Location> points = new ArrayList<>();
            for (String pointKey : route.getKeys(false)) {
                ConfigurationSection point = route.getConfigurationSection(pointKey);
                if (point == null) continue;
                World world = Bukkit.getWorld(point.getString("world", ""));
                if (world != null) points.add(new Location(world, point.getDouble("x"), point.getDouble("y"),
                        point.getDouble("z"), (float) point.getDouble("yaw"), (float) point.getDouble("pitch")));
            }
            if (points.size() < 2) return false;
            attachRoute(citizen, points);
        }
        paused.remove(id);
        return true;
    }

    public boolean halt(Citizen citizen) {
        if (citizen == null) return false;
        UUID id = citizen.getId();
        boolean existed = routes.containsKey(id);
        routes.remove(id);
        indexes.remove(id);
        directions.remove(id);
        paused.add(id);
        return existed;
    }

    public void removeCitizen(Citizen citizen) {
        if (citizen == null) return;
        UUID id = citizen.getId();
        stopRecordingTask(id);
        recordings.remove(id);
        routes.remove(id);
        indexes.remove(id);
        directions.remove(id);
        paused.remove(id);
        data.set("routes." + key(citizen), null);
        saveFile();
    }

    private void tickPatrols() {
        for (Citizen citizen : citizens.getCitizens()) {
            UUID id = citizen.getId();
            if (!citizen.isValid() || paused.contains(id)) continue;
            List<Location> route = routes.get(id);
            if (route == null || route.size() < 2) continue;
            Entity entity = citizen.getEntity();
            if (!(entity instanceof Villager villager) || !villager.isValid()) continue;

            int index = indexes.getOrDefault(id, 0);
            int direction = directions.getOrDefault(id, 1);
            if (index < 0 || index >= route.size()) index = 0;
            Location target = route.get(index);
            if (target.getWorld() == null || !target.getWorld().equals(villager.getWorld())) continue;

            Location current = villager.getLocation();
            Vector delta = target.toVector().subtract(current.toVector());
            double distance = delta.length();
            double speed = Math.max(0.04, Math.min(0.8, plugin.getConfig().getDouble("movement.speed", 0.22)));
            Location next;

            // Always face the direction of travel, including while walking back.
            // Recorded waypoint yaw belongs to the original player recording and
            // would make the citizen turn incorrectly on the reverse leg.
            float travelYaw = current.getYaw();
            if (distance > 0.001) {
                Vector directionVector = delta.clone().normalize();
                travelYaw = (float) Math.toDegrees(Math.atan2(-directionVector.getX(), directionVector.getZ()));
            }

            if (distance <= speed + 0.08) {
                next = target.clone();
                next.setYaw(travelYaw);
                next.setPitch(0.0f);
                if (index == route.size() - 1) direction = -1;
                else if (index == 0) direction = 1;
                index += direction;
                indexes.put(id, index);
                directions.put(id, direction);
            } else {
                delta.normalize();
                next = current.clone().add(delta.multiply(speed));
                next.setYaw(travelYaw);
                next.setPitch(0.0f);
            }
            if (villager.teleport(next)) {
                villager.setSilent(true);
                npcs.updateNpc(id, next);
            }
        }
    }

    private void loadRoutes() {
        ConfigurationSection section = data.getConfigurationSection("routes");
        if (section == null) return;
        for (String routeKey : section.getKeys(false)) {
            ConfigurationSection route = section.getConfigurationSection(routeKey);
            if (route == null) continue;
            List<Location> points = new ArrayList<>();
            for (String pointKey : route.getKeys(false)) {
                ConfigurationSection point = route.getConfigurationSection(pointKey);
                if (point == null) continue;
                World world = Bukkit.getWorld(point.getString("world", ""));
                if (world == null) continue;
                points.add(new Location(world, point.getDouble("x"), point.getDouble("y"),
                        point.getDouble("z"), (float) point.getDouble("yaw"), (float) point.getDouble("pitch")));
            }
            if (points.size() >= 2) {
                // Routes are keyed by citizen name, then attached to the restored runtime UUID.
                for (Citizen citizen : citizens.getCitizens()) {
                    if (key(citizen).equals(routeKey)) attachRoute(citizen, points);
                }
            }
        }
        // Citizens may restore after this manager is constructed; attach them as they appear.
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Citizen citizen : citizens.getCitizens()) {
                if (routes.containsKey(citizen.getId())) continue;
                String routeKey = key(citizen);
                ConfigurationSection route = data.getConfigurationSection("routes." + routeKey);
                if (route == null) continue;
                List<Location> points = new ArrayList<>();
                for (String pointKey : route.getKeys(false)) {
                    ConfigurationSection point = route.getConfigurationSection(pointKey);
                    if (point == null) continue;
                    World world = Bukkit.getWorld(point.getString("world", ""));
                    if (world != null) points.add(new Location(world, point.getDouble("x"), point.getDouble("y"),
                            point.getDouble("z"), (float) point.getDouble("yaw"), (float) point.getDouble("pitch")));
                }
                if (points.size() >= 2) attachRoute(citizen, points);
            }
        }, 20L, 20L);
    }

    private void attachRoute(Citizen citizen, List<Location> points) {
        routes.put(citizen.getId(), cloneLocations(points));
        indexes.put(citizen.getId(), 0);
        directions.put(citizen.getId(), 1);
    }

    private void saveRoute(Citizen citizen) {
        String routePath = "routes." + key(citizen);
        data.set(routePath, null);
        List<Location> points = routes.get(citizen.getId());
        for (int i = 0; i < points.size(); i++) {
            Location point = points.get(i);
            String p = routePath + "." + i;
            data.set(p + ".world", point.getWorld().getName());
            data.set(p + ".x", point.getX());
            data.set(p + ".y", point.getY());
            data.set(p + ".z", point.getZ());
            data.set(p + ".yaw", point.getYaw());
            data.set(p + ".pitch", point.getPitch());
        }
        saveFile();
    }

    private String key(Citizen citizen) {
        return citizen.getName().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
    }

    private List<Location> cloneLocations(List<Location> source) {
        List<Location> copy = new ArrayList<>();
        for (Location location : source) copy.add(location.clone());
        return copy;
    }

    private void stopRecordingTask(UUID id) {
        BukkitTask task = recordingTasks.remove(id);
        if (task != null) task.cancel();
    }

    private void saveFile() {
        try {
            data.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save paths.yml: " + exception.getMessage());
        }
    }

    public void shutdown() {
        for (BukkitTask task : recordingTasks.values()) task.cancel();
        recordingTasks.clear();
        recordings.clear();
    }
}
