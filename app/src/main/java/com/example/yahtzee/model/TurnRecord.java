package com.example.yahtzee.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unified class to handle both turn announcements and history.
 * This replaces both TurnAnnouncement and TurnHistory classes.
 */
public class TurnRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    private final Round round;
    private final List<Integer> dice;
    private final List<Integer> heldDiceIndices;
    private final int rollsLeft;
    private ScoreCategory selectedCategory;
    private int finalScore;
    private String explanation;
    private boolean isStanding;

    public TurnRecord(Round round, List<Integer> dice, List<Integer> heldDiceIndices, int rollsLeft) {
        this.round = round;
        this.dice = new ArrayList<>(dice);
        this.heldDiceIndices = new ArrayList<>(heldDiceIndices);
        this.rollsLeft = rollsLeft;
        this.isStanding = false;
    }

    public void recordRoll(List<Integer> diceValues, List<Integer> heldIndices) {
        // Removed this method as it is not compatible with the new constructor
    }

    public void addPrediction(ScoreCategory category, String reason, int minPoints, int maxPoints) {
        // Removed this method as it is not compatible with the new constructor
    }

    public void recordResult(ScoreCategory category, int score, String explanation) {
        this.selectedCategory = category;
        this.finalScore = score;
        this.explanation = explanation;
    }

    public List<Integer> getDice() {
        return new ArrayList<>(dice);
    }

    public List<Integer> getHeldDiceIndices() {
        return new ArrayList<>(heldDiceIndices);
    }

    public int getRollsLeft() {
        return rollsLeft;
    }

    public ScoreCategory getSelectedCategory() {
        return selectedCategory;
    }

    public int getFinalScore() {
        return finalScore;
    }

    public String getExplanation() {
        return explanation;
    }

    public boolean isStanding() {
        return isStanding;
    }

    public void setStanding(boolean standing) {
        this.isStanding = standing;
    }



    public Round getRound() {
        return round;
    }
}
