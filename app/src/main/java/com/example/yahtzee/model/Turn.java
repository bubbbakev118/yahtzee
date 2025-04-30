package com.example.yahtzee.model;

import com.example.yahtzee.callbacks.TurnCallback;
import java.io.Serializable;
import java.util.*;

/**
 * Represents a single turn in the Yahtzee game.
 * A turn consists of up to 3 dice rolls and selecting a scoring category.
 */
public class Turn implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final int MAX_ROLLS = 3;
    private static final int NUM_DICE = 5;
    
    private final List<Integer> dice;
    private final List<Integer> heldDiceIndices;
    private int rollsLeft;
    private TurnRecord turnRecord;
    private boolean isComplete;
    private final Round round;
    private TurnCallback callback;

    public Turn(Round round) {
        this.dice = new ArrayList<>(Arrays.asList(0, 0, 0, 0, 0));
        this.heldDiceIndices = new ArrayList<>();
        this.rollsLeft = MAX_ROLLS;
        this.turnRecord = new TurnRecord(round, new ArrayList<>(dice), new ArrayList<>(heldDiceIndices), rollsLeft);
        this.isComplete = false;
        this.round = round;
    }

    public void setCallback(TurnCallback callback) {
        this.callback = callback;
    }

    public List<Integer> getDice() {
        return Collections.unmodifiableList(dice);
    }

    public List<Integer> getHeldDiceIndices() {
        return new ArrayList<>(heldDiceIndices);
    }

    public int getRollsLeft() {
        return rollsLeft;
    }

    public void decrementRolls() {
        if (rollsLeft > 0) {
            rollsLeft--;
            if (callback != null) {
                callback.onRollsUpdated(rollsLeft);
            }
        }
    }

    public boolean isComplete() {
        return isComplete;
    }

    public Round getRound() {
        return round;
    }

    public void setComplete() {
        this.isComplete = true;
    }

    public void holdDie(int index) {
        if (index < 0 || index >= NUM_DICE) {
            throw new IllegalArgumentException("Invalid die index: " + index);
        }
        if (!heldDiceIndices.contains(index)) {
            heldDiceIndices.add(index);
            notifyDiceChanged();
        }
    }

    public void releaseDie(int index) {
        if (index < 0 || index >= NUM_DICE) {
            throw new IllegalArgumentException("Invalid die index: " + index);
        }
        heldDiceIndices.remove((Integer) index);
        notifyDiceChanged();
    }

    public void roll() {
        if (rollsLeft <= 0) {
            throw new IllegalStateException("No rolls left in this turn");
        }

        // Roll all non-held dice
        Random random = new Random();
        for (int i = 0; i < NUM_DICE; i++) {
            if (!heldDiceIndices.contains(i)) {
                dice.set(i, random.nextInt(6) + 1);
            }
        }
        rollsLeft--;
        
        // Notify about the roll
        if (callback != null) {
            callback.onRollsUpdated(rollsLeft);
            callback.onDiceValuesChanged(new ArrayList<>(dice));
        }
        
        // Record the turn state
        turnRecord = new TurnRecord(round, new ArrayList<>(dice), new ArrayList<>(heldDiceIndices), rollsLeft);
    }

    public void rollDice() {
        roll();
    }

    public void setDiceValues(List<Integer> values) {
        if (values == null || values.size() != NUM_DICE) {
            throw new IllegalArgumentException("Must provide exactly " + NUM_DICE + " dice values");
        }
        for (Integer value : values) {
            if (value < 0 || value > 6) {
                throw new IllegalArgumentException("Dice values must be between 0 and 6");
            }
        }
        
        if (rollsLeft <= 0) {
            throw new IllegalStateException("No rolls left in this turn");
        }

        for (int i = 0; i < NUM_DICE; i++) {
            dice.set(i, values.get(i));
        }
        
        notifyDiceChanged();
    }

    public List<Integer> getDiceValues() {
        return Collections.unmodifiableList(dice);
    }

    private void notifyDiceChanged() {
        if (callback != null) {
            callback.onDiceValuesChanged(new ArrayList<>(dice));
        }
    }

    public TurnRecord getRecord() {
        return turnRecord;
    }
}
