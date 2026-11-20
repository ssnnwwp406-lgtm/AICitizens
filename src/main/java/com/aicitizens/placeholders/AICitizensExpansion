
package com.aicitizens.placeholders;

import com.aicitizens.AICitizens;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

public final class AICitizensExpansion extends PlaceholderExpansion {

    private final AICitizens plugin;

    public AICitizensExpansion(AICitizens plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "aicitizens";
    }

    @Override
    public String getAuthor() {
        return "AICitizens";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(Player player, String params) {

        if (plugin.getCitizenManager() == null) {
            return "0";
        }

        int citizenCount = plugin.getCitizenManager().getCitizenCount();
        int onlinePlayers = plugin.getServer().getOnlinePlayers().size();

        switch (params.toLowerCase()) {

            case "count":
            case "citizens":
                return String.valueOf(citizenCount);

            case "online":
            case "real_online":
                return String.valueOf(onlinePlayers);

            case "total_online":
            case "total":
                return String.valueOf(onlinePlayers + citizenCount);

            case "max":
                return String.valueOf(
                    plugin.getConfig().getInt("citizens.max-count", 50)
                );

            default:
                return null;
        }
    }
}
