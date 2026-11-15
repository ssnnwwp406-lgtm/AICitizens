
package com.aicitizens.citizens;

import com.aicitizens.AICitizens;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class CitizenMessageManager {

    private final AICitizens plugin;

    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public CitizenMessageManager(AICitizens plugin) {
        this.plugin = plugin;
    }

    // ==========================================
    //              ARRIVAL MESSAGE
    // ==========================================

    public void sendArrivalMessage(Citizen citizen) {

        if (!plugin.getConfig().getBoolean(
                "messages.enabled", true
        )) {
            return;
        }

        if (!plugin.getConfig().getBoolean(
                "messages.on-arrival.enabled", true
        )) {
            return;
        }

        if (citizen == null || !citizen.isValid()) {
            return;
        }

        long now = System.currentTimeMillis();

        long cooldown = plugin.getConfig().getLong(
                "messages.cooldown", 30
        ) * 1000L;

        long nextAllowed = cooldowns.getOrDefault(
                citizen.getId(), 0L
        );

        if (now < nextAllowed) {
            return;
        }

        List<String> lines = plugin.getConfig().getStringList(
                "messages.on-arrival.lines"
        );

        if (lines.isEmpty()) {
            return;
        }

        String message = lines.get(
                ThreadLocalRandom.current().nextInt(lines.size())
        );

        message = message.replace("{name}", citizen.getName());

        String prefix = plugin.getConfig().getString(
                "messages.prefix", "&8[&aAICitizens&8]"
        );

        String formatted = color(
                prefix + " &f" + citizen.getName() + ": " + message
        );

        sendToPlayers(citizen, formatted);

        cooldowns.put(citizen.getId(), now + cooldown);
    }

    // ==========================================
    //              MESSAGE DELIVERY
    // ==========================================

    private void sendToPlayers(Citizen citizen, String message) {

        String mode = plugin.getConfig().getString(
                "messages.mode", "nearby"
        );

        if (mode.equalsIgnoreCase("global")) {

            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage(message);
            }

            return;
        }

        Location citizenLocation = citizen.getEntity().getLocation();

        double range = plugin.getConfig().getDouble(
                "messages.range", 16
        );

        double rangeSquared = range * range;

        for (Player player : Bukkit.getOnlinePlayers()) {

            if (!player.getWorld().equals(citizenLocation.getWorld())) {
                continue;
            }

            if (player.getLocation().distanceSquared(
                    citizenLocation
            ) <= rangeSquared) {

                player.sendMessage(message);
            }
        }
    }

    // ==========================================
    //              COLOR FORMAT
    // ==========================================

    private String color(String message) {

        return ChatColor.translateAlternateColorCodes(
                '&',
                message
        );
    }

    // ==========================================
    //              CLEANUP
    // ==========================================

    public void removeCitizen(UUID id) {
        cooldowns.remove(id);
    }

    public void clear() {
        cooldowns.clear();
    }
}
