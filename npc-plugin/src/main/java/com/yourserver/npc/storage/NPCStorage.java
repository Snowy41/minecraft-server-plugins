package com.yourserver.npc.storage;

import com.google.gson.*;
import com.yourserver.npc.NPCPlugin;
import com.yourserver.npc.model.NPC;
import com.yourserver.npc.model.NPCEquipment;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.*;
import java.util.*;

/**
 * Enhanced storage with complete pose persistence and proper equipment serialization.
 * FIXED: Equipment now properly serializes with all NBT data, enchantments, etc.
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

                // FIXED: Proper equipment deserialization
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

    private NPCEquipment parseEquipment(JsonObject obj) {
        if (obj == null || obj.isJsonNull()) {
            return new NPCEquipment();
        }

        NPCEquipment.Builder builder = NPCEquipment.builder();

        if (obj.has("mainHand") && !obj.get("mainHand").isJsonNull()) {
            builder.mainHand(parseItemStack(obj.get("mainHand").getAsString()));
        }
        if (obj.has("offHand") && !obj.get("offHand").isJsonNull()) {
            builder.offHand(parseItemStack(obj.get("offHand").getAsString()));
        }
        if (obj.has("helmet") && !obj.get("helmet").isJsonNull()) {
            builder.helmet(parseItemStack(obj.get("helmet").getAsString()));
        }
        if (obj.has("chestplate") && !obj.get("chestplate").isJsonNull()) {
            builder.chestplate(parseItemStack(obj.get("chestplate").getAsString()));
        }
        if (obj.has("leggings") && !obj.get("leggings").isJsonNull()) {
            builder.leggings(parseItemStack(obj.get("leggings").getAsString()));
        }
        if (obj.has("boots") && !obj.get("boots").isJsonNull()) {
            builder.boots(parseItemStack(obj.get("boots").getAsString()));
        }

        return builder.build();
    }

    private ItemStack deserializeItemStack(String base64) {
        if (base64 == null || base64.isEmpty()) {
            return null;
        }

        try {
            byte[] data = Base64.getDecoder().decode(base64);
            ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);

            ItemStack item = (ItemStack) dataInput.readObject();

            dataInput.close();
            return item;

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to deserialize item: " + e.getMessage());
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

            // Save pose (includes showSecondLayer)
            obj.add("pose", serializePose(npc.getPose()));

            // FIXED: Proper equipment serialization
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

    private JsonObject serializeEquipment(NPCEquipment equipment) {
        JsonObject obj = new JsonObject();

        if (equipment.getMainHand() != null) {
            obj.addProperty("mainHand", serializeItemStack(equipment.getMainHand()));
        }
        if (equipment.getOffHand() != null) {
            obj.addProperty("offHand", serializeItemStack(equipment.getOffHand()));
        }
        if (equipment.getHelmet() != null) {
            obj.addProperty("helmet", serializeItemStack(equipment.getHelmet()));
        }
        if (equipment.getChestplate() != null) {
            obj.addProperty("chestplate", serializeItemStack(equipment.getChestplate()));
        }
        if (equipment.getLeggings() != null) {
            obj.addProperty("leggings", serializeItemStack(equipment.getLeggings()));
        }
        if (equipment.getBoots() != null) {
            obj.addProperty("boots", serializeItemStack(equipment.getBoots()));
        }

        return obj;
    }

    /**
     * Serializes ItemStack to Base64 string (preserves ALL data).
     */
    private String serializeItemStack(ItemStack item) {
        try {
            byte[] bytes = item.serializeAsBytes();
            return Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to serialize item: " + e.getMessage());
            return null;
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

    /**
     * Deserializes ItemStack from Base64 string.
     */
    private ItemStack parseItemStack(String data) {
        try {
            byte[] bytes = Base64.getDecoder().decode(data);
            return ItemStack.deserializeBytes(bytes);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to deserialize item: " + e.getMessage());
            return null;
        }
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
     * Parses pose from JSON with showSecondLayer field.
     */
    private NPC.NPCPose parsePose(JsonObject obj) {
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

        boolean showSecondLayer = obj.has("showSecondLayer")
                ? obj.get("showSecondLayer").getAsBoolean()
                : true;

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
     * Serializes pose to JSON with showSecondLayer field.
     */
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
        obj.addProperty("showSecondLayer", pose.isShowSecondLayer());

        return obj;
    }
}