package com.aicitizens.citizens;

import com.aicitizens.AICitizens;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Slab;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

public class CitizenAI extends BukkitRunnable {

    private static final double SAMPLE_STEP = 0.5;
    private static final double MIN_ROUTE_LENGTH = 8.0;
    private static final double MAX_ROUTE_LENGTH = 32.0;

    private final AICitizens plugin;
    private final CitizenManager manager;
    private final CitizenMessageManager messageManager;
    private final CitizenNpcManager npcManager;
    private int tick;

    public CitizenAI(AICitizens plugin, CitizenManager manager, CitizenMessageManager messageManager) {
        this.plugin = plugin;
        this.manager = manager;
        this.messageManager = messageManager;
        this.npcManager = plugin.getCitizenNpcManager();
    }

    @Override
    public void run() {
        if (!plugin.getConfig().getBoolean("movement.enabled", true)) return;
        tick++;

        int retryInterval = Math.max(1,
                plugin.getConfig().getInt("movement.destination-selection-interval", 2));

        for (Citizen citizen : manager.getCitizens()) {
            if (!citizen.isValid()) continue;
            Entity entity = citizen.getEntity();
            if (!(entity instanceof Villager villager) || !villager.isValid()) continue;

            if (citizen.isMoving()) {
                moveCitizen(citizen, villager);
            } else if (tick % retryInterval == 0) {
                chooseStraightRoute(citizen, villager);
            }
        }
    }

    /*
     * Citizens only use continuous paths made from Smooth Stone Slabs.
     * A route is one long straight segment; a new random direction is chosen
     * only after reaching its end or finding that the path is blocked.
     */
    private void chooseStraightRoute(Citizen citizen, Villager villager) {
        Location current = villager.getLocation();
        World world = current.getWorld();
        if (world == null) return;

        double speed = Math.max(0.05, Math.min(1.0,
                plugin.getConfig().getDouble("movement.speed", 0.25)));
        double radius = Math.max(1.0,
                plugin.getConfig().getDouble("movement.radius", 300.0));
        Location origin = citizen.getSpawnLocation();

        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
            Vector direction = new Vector(Math.cos(angle), 0, Math.sin(angle)).normalize();
            double wantedLength = ThreadLocalRandom.current().nextDouble(16.0, MAX_ROUTE_LENGTH + 1.0);
            Location lastSafe = null;

            for (double distance = SAMPLE_STEP; distance <= wantedLength; distance += SAMPLE_STEP) {
                Location sample = current.clone().add(direction.clone().multiply(distance));
                Location surface = findSlabSurface(world, sample.getX(), sample.getZ());
                if (surface == null) break;
                if (Math.abs(surface.getY() - current.getY()) > 1.0) break;
                if (surface.distanceSquared(origin) > radius * radius) break;
                lastSafe = surface;
            }

            if (lastSafe != null && current.distance(lastSafe) >= MIN_ROUTE_LENGTH) {
                lastSafe.setYaw((float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ())));
                citizen.setDestination(lastSafe);
                citizen.setMoving(true);
                return;
            }
        }

        citizen.setDestination(null);
        citizen.setMoving(false);
    }

    private Location findSlabSurface(World world, double x, double z) {
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        Block floor = world.getHighestBlockAt(blockX, blockZ);

        if (floor.getType() != Material.SMOOTH_STONE_SLAB) return null;
        if (!(floor.getBlockData() instanceof Slab slab)) return null;

        double surfaceY = floor.getY() + (slab.getType() == Slab.Type.BOTTOM ? 0.5 : 1.0);
        Block feet = world.getBlockAt(blockX, floor.getY() + 1, blockZ);
        Block head = world.getBlockAt(blockX, floor.getY() + 2, blockZ);

        if (!feet.isPassable() || !head.isPassable()) return null;
        return new Location(world, x, surfaceY, z);
    }

    private void moveCitizen(Citizen citizen, Villager villager) {
        Location destination = citizen.getDestination();
        if (destination == null || destination.getWorld() == null
                || !destination.getWorld().equals(villager.getWorld())) {
            stopAndReroute(citizen, villager);
            return;
        }

        Location current = villager.getLocation();
        Vector direction = destination.toVector().subtract(current.toVector());
        direction.setY(0);

        if (direction.lengthSquared() <= 0.20) {
            messageManager.sendArrivalMessage(citizen);
            citizen.setMoving(false);
            citizen.setDestination(null);
            chooseStraightRoute(citizen, villager);
            return;
        }

        direction.normalize();
        double speed = Math.max(0.05, Math.min(1.0,
                plugin.getConfig().getDouble("movement.speed", 0.25)));
        double step = Math.min(speed, current.distance(destination));
        Location horizontalNext = current.clone().add(direction.clone().multiply(step));
        Location next = findSlabSurface(villager.getWorld(), horizontalNext.getX(), horizontalNext.getZ());

        if (next == null || Math.abs(next.getY() - current.getY()) > 1.0) {
            stopAndReroute(citizen, villager);
            return;
        }

        Location origin = citizen.getSpawnLocation();
        double radius = Math.max(1.0,
                plugin.getConfig().getDouble("movement.radius", 300.0));
        if (next.distanceSquared(origin) > radius * radius) {
            stopAndReroute(citizen, villager);
            return;
        }

        next.setYaw((float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ())));
        next.setPitch(0);

        if (!villager.teleport(next)) {
            stopAndReroute(citizen, villager);
            return;
        }
        npcManager.updateNpc(citizen.getId(), next);
    }

    private void stopAndReroute(Citizen citizen, Villager villager) {
        citizen.setMoving(false);
        citizen.setDestination(null);
        chooseStraightRoute(citizen, villager);
    }

    public void start() {
        long interval = Math.max(1L,
                plugin.getConfig().getLong("movement.update-interval", 2));
        runTaskTimer(plugin, 1L, interval);
    }
}
