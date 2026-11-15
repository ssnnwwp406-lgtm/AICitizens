
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

        reloadConfig();

        citizenManager = new CitizenManager(this);

        citizenAI = new CitizenAI(this, citizenManager);

        if (getConfig().getBoolean("movement.enabled", true)) {
            citizenAI.start();
        }

        getServer().getPluginManager().registerEvents(
                new CitizenProtection(this, citizenManager),
                this
        );

        if (getCommand("aic") != null) {
            getCommand("aic").setExecutor(
                    new CitizenCommand(citizenManager)
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

        if (citizenManager != null) {
            citizenManager.shutdown();
        }

        getLogger().info("AICitizens disabled!");
    }

    public CitizenManager getCitizenManager() {
        return citizenManager;
    }
}
