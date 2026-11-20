package com.aicitizens.citizens;

import com.aicitizens.AICitizens;
import com.aicitizens.utils.SkinManager;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.npc.NPC;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class CitizenNpcManager implements Listener {
    private static final AtomicInteger NEXT_ENTITY_ID = new AtomicInteger(1_000_000_000);

    private final AICitizens plugin;
    private final Map<UUID, NpcEntry> npcs = new HashMap<>();
    private BukkitTask tabRefreshTask;

    public CitizenNpcManager(AICitizens plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        // Keep the fake player-list entries present even if another plugin refreshes TAB.
        this.tabRefreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshTabEntries, 20L, 20L);
    }

    public boolean createNpc(Citizen citizen, SkinManager.SkinData skin) {
        if (citizen == null || skin == null || !citizen.isValid() || npcs.containsKey(citizen.getId())) return false;

        org.bukkit.Location bukkitLocation = citizen.getEntity().getLocation();
        String profileName = createProfileName(citizen.getName());
        UUID npcUUID = UUID.randomUUID();

        List<TextureProperty> textures = new ArrayList<>();
        textures.add(new TextureProperty("textures", skin.getTexture(), skin.getSignature()));
        UserProfile profile = new UserProfile(npcUUID, profileName, textures);

        String tabFormat = plugin.getConfig().getString("citizens.tab-list.name-format", "&f{name}");
        Component tabName = LegacyComponentSerializer.legacyAmpersand()
                .deserialize(tabFormat.replace("{name}", citizen.getName()));

        NPC npc = new NPC(profile, NEXT_ENTITY_ID.getAndIncrement(), tabName);
        npc.setLocation(toPacketLocation(bukkitLocation));
        NpcEntry entry = new NpcEntry(npc, bukkitLocation.getWorld());
        npcs.put(citizen.getId(), entry);

        for (Player player : Bukkit.getOnlinePlayers()) showTo(player, entry);
        return true;
    }

    public void updateNpc(UUID citizenId, org.bukkit.Location location) {
        NpcEntry entry = npcs.get(citizenId);
        if (entry == null || location == null || location.getWorld() == null) return;

        if (!entry.world.equals(location.getWorld())) {
            for (Object channel : new HashSet<>(entry.npc.getChannels())) hideFromChannel(entry.npc, channel);
            entry.world = location.getWorld();
            entry.npc.setLocation(toPacketLocation(location));
            for (Player player : Bukkit.getOnlinePlayers()) showTo(player, entry);
            return;
        }
        entry.npc.updateLocation(toPacketLocation(location));
    }

    public void removeNpc(UUID citizenId) {
        NpcEntry entry = npcs.remove(citizenId);
        if (entry == null) return;
        for (Object channel : new HashSet<>(entry.npc.getChannels())) hideFromChannel(entry.npc, channel);
        entry.npc.despawnAll();
    }

    public NPC getNpc(UUID citizenId) {
        NpcEntry entry = npcs.get(citizenId);
        return entry == null ? null : entry.npc;
    }

    public boolean hasNpc(UUID citizenId) {
        return npcs.containsKey(citizenId);
    }

    private void showTo(Player player, NpcEntry entry) {
        if (player == null || !player.isOnline() || !player.getWorld().equals(entry.world)) return;
        Object channel = PacketEvents.getAPI().getPlayerManager().getChannel(player);
        if (channel == null) return;
        if (!entry.npc.hasSpawned(channel)) entry.npc.spawn(channel);
        ensureTabEntry(entry.npc, channel);
    }

    private void ensureTabEntry(NPC npc, Object channel) {
        // ADD_PLAYER creates the profile; UPDATE_LISTED explicitly marks it as
        // visible in the tab list on modern Minecraft versions (1.19.3+).
        WrapperPlayServerPlayerInfoUpdate.PlayerInfo info = npc.getModernPlayerInfoData();
        info.setListed(true);
        info.setDisplayName(npc.getTabName());
        info.setLatency(npc.getDisplayPing());

        EnumSet<WrapperPlayServerPlayerInfoUpdate.Action> actions = EnumSet.of(
                WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
                WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED,
                WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME,
                WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LATENCY
        );

        PacketEvents.getAPI().getProtocolManager().sendPacket(channel,
                new WrapperPlayServerPlayerInfoUpdate(actions, info));
    }

    private void refreshTabEntries() {
        for (NpcEntry entry : npcs.values()) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.getWorld().equals(entry.world)) continue;
                // Re-assert both the fake player spawn and its tab-list profile.
                // This helps recover if a tab plugin periodically rebuilds player info.
                showTo(player, entry);
            }
        }
    }

    private void hideFrom(Player player, NpcEntry entry) {
        if (player == null) return;
        Object channel = PacketEvents.getAPI().getPlayerManager().getChannel(player);
        if (channel != null) hideFromChannel(entry.npc, channel);
    }

    private void hideFromChannel(NPC npc, Object channel) {
        if (!npc.hasSpawned(channel)) return;
        npc.despawn(channel);
        PacketEvents.getAPI().getProtocolManager().sendPacket(channel,
                new WrapperPlayServerPlayerInfoRemove(npc.getProfile().getUUID()));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = event.getPlayer();
            for (NpcEntry entry : npcs.values()) showTo(player, entry);
        });
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        for (NpcEntry entry : npcs.values()) {
            if (player.getWorld().equals(entry.world)) showTo(player, entry);
            else hideFrom(player, entry);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Object channel = PacketEvents.getAPI().getPlayerManager().getChannel(player);
        if (channel == null) return;
        for (NpcEntry entry : npcs.values()) hideFromChannel(entry.npc, channel);
    }

    public void shutdown() {
        if (tabRefreshTask != null) tabRefreshTask.cancel();
        for (NpcEntry entry : npcs.values()) {
            for (Object channel : new HashSet<>(entry.npc.getChannels())) hideFromChannel(entry.npc, channel);
            entry.npc.despawnAll();
        }
        npcs.clear();
    }

    private Location toPacketLocation(org.bukkit.Location location) {
        return new Location(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
    }

    private String createProfileName(String name) {
        String safeName = name.replaceAll("[^A-Za-z0-9_]", "_");
        if (safeName.isBlank()) safeName = "AICitizen";
        if (safeName.length() > 16) safeName = safeName.substring(0, 16);
        return safeName;
    }

    private static class NpcEntry {
        private final NPC npc;
        private World world;

        private NpcEntry(NPC npc, World world) {
            this.npc = npc;
            this.world = world;
        }
    }
}