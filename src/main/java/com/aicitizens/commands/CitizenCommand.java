
package com.aicitizens.commands;

import com.aicitizens.AICitizens;
import com.aicitizens.citizens.Citizen;
import com.aicitizens.citizens.CitizenManager;
import com.aicitizens.utils.RandomNames;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class CitizenCommand implements CommandExecutor {

    private final AICitizens plugin;
    private final CitizenManager manager;
    private final RandomNames randomNames;

    public CitizenCommand(AICitizens plugin, CitizenManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.randomNames = new RandomNames(plugin, manager);
    }

    private String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    private void send(CommandSender sender, String message) {
        String prefix = plugin.getConfig().getString(
                "messages.prefix", "&8[&aAICitizens&8]"
        );

        sender.sendMessage(color(prefix + " " + message));
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {

        if (!sender.hasPermission("aicitizens.admin")) {
            send(sender, "&cYou don't have permission.");
            return true;
        }

        if (args.length == 0) {
            send(sender, "&e/aic create [name]");
            send(sender, "&e/aic remove <UUID>");
            send(sender, "&e/aic list");
            send(sender, "&e/aic count");
            send(sender, "&e/aic location add <name>");
            send(sender, "&e/aic location list");
            send(sender, "&e/aic location remove <name>");
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "create" -> {

                if (!(sender instanceof Player player)) {
                    send(sender, "&cOnly players can create citizens.");
                    return true;
                }

                String name;

                if (args.length >= 2) {
                    name = String.join(" ", java.util.Arrays.copyOfRange(
                            args, 1, args.length
                    ));
                } else {
                    name = randomNames.generate();
                }

                Citizen citizen = manager.createCitizen(
                        name,
                        player.getLocation()
                );

                if (citizen == null) {
                    send(sender, "&cCould not create citizen. Check max-count and spawn settings.");
                    return true;
                }

                send(sender, "&aCreated citizen: &f" + name);
                send(sender, "&7UUID: &f" + citizen.getId());
                return true;
            }

            case "remove" -> {

                if (args.length < 2) {
                    send(sender, "&cUsage: /aic remove <UUID>");
                    return true;
                }

                UUID id;

                try {
                    id = UUID.fromString(args[1]);
                } catch (IllegalArgumentException exception) {
                    send(sender, "&cInvalid UUID.");
                    return true;
                }

                Citizen citizen = manager.getCitizen(id);

                if (citizen == null) {
                    send(sender, "&cCitizen not found.");
                    return true;
                }

                String citizenName = citizen.getName();

                if (!manager.removeCitizen(id)) {
                    send(sender, "&cCould not remove citizen.");
                    return true;
                }

                randomNames.release(citizenName);

                send(sender, "&aRemoved citizen: &f" + citizenName);
                return true;
            }

            case "list" -> {

                send(sender, "&aCitizens: &f" + manager.getCitizenCount());

                for (Citizen citizen : manager.getCitizens()) {
                    send(sender, "&7- &f" + citizen.getName()
                            + " &8(&7" + citizen.getId() + "&8)");
                }

                return true;
            }

            case "count" -> {
                send(sender, "&aCitizen count: &f" + manager.getCitizenCount());
                return true;
            }

            case "location" -> {

                if (args.length < 2) {
                    send(sender, "&cUsage: /aic location <add|list|remove>");
                    return true;
                }

                switch (args[1].toLowerCase()) {

                    case "add" -> {

                        if (!(sender instanceof Player player)) {
                            send(sender, "&cOnly players can add locations.");
                            return true;
                        }

                        if (args.length < 3) {
                            send(sender, "&cUsage: /aic location add <name>");
                            return true;
                        }

                        String locationName = String.join(
                                " ",
                                java.util.Arrays.copyOfRange(args, 2, args.length)
                        );

                        Location location = player.getLocation();

                        if (!manager.addDestination(locationName, location)) {
                            send(sender, "&cCould not add destination.");
                            return true;
                        }

                        send(sender, "&aDestination added: &f" + locationName);
                        return true;
                    }

                    case "list" -> {

                        send(sender, "&aSaved destinations:");

                        for (String name : manager.getDestinationNames()) {
                            send(sender, "&7- &f" + name);
                        }

                        return true;
                    }

                    case "remove" -> {

                        if (args.length < 3) {
                            send(sender, "&cUsage: /aic location remove <name>");
                            return true;
                        }

                        String locationName = String.join(
                                " ",
                                java.util.Arrays.copyOfRange(args, 2, args.length)
                        );

                        if (!manager.removeDestination(locationName)) {
                            send(sender, "&cDestination not found.");
                            return true;
                        }

                        send(sender, "&aDestination removed: &f" + locationName);
                        return true;
                    }

                    default -> {
                        send(sender, "&cUnknown location subcommand.");
                        return true;
                    }
                }
            }

            default -> {
                send(sender, "&cUnknown command. Use /aic");
                return true;
            }
        }
    }
}
