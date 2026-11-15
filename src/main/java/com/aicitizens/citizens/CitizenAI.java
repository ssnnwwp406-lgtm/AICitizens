
package com.aicitizens.citizens;

import com.aicitizens.AICitizens;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

public class CitizenAI extends BukkitRunnable {

    private static final double MOVEMENT_RADIUS = 300.0;

    private final AICitizens plugin;
    private final CitizenManager manager;
    private final CitizenMessageManager messageManager;

    private int tick = 0;

    public CitizenAI(
            AICitizens plugin,
            CitizenManager manager,
            CitizenMessageManager messageManager
    ) {
        this.plugin = plugin;
        this.manager = manager;
        this.messageManager = messageManager;
    }

    @Override
    public void run() {

        if (!plugin.getConfig().getBoolean("movement.enabled", true)) {
            return;
        }

        tick++;

        int selectionInterval = Math.max(
                1,
                plugin.getConfig().getInt(
                        "movement.destination-selection-interval", 20
                )
        );

        for (Citizen citizen : manager.getCitizens()) {

            if (!citizen.isValid()) continue;

            Entity entity = citizen.getEntity();

            if (!(entity instanceof Villager villager)) continue;
            if (!villager.isValid()) continue;

            if (!villager.getWorld().isChunkLoaded(
                    villager.getLocation().getBlockX() >> 4,
                    villager.getLocation().getBlockZ() >> 4
            )) continue;

            if (!citizen.isMoving()) {

                if (tick % selectionInterval == 0) {
                    chooseRandomDestination(citizen);
                }

                continue;
            }

            moveCitizen(citizen);
        }
    }

    private void chooseRandomDestination(Citizen citizen) {

        Location spawn = citizen.getSpawnLocation();

        if (spawn.getWorld() == null) return;

        World world = spawn.getWorld();

        for (int attempt = 0; attempt < 40; attempt++) {

            double angle = ThreadLocalRandom.current().nextDouble(
                    0, Math.PI * 2
            );

            double distance = Math.sqrt(
                    ThreadLocalRandom.current().nextDouble()
            ) * MOVEMENT_RADIUS;

            int x = spawn.getBlockX()
                    + (int) Math.round(Math.cos(angle) * distance);

            int z = spawn.getBlockZ()
                    + (int) Math.round(Math.sin(angle) * distance);

            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }

            int y = world.getHighestBlockYAt(x, z) + 1;

            Location target = new Location(
                    world,
                    x + 0.5,
                    y,
                    z + 0.5
            );

            if (!isSafeLocation(target)) continue;

            Location current = citizen.getEntity().getLocation();

            if (current.distanceSquared(target) < 16) continue;

            citizen.setDestination(target);
            citizen.setMoving(true);
            return;
        }
    }

    private boolean isSafeLocation(Location location) {

        Block floor = location.clone().subtract(0, 1, 0).getBlock();
        Block feet = location.getBlock();
        Block head = location.clone().add(0, 1, 0).getBlock();

        if (!floor.getType().isSolid()) return false;

        if (!feet.isPassable() || !head.isPassable()) return false;

        if (feet.isLiquid() || head.isLiquid()) return false;

        Material floorType = floor.getType();

        return floorType != Material.MAGMA_BLOCK
                && floorType != Material.CACTUS
                && floorType != Material.CAMPFIRE
                && floorType != Material.SOUL_CAMPFIRE;
    }

    private void moveCitizen(Citizen citizen) {

        Entity entity = citizen.getEntity();

        if (!(entity instanceof Villager villager)) {
            citizen.setMoving(false);
            return;
        }

        Location destination = citizen.getDestination();

        if (destination == null
                || !destination.getWorld().equals(villager.getWorld())) {
            citizen.setMoving(false);
            return;
        }

        Location current = villager.getLocation();

        Vector direction = destination.toVector()
                .subtract(current.toVector());

        direction.setY(0);

        if (direction.lengthSquared() < 0.5) {
            arrive(citizen);
            return;
        }

        direction.normalize();

        double speed = plugin.getConfig().getDouble(
                "movement.speed", 0.25
        );

        Location next = current.clone().add(
        direction.clone().multiply(speed)
        );

        int x = next.getBlockX();
        int z = next.getBlockZ();

        World world = next.getWorld();

        if (world == null || !world.isChunkLoaded(x >> 4, z >> 4)) {
            citizen.setMoving(false);
            return;
        }

        int groundY = world.getHighestBlockYAt(x, z);
        int nextY = groundY + 1;

        if (Math.abs(nextY - current.getBlockY()) > 1) {
            citizen.setMoving(false);
            return;
        }

        next.setY(nextY);

        if (!isSafeLocation(next)) {
            citizen.setMoving(false);
            return;
        }

        Location spawn = citizen.getSpawnLocation();

        if (next.distanceSquared(spawn) > MOVEMENT_RADIUS * MOVEMENT_RADIUS) {
            citizen.setMoving(false);
            return;
        }

        next.setYaw((float) Math.toDegrees(
                Math.atan2(-direction.getX(), direction.getZ())
        ));

        villager.teleport(next);
    }

    private void arrive(Citizen citizen) {

        citizen.setMoving(false);
        citizen.setDestination(null);

        messageManager.sendArrivalMessage(citizen);
    }

    public void start() {

        long interval = Math.max(
                1L,
                plugin.getConfig().getLong("movement.update-interval", 2)
        );

        runTaskTimer(plugin, 1L, interval);
    }
}
