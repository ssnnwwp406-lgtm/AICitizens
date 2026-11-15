
package com.aicitizens.citizens;

import com.aicitizens.AICitizens;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class CitizenAI extends BukkitRunnable {

    private final AICitizens plugin;
    private final CitizenManager manager;

    private int tick = 0;

    public CitizenAI(AICitizens plugin, CitizenManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public void run() {

        if (!plugin.getConfig().getBoolean("movement.enabled", true)) {
            return;
        }

        tick++;

        for (Citizen citizen : manager.getCitizens()) {

            if (!citizen.isValid()) {
                continue;
            }

            Entity entity = citizen.getEntity();

            if (entity == null || !entity.isValid()) {
                continue;
            }

            if (!entity.getWorld().isChunkLoaded(
                    entity.getLocation().getBlockX() >> 4,
                    entity.getLocation().getBlockZ() >> 4
            )) {
                continue;
            }

            if (!citizen.isMoving()) {

                int selectionInterval = Math.max(
                        1,
                        plugin.getConfig().getInt(
                                "movement.destination-selection-interval", 20
                        )
                );

                if (tick % selectionInterval == 0) {
                    chooseDestination(citizen);
                }

                continue;
            }

            moveCitizen(citizen);
        }
    }

    // ==========================================
    //          DESTINATION SELECTION
    // ==========================================

    private void chooseDestination(Citizen citizen) {

        if (!plugin.getConfig().getBoolean(
                "destinations.enabled", true
        )) {
            return;
        }

        List<Location> locations = new ArrayList<>();

        for (String name : manager.getDestinationNames()) {

            Location location = manager.getDestination(name);

            if (location == null || location.getWorld() == null) {
                continue;
            }

            if (!location.getWorld().equals(
                    citizen.getEntity().getWorld()
            )) {
                continue;
            }

            double distance = citizen.getEntity()
                    .getLocation()
                    .distance(location);

            double minimum = plugin.getConfig().getDouble(
                    "movement.minimum-distance", 2.0
            );

            double maximum = plugin.getConfig().getDouble(
                    "movement.maximum-distance", 50.0
            );

            if (distance < minimum || distance > maximum) {
                continue;
            }

            locations.add(location);
        }

        if (locations.isEmpty()) {
            return;
        }

        Location destination;

        if (plugin.getConfig().getBoolean(
                "movement.random-destinations", true
        )) {

            destination = locations.get(
                    ThreadLocalRandom.current().nextInt(locations.size())
            );

        } else {

            destination = locations.get(0);
        }

        citizen.setDestination(destination);
        citizen.setMoving(true);
    }

    // ==========================================
    //              MOVEMENT
    // ==========================================

    private void moveCitizen(Citizen citizen) {

        Entity entity = citizen.getEntity();

        if (!(entity instanceof Villager villager)) {
            citizen.setMoving(false);
            return;
        }

        Location destination = citizen.getDestination();

        if (destination == null
                || destination.getWorld() == null
                || !destination.getWorld().equals(villager.getWorld())) {

            citizen.setMoving(false);
            return;
        }

        Location current = villager.getLocation();

        Vector direction = destination.toVector()
                .subtract(current.toVector());

        direction.setY(0);

        if (direction.lengthSquared() < 0.25) {
            citizen.setMoving(false);
            return;
        }

        direction.normalize();

        double speed = plugin.getConfig().getDouble(
                "movement.speed", 0.25
        );

        Location next = current.clone().add(
                direction.multiply(speed)
        );

        next.setY(current.getY());

        next.setYaw((float) Math.toDegrees(
                Math.atan2(-direction.getX(), direction.getZ())
        ));

        boolean stopWhenBlocked = plugin.getConfig().getBoolean(
                "movement.stop-when-blocked", true
        );

        if (stopWhenBlocked) {

            if (!next.getBlock().isPassable()
                    || !next.clone().add(0, 1, 0)
                    .getBlock().isPassable()) {

                citizen.setMoving(false);
                return;
            }
        }

        villager.teleport(next);
    }

    // ==========================================
    //              START
    // ==========================================

    public void start() {

        long interval = Math.max(
                1L,
                plugin.getConfig().getLong(
                        "movement.update-interval", 2
                )
        );

        runTaskTimer(plugin, 1L, interval);
    }
}
