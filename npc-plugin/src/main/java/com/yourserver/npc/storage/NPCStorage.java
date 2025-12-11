package com.yourserver.npc.storage;

import com.google.gson.*;
import com.yourserver.npc.NPCPlugin;
import com.yourserver.npc.model.NPC;
import com.yourserver.npc.model.NPCEquipment;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.io.*;
import java.util.*;

/**
 * Enhanced storage with complete pose persistence.
 * FIXED: Added showSecondLayer field to serialization/deserialization.
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

                NPCEquipment equipment = obj.has("equipment")
                        ? parseEquipment(obj.getAsJsonObject("equipment"))
                        : new NPCEquipment();

                NPC npc = new NPC(id, name, uuid, entityId, location,
                        skinTexture, skinSignature, action, hologramLines, pose, equipment);

                npcs.add(npc);
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load NPCs: " + e.getMessage());
            e.printStackTrace();
        }

        return npcs;
    }


    /**
     * Parses equipment from JSON.
     */
    private NPCEquipment parseEquipment(JsonObject obj) {
        if (obj == null || obj.isJsonNull()) {
            return new NPCEquipment();
        }

        NPCEquipment.Builder builder = NPCEquipment.builder();

        if (obj.has("mainHand") && !obj.get("mainHand").isJsonNull()) {
            builder.mainHand(parseItemStack(obj.getAsJsonObject("mainHand")));
        }
        if (obj.has("offHand") && !obj.get("offHand").isJsonNull()) {
            builder.offHand(parseItemStack(obj.getAsJsonObject("offHand")));
        }
        if (obj.has("helmet") && !obj.get("helmet").isJsonNull()) {
            builder.helmet(parseItemStack(obj.getAsJsonObject("helmet")));
        }
        if (obj.has("chestplate") && !obj.get("chestplate").isJsonNull()) {
            builder.chestplate(parseItemStack(obj.getAsJsonObject("chestplate")));
        }
        if (obj.has("leggings") && !obj.get("leggings").isJsonNull()) {
            builder.leggings(parseItemStack(obj.getAsJsonObject("leggings")));
        }
        if (obj.has("boots") && !obj.get("boots").isJsonNull()) {
            builder.boots(parseItemStack(obj.getAsJsonObject("boots")));
        }

        return builder.build();
    }

    /**
     * Parses ItemStack from JSON (simple version).
     */
    private ItemStack parseItemStack(JsonObject obj) {
        try {
            Material material = Material.valueOf(obj.get("material").getAsString());
            int amount = obj.has("amount") ? obj.get("amount").getAsInt() : 1;
            return new ItemStack(material, amount);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse item: " + e.getMessage());
            return null;
        }
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

            // Save pose (now includes showSecondLayer)
            obj.add("pose", serializePose(npc.getPose()));

            if (npc.getEquipment().hasEquipment()) {
                obj.add("equipment", serializeEquipment(npc.getEquipment()));
            }

            array.add(obj);
        }

        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(array, writer);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to save NPCs: " + e.getMessage());
        }
    }

    /**
     * Deserializes an ItemStack from Base64 string.
     */
    private ItemStack deserializeItem(String data) {
        try {
            byte[] bytes = Base64.getDecoder().decode(data);
            return ItemStack.deserializeBytes(bytes);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to deserialize item: " + e.getMessage());
            return null;
        }
    }

    /**
     * Serializes equipment to JSON.
     */
    private JsonObject serializeEquipment(NPCEquipment equipment) {
        JsonObject obj = new JsonObject();

        if (equipment.getMainHand() != null) {
            obj.add("mainHand", serializeItemStack(equipment.getMainHand()));
        }
        if (equipment.getOffHand() != null) {
            obj.add("offHand", serializeItemStack(equipment.getOffHand()));
        }
        if (equipment.getHelmet() != null) {
            obj.add("helmet", serializeItemStack(equipment.getHelmet()));
        }
        if (equipment.getChestplate() != null) {
            obj.add("chestplate", serializeItemStack(equipment.getChestplate()));
        }
        if (equipment.getLeggings() != null) {
            obj.add("leggings", serializeItemStack(equipment.getLeggings()));
        }
        if (equipment.getBoots() != null) {
            obj.add("boots", serializeItemStack(equipment.getBoots()));
        }

        return obj;
    }

    /**
     * Serializes ItemStack to JSON (simple version).
     */
    private JsonObject serializeItemStack(ItemStack item) {
        JsonObject obj = new JsonObject();
        obj.addProperty("material", item.getType().name());
        obj.addProperty("amount", item.getAmount());
        return obj;
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

    /**
     * Parses pose from JSON.
     * FIXED: Added showSecondLayer field with backward compatibility.
     */
    private NPC.NPCPose parsePose(JsonObject obj) {
        // Get all rotation values
        float headPitch = obj.get("headPitch").getAsFloat();
        float headYaw = obj.get("headYaw").getAsFloat();
        float headRoll = obj.get("headRoll").getAsFloat();
        float bodyPitch = obj.get("bodyPitch").getAsFloat();
        float bodyYaw = obj.get("bodyYaw").getAsFloat();
        float bodyRoll = obj.get("bodyRoll").getAsFloat();
        float rightArmPitch = obj.get("rightArmPitch").getAsFloat();
        float rightArmYaw = obj.get("rightArmYaw").getAsFloat();
        float rightArmRoll = obj.get("rightArmRoll").getAsFloat();
        float leftArmPitch = obj.get("leftArmPitch").getAsFloat();
        float leftArmYaw = obj.get("leftArmYaw").getAsFloat();
        float leftArmRoll = obj.get("leftArmRoll").getAsFloat();
        float rightLegPitch = obj.get("rightLegPitch").getAsFloat();
        float rightLegYaw = obj.get("rightLegYaw").getAsFloat();
        float rightLegRoll = obj.get("rightLegRoll").getAsFloat();
        float leftLegPitch = obj.get("leftLegPitch").getAsFloat();
        float leftLegYaw = obj.get("leftLegYaw").getAsFloat();
        float leftLegRoll = obj.get("leftLegRoll").getAsFloat();

        // Get showSecondLayer with default value (true) for backward compatibility
        boolean showSecondLayer = obj.has("showSecondLayer")
                ? obj.get("showSecondLayer").getAsBoolean()
                : true; // Default to true for existing NPCs

        return new NPC.NPCPose(
                headPitch, headYaw, headRoll,
                bodyPitch, bodyYaw, bodyRoll,
                rightArmPitch, rightArmYaw, rightArmRoll,
                leftArmPitch, leftArmYaw, leftArmRoll,
                rightLegPitch, rightLegYaw, rightLegRoll,
                leftLegPitch, leftLegYaw, leftLegRoll,
                showSecondLayer
        );
    }

    /**
     * Serializes pose to JSON.
     * FIXED: Added showSecondLayer field.
     */
    private JsonObject serializePose(NPC.NPCPose pose) {
        JsonObject obj = new JsonObject();

        // Serialize all rotation values
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

        // Add showSecondLayer field (NEW!)
        obj.addProperty("showSecondLayer", pose.isShowSecondLayer());

        return obj;
    }
}