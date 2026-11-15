
package com.aicitizens.citizens;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

public class Citizen {

    private final UUID id;
    private final String name;
    private final Entity entity;

    private final Location spawnLocation;

    private Location destination;

    private boolean moving;

    public Citizen(
            UUID id,
            String name,
            Entity entity,
            Location spawnLocation
    ) {
        this.id = id;
        this.name = name;
        this.entity = entity;
        this.spawnLocation = spawnLocation.clone();
        this.moving = false;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Entity getEntity() {
        return entity;
    }

    public Location getSpawnLocation() {
        return spawnLocation.clone();
    }

    public Location getDestination() {
        return destination == null ? null : destination.clone();
    }

    public void setDestination(Location destination) {
        this.destination = destination == null
                ? null
                : destination.clone();
    }

    public boolean isMoving() {
        return moving;
    }

    public void setMoving(boolean moving) {
        this.moving = moving;
    }

    public boolean isValid() {
        return entity != null && entity.isValid();
    }
}
