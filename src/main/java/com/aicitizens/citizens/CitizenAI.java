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

    private static final double SAMPLE_STEP = 0.25;
    private static final double MIN_ROUTE_LENGTH = 0.20;
    private static final double MAX_ROUTE_LENGTH = 24.0;
    private static final double HEIGHT_TOLERANCE = 0.60;

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

            // Enforce silence continuously, including after citizen restoration.
            villager.setSilent(true);

            if (citizen.isMoving()) {
                moveCitizen(citizen, villager);
            } else if (tick % retryInterval == 0) {
                chooseStraightRoute(citizen, villager);
            }
        }
    }

    /*
     * Pick one of four cardinal directions and follow a straight slab path.
     * The route may be short; it must never require an arbitrary 8-block run.
     */
    private void chooseStraightRoute(Citizen citizen, Villager villager) {
        Location current = villager.getLocation();
        World world = current.getWorld();
        if (world == null) return;

        double radius = Math.max(1.0,
                plugin.getConfig().getDouble("movement.radius", 300.0));
        Location origin = citizen.getSpawnLocation();

        int firstDirection = ThreadLocalRandom.current().nextInt(4);
        for (int attempt = 0; attempt < 4; attempt++) {
            int directionIndex = (firstDirection + attempt) % 4;
            Vector direction = switch (directionIndex) {
                case 0 -> new Vector(0, 0, 1);
                case 1 -> new Vector(1, 0, 0);
                case 2 -> new Vector(0, 0, -1);
                default -> new Vector(-1, 0, 0);
            };

            double wantedLength = ThreadLocalRandom.current().nextDouble(4.0, MAX_ROUTE_LENGTH + 1.0);
            Location lastSafe = null;

            for (double distance = SAMPLE_STEP; distance <= wantedLength; distance += SAMPLE_STEP) {
                Location sample = current.clone().add(direction.clone().multiply(distance));
                Location surface = findSlabSurface(world, sample.getX(), sample.getZ(), current.getY());
                if (surface == null) break;
                if (Math.abs(surface.getY() - current.getY()) > HEIGHT_TOLERANCE) break;
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

    /*
     * Search around the expected feet height instead of relying on
     * getHighestBlockAt(), which can select an unrelated block above the slab.
     */
    private Location findSlabSurface(World world, double x, double z, double expectedY) {
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        int baseY = (int) Math.floor(expectedY);

        Location best = null;
        double bestDifference = Double.MAX_VALUE;

        for (int blockY = baseY - 2; blockY <= baseY + 1; blockY++) {
            Block floor = world.getBlockAt(blockX, blockY, blockZ);
            if (floor.getType() != Material.SMOOTH_STONE_SLAB) continue;
            if (!(floor.getBlockData() instanceof Slab slab)) continue;

            double surfaceY = floor.getY() + (slab.getType() == Slab.Type.BOTTOM ? 0.5 : 1.0);
            double difference = Math.abs(surfaceY - expectedY);
            if (difference > HEIGHT_TOLERANCE || difference >= bestDifference) continue;

            // Check the two air spaces above the actual collision surface.
            int feetY = (int) Math.floor(surfaceY + 0.51);
            int headY = (int) Math.floor(surfaceY + 1.51);
            Block feet = world.getBlockAt(blockX, feetY, blockZ);
            Block head = world.getBlockAt(blockX, headY, blockZ);
            if (!feet.isPassable() || !head.isPassable()) continue;

            best = new Location(world, x, surfaceY, z);
            bestDifference = difference;
        }

        return best;
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

        if (direction.lengthSquared() <= 0.04) {
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
        Location next = findSlabSurface(villager.getWorld(),
                horizontalNext.getX(), horizontalNext.getZ(), current.getY());

        if (next == null || Math.abs(next.getY() - current.getY()) > HEIGHT_TOLERANCE) {
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

        // The invisible carrier moves; the visible player-shaped NPC follows it.
        if (!villager.teleport(next)) {
            stopAndReroute(citizen, villager);
            return;
        }
        villager.setSilent(true);
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