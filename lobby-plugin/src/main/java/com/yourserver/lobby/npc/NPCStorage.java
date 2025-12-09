package com.yourserver.lobby.npc;

import com.yourserver.lobby.LobbyPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles saving and loading NPCs from npcs.yml
 */
public class NPCStorage {

    private final LobbyPlugin plugin;
    private final File npcsFile;
    private FileConfiguration npcsConfig;

    public NPCStorage(LobbyPlugin plugin) {
        this.plugin = plugin;
        this.npcsFile = new File(plugin.getDataFolder(), "npcs.yml");

        // Create file if it doesn't exist
        if (!npcsFile.exists()) {
            try {
                npcsFile.getParentFile().mkdirs();
                npcsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create npcs.yml: " + e.getMessage());
            }
        }

        this.npcsConfig = YamlConfiguration.loadConfiguration(npcsFile);
    }

    /**
     * Saves all NPCs to npcs.yml
     */
    public void saveNPCs(List<CustomNPC> npcs) {
        // Clear existing data
        npcsConfig = new YamlConfiguration();

        // Save each NPC
        for (CustomNPC npc : npcs) {
            String path = "npcs." + npc.getId();

            npcsConfig.set(path + ".name", npc.getName());
            npcsConfig.set(path + ".location.world", npc.getLocation().getWorld().getName());
            npcsConfig.set(path + ".location.x", npc.getLocation().getX());
            npcsConfig.set(path + ".location.y", npc.getLocation().getY());
            npcsConfig.set(path + ".location.z", npc.getLocation().getZ());
            npcsConfig.set(path + ".location.yaw", npc.getLocation().getYaw());
            npcsConfig.set(path + ".location.pitch", npc.getLocation().getPitch());

            npcsConfig.set(path + ".skin.texture", npc.getSkinTexture());
            npcsConfig.set(path + ".skin.signature", npc.getSkinSignature());

            npcsConfig.set(path + ".action.type", npc.getAction().getType().name());
            npcsConfig.set(path + ".action.data", npc.getAction().getData());

            npcsConfig.set(path + ".hologram", npc.getHologramLines());
        }

        // Save to file
        try {
            npcsConfig.save(npcsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save npcs.yml: " + e.getMessage());
        }
    }

    /**
     * Loads all NPCs from npcs.yml
     */
    public List<CustomNPC> loadNPCs() {
        List<CustomNPC> npcs = new ArrayList<>();

        npcsConfig = YamlConfiguration.loadConfiguration(npcsFile);

        ConfigurationSection npcsSection = npcsConfig.getConfigurationSection("npcs");
        if (npcsSection == null) {
            return npcs;
        }

        for (String npcId : npcsSection.getKeys(false)) {
            String path = "npcs." + npcId;

            try {
                String name = npcsConfig.getString(path + ".name");

                String worldName = npcsConfig.getString(path + ".location.world");
                double x = npcsConfig.getDouble(path + ".location.x");
                double y = npcsConfig.getDouble(path + ".location.y");
                double z = npcsConfig.getDouble(path + ".location.z");
                float yaw = (float) npcsConfig.getDouble(path + ".location.yaw");
                float pitch = (float) npcsConfig.getDouble(path + ".location.pitch");

                Location location = new Location(Bukkit.getWorld(worldName), x, y, z, yaw, pitch);

                String skinTexture = npcsConfig.getString(path + ".skin.texture");
                String skinSignature = npcsConfig.getString(path + ".skin.signature");

                String actionTypeStr = npcsConfig.getString(path + ".action.type");
                String actionData = npcsConfig.getString(path + ".action.data");
                CustomNPC.NPCAction.ActionType actionType = CustomNPC.NPCAction.ActionType.valueOf(actionTypeStr);
                CustomNPC.NPCAction action = new CustomNPC.NPCAction(actionType, actionData);

                List<String> hologramLines = npcsConfig.getStringList(path + ".hologram");

                CustomNPC npc = new CustomNPC(
                        npcId,
                        name,
                        location,
                        skinTexture,
                        skinSignature,
                        action,
                        hologramLines
                );

                npcs.add(npc);

            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load NPC: " + npcId + " - " + e.getMessage());
            }
        }

        return npcs;
    }
}