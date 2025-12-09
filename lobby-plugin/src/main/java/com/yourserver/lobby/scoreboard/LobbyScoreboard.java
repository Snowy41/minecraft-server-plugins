package com.yourserver.lobby.scoreboard;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Flicker-free scoreboard implementation using team-based approach.
 * Each line is a team with an invisible entry name.
 */
public class LobbyScoreboard {

    private final Player player;
    private final Scoreboard scoreboard;
    private final Objective objective;
    private final List<Team> teams;

    public LobbyScoreboard(@NotNull Player player, @NotNull Component title) {
        this.player = player;

        // Create new scoreboard
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        this.scoreboard = manager.getNewScoreboard();

        // Create objective
        this.objective = scoreboard.registerNewObjective(
                "lobby",
                Criteria.DUMMY,
                title
        );
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        // Initialize teams list
        this.teams = new ArrayList<>();

        // Set the scoreboard for the player
        player.setScoreboard(scoreboard);
    }

    /**
     * Updates the scoreboard title.
     *
     * @param title The new title
     */
    public void updateTitle(@NotNull Component title) {
        objective.displayName(title);
    }

    /**
     * Updates all scoreboard lines.
     * Uses team-based approach to prevent flickering.
     *
     * @param lines The lines to display (top to bottom)
     */
    public void updateLines(@NotNull List<Component> lines) {
        int size = lines.size();

        // Create or update teams for each line
        for (int i = 0; i < size; i++) {
            int score = size - i; // Reverse order for display
            String entry = getColorCode(i); // Unique entry for each line

            // Get or create team
            Team team;
            if (i < teams.size()) {
                team = teams.get(i);
            } else {
                team = scoreboard.registerNewTeam("line_" + i);
                team.addEntry(entry);
                teams.add(team);
            }

            // Set team prefix (the actual line content)
            team.prefix(lines.get(i));

            // Set score (position on scoreboard)
            objective.getScore(entry).setScore(score);
        }

        // Remove extra teams if lines decreased
        while (teams.size() > size) {
            Team team = teams.remove(teams.size() - 1);
            team.unregister();
        }
    }

    /**
     * Gets a unique color code for each line (invisible entry names).
     * Uses color codes to create unique entries.
     *
     * @param index The line index
     * @return A unique color code string
     */
    private String getColorCode(int index) {
        // Use legacy color codes to create unique invisible entries
        // §0 to §f gives us 16 possible unique entries
        return "§" + Integer.toHexString(index);
    }

    /**
     * Removes this scoreboard from the player.
     */
    public void remove() {
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    public Player getPlayer() {
        return player;
    }
}