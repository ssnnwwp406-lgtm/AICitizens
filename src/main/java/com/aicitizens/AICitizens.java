package com.aicitizens;

import org.bukkit.plugin.java.JavaPlugin;
import com.aicitizens.citizens.CitizenManager;
import com.aicitizens.citizens.CitizenMessageManager;
import com.aicitizens.citizens.CitizenNpcManager;
import com.aicitizens.citizens.CitizenPathManager;
import com.aicitizens.commands.CitizenCommand;
import com.aicitizens.listeners.CitizenProtection;
import com.aicitizens.utils.SkinManager;
import com.github.retrooper.packetevents.PacketEvents;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;

public final class AICitizens extends JavaPlugin {
    private CitizenManager citizenManager;
    private CitizenMessageManager messageManager;
    private CitizenNpcManager citizenNpcManager;
    private CitizenPathManager citizenPathManager;
    private SkinManager skinManager;

    @Override public void onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().load();
    }

    @Override public void onEnable() {
        PacketEvents.getAPI().init();
        saveDefaultConfig();
        reloadConfig();
        citizenManager = new CitizenManager(this);
        messageManager = new CitizenMessageManager(this);
        citizenNpcManager = new CitizenNpcManager(this);
        skinManager = new SkinManager();
        citizenPathManager = new CitizenPathManager(this, citizenManager, citizenNpcManager);
        getServer().getPluginManager().registerEvents(new CitizenProtection(this, citizenManager), this);
        if (getCommand("aic") == null) {
            getLogger().severe("Command 'aic' is missing from plugin.yml!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getCommand("aic").setExecutor(new CitizenCommand(this, citizenManager));
        citizenManager.restoreSavedCitizens();
        getLogger().info("AICitizens enabled successfully!");
    }

    @Override public void onDisable() {
        if (citizenPathManager != null) citizenPathManager.shutdown();
        if (messageManager != null) messageManager.clear();
        if (citizenNpcManager != null) citizenNpcManager.shutdown();
        if (citizenManager != null) citizenManager.shutdown();
        if (skinManager != null) skinManager.clearCache();
        if (PacketEvents.getAPI() != null) PacketEvents.getAPI().terminate();
        getLogger().info("AICitizens disabled!");
    }

    public CitizenManager getCitizenManager() { return citizenManager; }
    public CitizenMessageManager getMessageManager() { return messageManager; }
    public CitizenNpcManager getCitizenNpcManager() { return citizenNpcManager; }
    public CitizenPathManager getCitizenPathManager() { return citizenPathManager; }
    public SkinManager getSkinManager() { return skinManager; }
}