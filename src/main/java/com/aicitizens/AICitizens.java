
package com.aicitizens;

import org.bukkit.plugin.java.JavaPlugin;

import com.aicitizens.citizens.CitizenManager;
import com.aicitizens.citizens.CitizenAI;
import com.aicitizens.citizens.CitizenMessageManager;
import com.aicitizens.commands.CitizenCommand;
import com.aicitizens.listeners.CitizenProtection;

import com.github.retrooper.packetevents.PacketEvents;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;

public final class AICitizens extends JavaPlugin {

    private CitizenManager citizenManager;
    private CitizenAI citizenAI;
    private CitizenMessageManager messageManager;

    @Override
    public void onLoad() {
        PacketEvents.setAPI(
                SpigotPacketEventsBuilder.build(this)
        );

        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {

        PacketEvents.getAPI().init();

        saveDefaultConfig();
        reloadConfig();

        citizenManager = new CitizenManager(this);

        messageManager = new CitizenMessageManager(this);

        citizenAI = new CitizenAI(
                this,
                citizenManager,
                messageManager
        );

        if (getConfig().getBoolean("movement.enabled", true)) {
            citizenAI.start();
        }

        getServer().getPluginManager().registerEvents(
                new CitizenProtection(this, citizenManager),
                this
        );

        if (getCommand("aic") != null) {

            getCommand("aic").setExecutor(
                    new CitizenCommand(this, citizenManager)
            );

        } else {

            getLogger().severe(
                    "Command 'aic' is missing from plugin.yml!"
            );

            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getLogger().info("AICitizens enabled successfully!");
    }

    @Override
    public void onDisable() {

        if (citizenAI != null) {
            citizenAI.cancel();
        }

        if (messageManager != null) {
            messageManager.clear();
        }

        if (citizenManager != null) {
            citizenManager.shutdown();
        }

        if (PacketEvents.getAPI() != null) {
            PacketEvents.getAPI().terminate();
        }

        getLogger().info("AICitizens disabled!");
    }

    public CitizenManager getCitizenManager() {
        return citizenManager;
    }

    public CitizenMessageManager getMessageManager() {
        return messageManager;
    }
}
