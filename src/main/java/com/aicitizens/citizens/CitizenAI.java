
package com.aicitizens.citizens;

import com.aicitizens.AICitizens;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class CitizenAI extends BukkitRunnable {

    private final AICitizens plugin;
    private final CitizenManager manager;

    public CitizenAI(AICitizens plugin, CitizenManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public void run() {

        for (Citizen citizen : manager.getCitizens()) {

            if (!citizen.isValid()) {
                continue;
            }

            if (!citizen.isMoving()) {
                continue;
            }

            Location destination = citizen.getDestination();

            if (destination == null ||
                destination.getWorld() == null) {
                citizen.setMoving(false);
                continue;
            }

            Entity entity = citizen.getEntity();

            Location current = entity.getLocation();

            if (!current.getWorld().equals(destination.getWorld())) {
                citizen.setMoving(false);
                continue;
            }

            Vector direction = destination.toVector()
                    .subtract(current.toVector());

            direction.setY(0);

            if (direction.lengthSquared() < 0.25) {
                citizen.setMoving(false);
                continue;
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
                continue;
            }

            if (entity instanceof Villager villager) {
                villager.teleport(next);
            }
        }
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
