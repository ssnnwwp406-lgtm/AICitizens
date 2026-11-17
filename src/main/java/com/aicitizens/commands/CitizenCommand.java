package com.aicitizens.commands;

import com.aicitizens.AICitizens;
import com.aicitizens.citizens.Citizen;
import com.aicitizens.citizens.CitizenManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CitizenCommand implements CommandExecutor {
    private final AICitizens plugin;
    private final CitizenManager manager;
    private final Map<UUID, UUID> selected = new HashMap<>();

    public CitizenCommand(AICitizens plugin, CitizenManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    private String color(String message) { return ChatColor.translateAlternateColorCodes('&', message); }
    private void send(CommandSender sender, String message) { sender.sendMessage(color("&a[AICitizens] &f" + message)); }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("aicitizens.admin")) { send(sender, "&cYou don't have permission."); return true; }
        if (args.length == 0) {
            send(sender, "&eCommands:");
            send(sender, "&7/aic create <name> <skin>");
            send(sender, "&7/aic select <UUID>");
            send(sender, "&7/aic path <start|stop|save|pause|resume|halt>");
            send(sender, "&7/aic remove <UUID> | list | count");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "create" -> {
                if (!(sender instanceof Player player)) { send(sender, "&cOnly players can create citizens."); return true; }
                if (args.length < 3) { send(player, "&cUsage: /aic create <name> <skin>"); return true; }
                String skinName = args[args.length - 1];
                String citizenName = String.join(" ", Arrays.copyOfRange(args, 1, args.length - 1));
                Location spawnLocation = player.getLocation().clone();
                send(player, "&eLoading Minecraft skin for &f" + skinName + "&e...");
                plugin.getSkinManager().getSkin(skinName).whenComplete((skin, error) ->
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!player.isOnline()) return;
                        if (error != null) { send(player, "&cCould not load skin: " + error.getMessage()); return; }
                        Citizen citizen = manager.createCitizen(citizenName, skinName, spawnLocation);
                        if (citizen == null) { send(player, "&cCould not create citizen. Check max-count and spawn settings."); return; }
                        boolean created = plugin.getCitizenNpcManager().createNpc(citizen, skin);
                        if (!created) { manager.removeCitizen(citizen.getId()); send(player, "&cCould not create the NPC."); return; }
                        if (citizen.getEntity() instanceof Villager villager) {
                            villager.setInvisible(true); villager.setCustomNameVisible(false); villager.setSilent(true);
                        }
                        selected.put(player.getUniqueId(), citizen.getId());
                        send(player, "&aCitizen created and selected!");
                        send(player, "&7Name: &f" + citizenName);
                        send(player, "&7UUID: &f" + citizen.getId());
                    }));
                return true;
            }
            case "select" -> {
                if (!(sender instanceof Player player)) { send(sender, "&cOnly players can select citizens."); return true; }
                if (args.length < 2) { send(player, "&cUsage: /aic select <UUID>"); return true; }
                try {
                    UUID id = UUID.fromString(args[1]);
                    Citizen citizen = manager.getCitizen(id);
                    if (citizen == null) { send(player, "&cCitizen not found."); return true; }
                    selected.put(player.getUniqueId(), id);
                    send(player, "&aSelected citizen: &f" + citizen.getName());
                } catch (IllegalArgumentException ex) { send(player, "&cInvalid UUID."); }
                return true;
            }
            case "path" -> {
                if (!(sender instanceof Player player)) { send(sender, "&cOnly players can use path recording."); return true; }
                if (args.length < 2) { send(player, "&eUsage: /aic path <start|stop|save|pause|resume|halt>"); return true; }
                UUID id = selected.get(player.getUniqueId());
                Citizen citizen = id == null ? null : manager.getCitizen(id);
                if (citizen == null) { send(player, "&cSelect a citizen first with /aic select <UUID>."); return true; }
                switch (args[1].toLowerCase()) {
                    case "start" -> {
                        if (plugin.getCitizenPathManager().startRecording(player, citizen)) send(player, "&aRecording started. Walk the exact route you want the citizen to follow.");
                        else send(player, "&cCould not start recording.");
                    }
                    case "stop" -> {
                        if (plugin.getCitizenPathManager().stopRecording(id)) send(player, "&eRecording stopped. Now use /aic path save.");
                        else send(player, "&cNo active recording for this citizen.");
                    }
                    case "save" -> {
                        if (plugin.getCitizenPathManager().saveAndStart(citizen)) send(player, "&aRoute saved to paths.yml. Citizen patrol started automatically!");
                        else send(player, "&cRoute needs at least two recorded positions. Use start, walk, stop, then save.");
                    }
                    case "pause" -> send(player, plugin.getCitizenPathManager().pause(citizen) ? "&ePatrol paused." : "&cNo saved route for this citizen.");
                    case "resume" -> send(player, plugin.getCitizenPathManager().resume(citizen) ? "&aPatrol resumed." : "&cNo saved route for this citizen.");
                    case "halt" -> send(player, plugin.getCitizenPathManager().halt(citizen) ? "&ePatrol halted. Saved route remains in paths.yml." : "&cThis citizen has no active patrol.");
                    default -> send(player, "&cUnknown path action.");
                }
                return true;
            }
            case "remove" -> {
                if (args.length < 2) { send(sender, "&cUsage: /aic remove <UUID>"); return true; }
                UUID id;
                try { id = UUID.fromString(args[1]); } catch (IllegalArgumentException ex) { send(sender, "&cInvalid UUID."); return true; }
                Citizen citizen = manager.getCitizen(id);
                if (citizen == null) { send(sender, "&cCitizen not found."); return true; }
                plugin.getCitizenPathManager().removeCitizen(citizen);
                plugin.getCitizenNpcManager().removeNpc(id);
                plugin.getMessageManager().removeCitizen(id);
                manager.removeCitizen(id);
                send(sender, "&aCitizen removed successfully!");
                return true;
            }
            case "list" -> {
                send(sender, "&eCitizens:");
                if (manager.getCitizens().isEmpty()) { send(sender, "&7No citizens found."); return true; }
                for (Citizen citizen : manager.getCitizens()) send(sender, "&f" + citizen.getName() + " &7- &f" + citizen.getId());
                return true;
            }
            case "count" -> { send(sender, "&eCitizen count: &f" + manager.getCitizenCount()); return true; }
            default -> { send(sender, "&cUnknown command. Use /aic"); return true; }
        }
    }
}