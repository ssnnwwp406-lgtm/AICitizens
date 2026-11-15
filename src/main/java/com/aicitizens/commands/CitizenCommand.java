
package com.aicitizens.commands;

import com.aicitizens.citizens.Citizen;
import com.aicitizens.citizens.CitizenManager;
import com.aicitizens.utils.RandomNames;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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
            sender.sendMessage(color("&aAICitizens Commands:"));
            sender.sendMessage(color("&7/aic create"));
            sender.sendMessage(color("&7/aic create <name>"));
            sender.sendMessage(color("&7/aic remove <uuid>"));
            sender.sendMessage(color("&7/aic list"));
            sender.sendMessage(color("&7/aic count"));
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "create" -> {

                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Players only.");
                    return true;
                }

                String name = args.length >= 2
                        ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length))
                        : RandomNames.generate();

                Citizen citizen = manager.createCitizen(
                        name,
                        player.getLocation()
                );

                if (citizen == null) {
                    sender.sendMessage(color("&cCannot create more citizens."));
                    return true;
                }

                sender.sendMessage(color(
                        "&aCreated citizen: &f" + name
                ));

                sender.sendMessage(color(
                        "&7UUID: &f" + citizen.getId()
                ));
            }

            case "remove" -> {

                if (args.length < 2) {
                    sender.sendMessage(color("&cUsage: /aic remove <uuid>"));
                    return true;
                }

                try {
                    UUID id = UUID.fromString(args[1]);

                    if (manager.removeCitizen(id)) {
                        sender.sendMessage(color("&aCitizen removed."));
                    } else {
                        sender.sendMessage(color("&cCitizen not found."));
                    }

                } catch (IllegalArgumentException exception) {
                    sender.sendMessage(color("&cInvalid UUID."));
                }
            }

            case "list" -> {

                sender.sendMessage(color("&aCitizens:"));

                for (Citizen citizen : manager.getCitizens()) {
                    sender.sendMessage(
                            color("&7- &f" + citizen.getName()
                                    + " &8(" + citizen.getId() + ")")
                    );
                }
            }

            case "count" -> {

                sender.sendMessage(color(
                        "&aCitizens: &f" + manager.getCitizenCount()
                ));
            }

            default -> sender.sendMessage(color("&cUnknown command."));
        }

        return true;
    }
}
