package com.yourserver.social.gui;

import com.yourserver.social.manager.ClanManager;
import com.yourserver.social.manager.FriendManager;
import com.yourserver.social.manager.PartyManager;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

public class GUIManager implements Listener {
    public GUIManager(Object plugin, FriendManager friendManager, PartyManager partyManager, ClanManager clanManager) {
        // TODO: Implement GUIs
    }

    public void openFriendsGUI(Player player) {
        player.sendMessage("§cFriends GUI not yet implemented!");
    }
}