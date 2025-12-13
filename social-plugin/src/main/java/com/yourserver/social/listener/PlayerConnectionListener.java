package com.yourserver.social.listener;

import com.yourserver.social.SocialPlugin;
import com.yourserver.social.manager.FriendManager;
import com.yourserver.social.manager.PartyManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerConnectionListener implements Listener {
    private final SocialPlugin plugin;
    private final FriendManager friendManager;
    private final PartyManager partyManager;

    public PlayerConnectionListener(SocialPlugin plugin, FriendManager friendManager, PartyManager partyManager) {
        this.plugin = plugin;
        this.friendManager = friendManager;
        this.partyManager = partyManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // TODO: Handle friend online notifications
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // TODO: Handle friend offline notifications
    }
}