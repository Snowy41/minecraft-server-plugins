package com.yourserver.npc.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Represents a real player NPC with skin.
 */
public class NPC {

    private final String id;
    private final String name;
    private final UUID uuid;
    private final int entityId;
    private final Location location;
    private final Action action;
    private final List<String> hologramLines;

    private String skinTexture;
    private String skinSignature;
    private List<Integer> hologramEntityIds;

    public NPC(@NotNull String id, @NotNull String name, @NotNull Location location, @NotNull Action action) {
        this.id = id;
        this.name = name;
        this.uuid = UUID.randomUUID();
        this.entityId = generateEntityId();
        this.location = location;
        this.action = action;
        this.hologramLines = new ArrayList<>();
        this.hologramEntityIds = new ArrayList<>();
    }

    // Full constructor for loading from storage
    public NPC(@NotNull String id, @NotNull String name, @NotNull UUID uuid, int entityId,
               @NotNull Location location, @Nullable String skinTexture, @Nullable String skinSignature,
               @NotNull Action action, @NotNull List<String> hologramLines) {
        this.id = id;
        this.name = name;
        this.uuid = uuid;
        this.entityId = entityId;
        this.location = location;
        this.skinTexture = skinTexture;
        this.skinSignature = skinSignature;
        this.action = action;
        this.hologramLines = new ArrayList<>(hologramLines);
        this.hologramEntityIds = new ArrayList<>();
    }

    public void setSkin(@Nullable String texture, @Nullable String signature) {
        this.skinTexture = texture;
        this.skinSignature = signature;
    }

    public void setHologramEntityIds(@NotNull List<Integer> ids) {
        this.hologramEntityIds = new ArrayList<>(ids);
    }

    public void addHologramLine(@NotNull String line) {
        hologramLines.add(line);
    }

    public void clearHologramLines() {
        hologramLines.clear();
    }

    public boolean hasSkin() {
        return skinTexture != null && !skinTexture.isEmpty();
    }

    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public UUID getUuid() { return uuid; }
    public int getEntityId() { return entityId; }
    public Location getLocation() { return location.clone(); }
    public Action getAction() { return action; }
    public List<String> getHologramLines() { return new ArrayList<>(hologramLines); }
    public String getSkinTexture() { return skinTexture; }
    public String getSkinSignature() { return skinSignature; }
    public List<Integer> getHologramEntityIds() { return new ArrayList<>(hologramEntityIds); }

    private static int generateEntityId() {
        return (int) (Math.random() * Integer.MAX_VALUE);
    }

    /**
     * Represents an action that can be performed when clicking an NPC.
     */
    public static class Action {
        private final ActionType type;
        private final String data;
        private Consumer<Player> customHandler;

        public Action(@NotNull ActionType type, @NotNull String data) {
            this.type = type;
            this.data = data;
        }

        public Action(@NotNull Consumer<Player> customHandler) {
            this.type = ActionType.CUSTOM;
            this.data = "";
            this.customHandler = customHandler;
        }

        public void execute(@NotNull Plugin plugin, @NotNull Player player) {
            switch (type) {
                case TELEPORT -> handleTeleport(player);
                case COMMAND -> handleCommand(player);
                case GUI -> handleGUI(plugin, player);
                case MESSAGE -> handleMessage(player);
                case SERVER -> handleServer(player);
                case CUSTOM -> customHandler.accept(player);
            }
        }

        private void handleTeleport(Player player) {
            try {
                String[] parts = data.split(",");
                Location loc = new Location(
                        Bukkit.getWorld(parts[0]),
                        Double.parseDouble(parts[1]),
                        Double.parseDouble(parts[2]),
                        Double.parseDouble(parts[3]),
                        parts.length > 4 ? Float.parseFloat(parts[4]) : 0f,
                        parts.length > 5 ? Float.parseFloat(parts[5]) : 0f
                );
                player.teleport(loc);
            } catch (Exception e) {
                player.sendMessage("§cInvalid teleport location!");
            }
        }

        private void handleCommand(Player player) {
            String command = data.replace("{player}", player.getName());
            player.performCommand(command);
        }

        private void handleGUI(Plugin plugin, Player player) {
            // Call other plugin's GUI system
            Plugin target = Bukkit.getPluginManager().getPlugin("LobbyPlugin");
            if (target != null) {
                try {
                    var method = target.getClass().getMethod("getGuiManager");
                    Object guiManager = method.invoke(target);

                    String guiType = data.toLowerCase();
                    String methodName = switch (guiType) {
                        case "game_selector", "games" -> "openGameSelector";
                        case "cosmetics" -> "openCosmetics";
                        case "stats" -> "openStats";
                        default -> null;
                    };

                    if (methodName != null) {
                        var openMethod = guiManager.getClass().getMethod(methodName, Player.class);
                        openMethod.invoke(guiManager, player);
                    }
                } catch (Exception e) {
                    player.sendMessage("§cFailed to open GUI!");
                }
            }
        }

        private void handleMessage(Player player) {
            player.sendMessage(data);
        }

        private void handleServer(Player player) {
            player.sendMessage("§eConnecting to " + data + "...");
            // TODO: BungeeCord/Velocity server switch
        }

        public ActionType getType() { return type; }
        public String getData() { return data; }
    }

    public enum ActionType {
        TELEPORT,
        COMMAND,
        GUI,
        MESSAGE,
        SERVER,
        CUSTOM
    }
}