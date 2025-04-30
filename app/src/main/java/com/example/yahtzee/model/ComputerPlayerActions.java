package com.example.yahtzee.model;

import java.util.List;

/**
 * Interface defining the actions that can be performed on a computer player
 * from outside the model package.
 */
public interface ComputerPlayerActions {
    /**
     * Add an explanation message about the computer's actions
     */
    void addExplanationMessage(String title, String explanation);
    
    /**
     * Format a list of dice values as a string
     */
    String formatDiceValuesString(List<Integer> diceValues);
}
