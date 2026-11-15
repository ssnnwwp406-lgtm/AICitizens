
package com.aicitizens;

import org.bukkit.plugin.java.JavaPlugin;

import com.aicitizens.citizens.CitizenManager;
import com.aicitizens.citizens.CitizenAI;
import com.aicitizens.commands.CitizenCommand;
import com.aicitizens.listeners.CitizenProtection;

public final class AICitizens extends JavaPlugin {

    private CitizenManager citizenManager;
    private CitizenAI citizenAI;

    @Override
    public void onEnable() {

        saveDefaultConfig();

        // Initialize citizen manager
        citizenManager = new CitizenManager(this);

        // Initialize AI movement system
        citizenAI = new CitizenAI(this, citizenManager);
        citizenAI.start();

        // Register protection listener
        getServer().getPluginManager().registerEvents(
                new CitizenProtection(citizenManager), this
        );

        // Register command
        getCommand("aic").setExecutor(
                new CitizenCommand(citizenManager)
        );

        getLogger().info("AICitizens enabled successfully!");
    }

    @Override
    public void onDisable() {

        if (citizenAI != null) {
            citizenAI.cancel();
        }

        if (citizenManager != null) {
            citizenManager.shutdown();
        }

        getLogger().info("AICitizens disabled!");
    }

    public CitizenManager getCitizenManager() {
        return citizenManager;
    }
}
