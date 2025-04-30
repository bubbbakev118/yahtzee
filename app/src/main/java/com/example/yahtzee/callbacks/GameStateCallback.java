package com.example.yahtzee.callbacks;

import com.example.yahtzee.model.*;
import java.util.*;

public interface GameStateCallback {
    void onRoundStarted(Round round, Player firstPlayer);
    void onTurnStarted(Player player, int rollsLeft);
    void onDiceRolled(List<Integer> diceValues, List<Integer> heldIndices);
    void onDiceHeld(List<Integer> heldIndices);
    void onScoreSelected(ScoreCategory category, int score, Round round);
    void onRoundComplete(Round round, Map<Player, Integer> roundScores);
    void onGameOver(Player winner, Map<Player, Integer> finalScores);
    void onComputerTurnAnnouncement(List<String> announcements);
    void onComputerRollRequest();
    void onError(String message);
    
    /**
     * Called when the computer's turn is complete to clean up any dialogs
     */
    void onComputerTurnEnd();
    
    /**
     * Called when the computer is waiting for user to press Next before continuing
     * @param stepDescription A short description of what will happen in the next step
     * @param callback A callback to be invoked when the user is ready to continue
     */
    void onComputerTurnWaitingForUser(String stepDescription, Runnable callback);
}
