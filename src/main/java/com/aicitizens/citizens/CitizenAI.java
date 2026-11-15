
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

        tick++;

        for (Citizen citizen : manager.getCitizens()) {

            if (!citizen.isValid()) {
                continue;
            }

            if (!citizen.isMoving()) {

                if (tick % 20 == 0) {
                    chooseDestination(citizen);
                }

                continue;
            }

            moveCitizen(citizen);
        }
    }

    private void chooseDestination(Citizen citizen) {

        List<Location> locations = new ArrayList<>();

        for (Location location : manager.getDestinationNames()
                .stream()
                .map(manager::getDestination)
                .toList()) {

            if (location == null || location.getWorld() == null) {
                continue;
            }

            if (!location.getWorld().equals(
                    citizen.getEntity().getWorld())) {
                continue;
            }

            locations.add(location);
        }

        if (locations.isEmpty()) {
            return;
        }

        Location destination = locations.get(
                ThreadLocalRandom.current().nextInt(locations.size())
        );

        if (citizen.getEntity().getLocation()
                .distanceSquared(destination) < 4) {
            return;
        }

        citizen.setDestination(destination);
        citizen.setMoving(true);
    }

    private void moveCitizen(Citizen citizen) {

        Entity entity = citizen.getEntity();

        if (!(entity instanceof Villager villager)) {
            citizen.setMoving(false);
            return;
        }

        Location destination = citizen.getDestination();

        if (destination == null ||
                destination.getWorld() == null ||
                !destination.getWorld().equals(villager.getWorld())) {

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

        double speed = plugin.getConfig()
                .getDouble("citizens.movement.speed", 0.25);

        Location next = current.clone().add(
                direction.multiply(speed)
        );

        next.setY(current.getY());

        next.setYaw((float) Math.toDegrees(
                Math.atan2(-direction.getX(), direction.getZ())
        ));

        if (!next.getBlock().isPassable() ||
                !next.clone().add(0, 1, 0).getBlock().isPassable()) {

            citizen.setMoving(false);
            return;
        }

        villager.teleport(next);
    }

    public void start() {

        long interval = Math.max(
                1L,
                plugin.getConfig().getLong(
                        "citizens.movement.update-interval", 10
                )
        );

        runTaskTimer(plugin, 1L, interval);
    }
}
