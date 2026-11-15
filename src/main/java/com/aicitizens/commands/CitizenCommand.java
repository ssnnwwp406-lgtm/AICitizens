
package com.aicitizens.commands;

import com.aicitizens.citizens.Citizen;
import com.aicitizens.citizens.CitizenManager;
import com.aicitizens.utils.RandomNames;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.UUID;

public class CitizenCommand implements CommandExecutor {

    private final CitizenManager manager;

    public CitizenCommand(CitizenManager manager) {
        this.manager = manager;
    }

    private String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {

        if (!sender.hasPermission("aicitizens.admin")) {
            sender.sendMessage(color("&cYou don't have permission."));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "create" -> {

                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Players only.");
                    return true;
                }

                String name = args.length >= 2
                        ? String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                        : RandomNames.generate();

                Citizen citizen = manager.createCitizen(
                        name,
                        player.getLocation()
                );

                if (citizen == null) {
                    sender.sendMessage(color("&cCannot create more citizens."));
                    return true;
                }

                sender.sendMessage(color("&aCreated citizen: &f" + name));
                sender.sendMessage(color("&7UUID: &f" + citizen.getId()));
            }

            case "remove" -> {

                if (args.length < 2) {
                    sender.sendMessage(color("&cUsage: /aic remove <uuid>"));
                    return true;
                }

                try {
                    UUID id = UUID.fromString(args[1]);

                    sender.sendMessage(manager.removeCitizen(id)
                            ? color("&aCitizen removed.")
                            : color("&cCitizen not found."));

                } catch (IllegalArgumentException exception) {
                    sender.sendMessage(color("&cInvalid UUID."));
                }
            }

            case "list" -> {

                sender.sendMessage(color("&aCitizens:"));

                for (Citizen citizen : manager.getCitizens()) {
                    sender.sendMessage(color(
                            "&7- &f" + citizen.getName()
                                    + " &8(" + citizen.getId() + ")"
                    ));
                }
            }

            case "count" -> sender.sendMessage(color(
                    "&aCitizens: &f" + manager.getCitizenCount()
            ));

            case "location" -> handleLocation(sender, args);

            default -> sendHelp(sender);
        }

        return true;
    }

    private void handleLocation(CommandSender sender, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(color("&cUsage: /aic location add/list/remove"));
            return;
        }

        switch (args[1].toLowerCase()) {

            case "add" -> {

                if (args.length < 3) {
                    sender.sendMessage(color("&cUsage: /aic location add <name>"));
                    return;
                }

                String name = args[2].toLowerCase();
                Location location = player.getLocation();

                if (manager.addDestination(name, location)) {
                    sender.sendMessage(color(
                            "&aLocation &f" + name
                                    + " &asaved at your current position!"
                    ));
                } else {
                    sender.sendMessage(color("&cCould not save location."));
                }
            }

            case "list" -> {

                sender.sendMessage(color("&aSaved locations:"));

                if (manager.getDestinationNames().isEmpty()) {
                    sender.sendMessage(color("&7No locations saved."));
                    return;
                }

                for (String name : manager.getDestinationNames()) {
                    sender.sendMessage(color("&7- &f" + name));
                }
            }

            case "remove" -> {

                if (args.length < 3) {
                    sender.sendMessage(color("&cUsage: /aic location remove <name>"));
                    return;
                }

                boolean removed = manager.removeDestination(args[2]);

                sender.sendMessage(removed
                        ? color("&aLocation removed.")
                        : color("&cLocation not found."));
            }

            default -> sender.sendMessage(color(
                    "&cUsage: /aic location add/list/remove"
            ));
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(color("&aAICitizens Commands:"));
        sender.sendMessage(color("&7/aic create [name]"));
        sender.sendMessage(color("&7/aic remove <uuid>"));
        sender.sendMessage(color("&7/aic list"));
        sender.sendMessage(color("&7/aic count"));
        sender.sendMessage(color("&7/aic location add <name>"));
        sender.sendMessage(color("&7/aic location list"));
        sender.sendMessage(color("&7/aic location remove <name>"));
    }
}
