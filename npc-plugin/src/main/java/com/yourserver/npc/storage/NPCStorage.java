package com.yourserver.npc.storage;

import com.google.gson.*;
import com.yourserver.npc.NPCPlugin;
import com.yourserver.npc.model.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.io.*;
import java.util.*;

/**
 * Enhanced storage with pose persistence.
 */
public class NPCStorage {

    private final NPCPlugin plugin;
    private final File file;
    private final Gson gson;

    public NPCStorage(NPCPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "npcs.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();

        if (!file.exists()) {
            file.getParentFile().mkdirs();
            try {
                file.createNewFile();
                // Write empty array
                try (FileWriter writer = new FileWriter(file)) {
                    writer.write("[]");
                }
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create npcs.json: " + e.getMessage());
            }
        }
    }

    public List<NPC> loadNPCs() {
        List<NPC> npcs = new ArrayList<>();

        try (FileReader reader = new FileReader(file)) {
            JsonArray array = JsonParser.parseReader(reader).getAsJsonArray();

            for (JsonElement element : array) {
                JsonObject obj = element.getAsJsonObject();

                String id = obj.get("id").getAsString();
                String name = obj.get("name").getAsString();
                UUID uuid = UUID.fromString(obj.get("uuid").getAsString());
                int entityId = obj.get("entityId").getAsInt();

                Location location = parseLocation(obj.getAsJsonObject("location"));

                String skinTexture = obj.has("skinTexture") ? obj.get("skinTexture").getAsString() : null;
                String skinSignature = obj.has("skinSignature") ? obj.get("skinSignature").getAsString() : null;

                NPC.Action action = parseAction(obj.getAsJsonObject("action"));

                List<String> hologramLines = new ArrayList<>();
                if (obj.has("hologram")) {
                    obj.getAsJsonArray("hologram").forEach(e -> hologramLines.add(e.getAsString()));
                }

                // Load pose (with defaults if missing)
                NPC.NPCPose pose = obj.has("pose")
                        ? parsePose(obj.getAsJsonObject("pose"))
                        : new NPC.NPCPose();

                NPC npc = new NPC(id, name, uuid, entityId, location,
                        skinTexture, skinSignature, action, hologramLines, pose);

                npcs.add(npc);
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load NPCs: " + e.getMessage());
            e.printStackTrace();
        }

        return npcs;
    }

    public void saveNPCs(List<NPC> npcs) {
        JsonArray array = new JsonArray();

        for (NPC npc : npcs) {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", npc.getId());
            obj.addProperty("name", npc.getName());
            obj.addProperty("uuid", npc.getUuid().toString());
            obj.addProperty("entityId", npc.getEntityId());
            obj.add("location", serializeLocation(npc.getLocation()));

            if (npc.hasSkin()) {
                obj.addProperty("skinTexture", npc.getSkinTexture());
                obj.addProperty("skinSignature", npc.getSkinSignature());
            }

            obj.add("action", serializeAction(npc.getAction()));

            JsonArray hologram = new JsonArray();
            npc.getHologramLines().forEach(hologram::add);
            obj.add("hologram", hologram);

            // Save pose
            obj.add("pose", serializePose(npc.getPose()));

            array.add(obj);
        }

        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(array, writer);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to save NPCs: " + e.getMessage());
        }
    }

    private Location parseLocation(JsonObject obj) {
        return new Location(
                Bukkit.getWorld(obj.get("world").getAsString()),
                obj.get("x").getAsDouble(),
                obj.get("y").getAsDouble(),
                obj.get("z").getAsDouble(),
                obj.get("yaw").getAsFloat(),
                obj.get("pitch").getAsFloat()
        );
    }

    private JsonObject serializeLocation(Location loc) {
        JsonObject obj = new JsonObject();
        obj.addProperty("world", loc.getWorld().getName());
        obj.addProperty("x", loc.getX());
        obj.addProperty("y", loc.getY());
        obj.addProperty("z", loc.getZ());
        obj.addProperty("yaw", loc.getYaw());
        obj.addProperty("pitch", loc.getPitch());
        return obj;
    }

    private NPC.Action parseAction(JsonObject obj) {
        NPC.ActionType type = NPC.ActionType.valueOf(obj.get("type").getAsString());
        String data = obj.get("data").getAsString();
        return new NPC.Action(type, data);
    }

    private JsonObject serializeAction(NPC.Action action) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", action.getType().name());
        obj.addProperty("data", action.getData());
        return obj;
    }

    private NPC.NPCPose parsePose(JsonObject obj) {
        return new NPC.NPCPose(
                obj.get("headPitch").getAsFloat(),
                obj.get("headYaw").getAsFloat(),
                obj.get("headRoll").getAsFloat(),
                obj.get("bodyPitch").getAsFloat(),
                obj.get("bodyYaw").getAsFloat(),
                obj.get("bodyRoll").getAsFloat(),
                obj.get("rightArmPitch").getAsFloat(),
                obj.get("rightArmYaw").getAsFloat(),
                obj.get("rightArmRoll").getAsFloat(),
                obj.get("leftArmPitch").getAsFloat(),
                obj.get("leftArmYaw").getAsFloat(),
                obj.get("leftArmRoll").getAsFloat(),
                obj.get("rightLegPitch").getAsFloat(),
                obj.get("rightLegYaw").getAsFloat(),
                obj.get("rightLegRoll").getAsFloat(),
                obj.get("leftLegPitch").getAsFloat(),
                obj.get("leftLegYaw").getAsFloat(),
                obj.get("leftLegRoll").getAsFloat()
        );
    }

    private JsonObject serializePose(NPC.NPCPose pose) {
        JsonObject obj = new JsonObject();
        obj.addProperty("headPitch", pose.getHeadPitch());
        obj.addProperty("headYaw", pose.getHeadYaw());
        obj.addProperty("headRoll", pose.getHeadRoll());
        obj.addProperty("bodyPitch", pose.getBodyPitch());
        obj.addProperty("bodyYaw", pose.getBodyYaw());
        obj.addProperty("bodyRoll", pose.getBodyRoll());
        obj.addProperty("rightArmPitch", pose.getRightArmPitch());
        obj.addProperty("rightArmYaw", pose.getRightArmYaw());
        obj.addProperty("rightArmRoll", pose.getRightArmRoll());
        obj.addProperty("leftArmPitch", pose.getLeftArmPitch());
        obj.addProperty("leftArmYaw", pose.getLeftArmYaw());
        obj.addProperty("leftArmRoll", pose.getLeftArmRoll());
        obj.addProperty("rightLegPitch", pose.getRightLegPitch());
        obj.addProperty("rightLegYaw", pose.getRightLegYaw());
        obj.addProperty("rightLegRoll", pose.getRightLegRoll());
        obj.addProperty("leftLegPitch", pose.getLeftLegPitch());
        obj.addProperty("leftLegYaw", pose.getLeftLegYaw());
        obj.addProperty("leftLegRoll", pose.getLeftLegRoll());
        return obj;
    }
}