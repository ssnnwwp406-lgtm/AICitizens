
package com.aicitizens;

import org.bukkit.plugin.java.JavaPlugin;
import com.aicitizens.citizens.CitizenManager;
import com.aicitizens.commands.CitizenCommand;
import com.aicitizens.listeners.CitizenProtection;

public final class AICitizens extends JavaPlugin {

    private CitizenManager citizenManager;

    @Override
    public void onEnable() {

        saveDefaultConfig();

        citizenManager = new CitizenManager(this);

        getServer().getPluginManager().registerEvents(
                new CitizenProtection(citizenManager), this
        );

        getCommand("aic").setExecutor(
                new CitizenCommand(citizenManager)
        );

        getLogger().info("AICitizens enabled successfully!");
    }

    @Override
    public void onDisable() {
        if (citizenManager != null) {
            citizenManager.shutdown();
        }
    }

    public CitizenManager getCitizenManager() {
        return citizenManager;
    }
}
