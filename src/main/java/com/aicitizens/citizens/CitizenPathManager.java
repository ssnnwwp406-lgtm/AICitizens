package com.aicitizens.citizens;

import com.aicitizens.AICitizens;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class CitizenPathManager {
    private static final int MAX_RECORDED_TICKS = 36_000;

    private final AICitizens plugin;
    private final CitizenManager citizens;
    private final CitizenNpcManager npcs;
    private final File file;
    private final YamlConfiguration data;
    private final Map<UUID, List<PathFrame>> routes = new HashMap<>();
    private final Map<UUID, Double> indexes = new HashMap<>();
    private final Map<UUID, Integer> directions = new HashMap<>();
    private final Map<UUID, List<PathFrame>> recordings = new HashMap<>();
    private final Map<UUID, BukkitTask> recordingTasks = new HashMap<>();
    private final Set<UUID> paused = new HashSet<>();

    public CitizenPathManager(AICitizens plugin, CitizenManager citizens, CitizenNpcManager npcs) {
        this.plugin = plugin;
        this.citizens = citizens;
        this.npcs = npcs;
        this.file = new File(plugin.getDataFolder(), "paths.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        loadRoutes();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickPatrols, 1L, 1L);
    }

    public boolean startRecording(Player player, Citizen citizen) {
        if (player == null || citizen == null || !citizen.isValid()) return false;
        UUID id = citizen.getId();
        stopRecordingTask(id);
        paused.add(id);

        List<PathFrame> frames = new ArrayList<>();
        frames.add(capture(player));
        recordings.put(id, frames);

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || !recordings.containsKey(id)) {
                stopRecordingTask(id);
                return;
            }
            List<PathFrame> current = recordings.get(id);
            if (current == null) return;
            if (current.size() >= MAX_RECORDED_TICKS) {
                stopRecordingTask(id);
                plugin.getLogger().warning("Citizen route recording reached the 30-minute safety limit.");
                return;
            }
            current.add(capture(player));
        }, 1L, 1L);
        recordingTasks.put(id, task);
        return true;
    }

    private PathFrame capture(Player player) {
        return new PathFrame(player.getLocation().clone(), player.isSprinting(), player.isOnGround());
    }

    public boolean stopRecording(UUID id) {
        boolean active = recordings.containsKey(id);
        stopRecordingTask(id);
        return active;
    }

    public boolean saveAndStart(Citizen citizen) {
        if (citizen == null) return false;
        List<PathFrame> frames = recordings.remove(citizen.getId());
        if (frames == null || frames.size() < 2) return false;
        World world = frames.get(0).location().getWorld();
        if (world == null || frames.stream().anyMatch(frame ->
                frame.location().getWorld() == null || !world.equals(frame.location().getWorld()))) return false;

        routes.put(citizen.getId(), cloneFrames(frames));
        indexes.put(citizen.getId(), 0.0);
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
            List<PathFrame> frames = readRoute(key(citizen));
            if (frames.size() < 2) return false;
            attachRoute(citizen, frames);
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
            List<PathFrame> route = routes.get(id);
            if (route == null || route.size() < 2) continue;

            Entity entity = citizen.getEntity();
            if (!(entity instanceof Villager villager) || !villager.isValid()) continue;

            double cursor = indexes.getOrDefault(id, 0.0);
            int direction = directions.getOrDefault(id, 1);
            double lastIndex = route.size() - 1.0;
            if (!Double.isFinite(cursor) || cursor < 0.0 || cursor > lastIndex) cursor = 0.0;

            double forwardSpeed = Math.max(0.1, plugin.getConfig().getDouble("movement.forward-speed", 1.0));
            double returnSpeed = Math.max(0.1, plugin.getConfig().getDouble("movement.return-speed", 2.5));

            // Turn around immediately at the route endpoints. The return trip can
            // move through more recorded samples per tick without making the
            // visible motion choppy because positions are interpolated below.
            if (direction >= 0) {
                cursor += forwardSpeed;
                if (cursor >= lastIndex) {
                    cursor = lastIndex;
                    direction = -1;
                }
            } else {
                cursor -= returnSpeed;
                if (cursor <= 0.0) {
                    cursor = 0.0;
                    direction = 1;
                }
            }

            indexes.put(id, cursor);
            directions.put(id, direction);

            Location next = interpolate(route, cursor);
            if (next == null || next.getWorld() == null || !next.getWorld().equals(villager.getWorld())) continue;

            if (villager.teleport(next)) {
                villager.setSilent(true);
                npcs.updateNpc(id, next);
            }
        }
    }

    private Location interpolate(List<PathFrame> route, double cursor) {
        if (route == null || route.isEmpty()) return null;
        int from = Math.max(0, Math.min(route.size() - 1, (int) Math.floor(cursor)));
        int to = Math.min(route.size() - 1, from + 1);
        double alpha = Math.max(0.0, Math.min(1.0, cursor - from));

        Location a = route.get(from).location();
        Location b = route.get(to).location();
        if (a.getWorld() == null || !a.getWorld().equals(b.getWorld())) return a.clone();

        float yawDelta = ((b.getYaw() - a.getYaw() + 540.0f) % 360.0f) - 180.0f;
        float yaw = a.getYaw() + (float) (yawDelta * alpha);
        float pitch = a.getPitch() + (b.getPitch() - a.getPitch()) * (float) alpha;
        return new Location(a.getWorld(),
                a.getX() + (b.getX() - a.getX()) * alpha,
                a.getY() + (b.getY() - a.getY()) * alpha,
                a.getZ() + (b.getZ() - a.getZ()) * alpha,
                yaw, pitch);
    }

    private void loadRoutes() {
        ConfigurationSection section = data.getConfigurationSection("routes");
        if (section == null) return;
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Citizen citizen : citizens.getCitizens()) {
                if (routes.containsKey(citizen.getId())) continue;
                List<PathFrame> frames = readRoute(key(citizen));
                if (frames.size() >= 2) attachRoute(citizen, frames);
            }
        }, 20L, 20L);
    }

    private List<PathFrame> readRoute(String routeKey) {
        List<PathFrame> frames = new ArrayList<>();
        ConfigurationSection route = data.getConfigurationSection("routes." + routeKey);
        if (route == null) return frames;

        List<String> keys = new ArrayList<>(route.getKeys(false));
        keys.sort(Comparator.comparingInt(value -> {
            try { return Integer.parseInt(value); } catch (NumberFormatException ignored) { return Integer.MAX_VALUE; }
        }));

        for (String pointKey : keys) {
            ConfigurationSection point = route.getConfigurationSection(pointKey);
            if (point == null) continue;
            World world = Bukkit.getWorld(point.getString("world", ""));
            if (world == null) continue;
            Location location = new Location(world, point.getDouble("x"), point.getDouble("y"),
                    point.getDouble("z"), (float) point.getDouble("yaw"), (float) point.getDouble("pitch"));
            frames.add(new PathFrame(location, point.getBoolean("sprinting", false),
                    point.getBoolean("on-ground", true)));
        }
        return frames;
    }

    private void attachRoute(Citizen citizen, List<PathFrame> frames) {
        routes.put(citizen.getId(), cloneFrames(frames));
        indexes.put(citizen.getId(), 0.0);
        directions.put(citizen.getId(), 1);
    }

    private void saveRoute(Citizen citizen) {
        String routePath = "routes." + key(citizen);
        data.set(routePath, null);
        List<PathFrame> frames = routes.get(citizen.getId());
        for (int i = 0; i < frames.size(); i++) {
            PathFrame frame = frames.get(i);
            Location point = frame.location();
            String path = routePath + "." + i;
            data.set(path + ".world", point.getWorld().getName());
            data.set(path + ".x", point.getX());
            data.set(path + ".y", point.getY());
            data.set(path + ".z", point.getZ());
            data.set(path + ".yaw", point.getYaw());
            data.set(path + ".pitch", point.getPitch());
            data.set(path + ".sprinting", frame.sprinting());
            data.set(path + ".on-ground", frame.onGround());
        }
        saveFile();
    }

    private String key(Citizen citizen) {
        return citizen.getName().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
    }

    private List<PathFrame> cloneFrames(List<PathFrame> source) {
        List<PathFrame> copy = new ArrayList<>(source.size());
        for (PathFrame frame : source) {
            copy.add(new PathFrame(frame.location().clone(), frame.sprinting(), frame.onGround()));
        }
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

    private record PathFrame(Location location, boolean sprinting, boolean onGround) {}
}