package com.yourserver.npc.api;

import com.yourserver.npc.manager.NPCManager;
import com.yourserver.npc.model.NPC;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import java.util.function.Consumer;

public class NPCAPI {

    private final NPCManager npcManager;

    public NPCAPI(NPCManager npcManager) {
        this.npcManager = npcManager;
    }

    /**
     * Creates an NPC with a custom click handler.
     *
     * Example:
     * ```java
     * api.createNPC("my_npc", "Steve", location, player -> {
     *     player.sendMessage("Hello!");
     * });
     * ```
     */
    public void createNPC(String id, String playerName, Location location,
                          Consumer<Player> onClickHandler) {
        NPC.Action action = new NPC.Action(onClickHandler);
        npcManager.createNPC(id, playerName, location, action);
    }

    /**
     * Creates an NPC that opens a GUI.
     */
    public void createGUINPC(String id, String playerName, Location location, String guiType) {
        NPC.Action action = new NPC.Action(NPC.ActionType.GUI, guiType);
        npcManager.createNPC(id, playerName, location, action);
    }

    /**
     * Creates an NPC that runs a command.
     */
    public void createCommandNPC(String id, String playerName, Location location, String command) {
        NPC.Action action = new NPC.Action(NPC.ActionType.COMMAND, command);
        npcManager.createNPC(id, playerName, location, action);
    }

    /**
     * Removes an NPC.
     */
    public void removeNPC(String id) {
        npcManager.removeNPC(id);
    }

    /**
     * Gets an NPC by ID.
     */
    public NPC getNPC(String id) {
        return npcManager.getNPC(id);
    }
}