package com.example.yahtzee.model;

import java.io.Serializable;
import java.util.*;

/**
 * Abstract base class for all players in the Yahtzee game.
 * Implements common functionality and defines the contract for player-specific behavior.
 */
public abstract class Player implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final String name;
    protected Turn currentTurn;
    private Round currentRound;
    private String lastDecisionExplanation;

    protected Player(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setCurrentTurn(Turn turn) {
        this.currentTurn = turn;
    }

    public Turn getCurrentTurn() {
        return currentTurn;
    }

    public void setCurrentRound(Round round) {
        this.currentRound = round;
        if (round != null) {
            setCurrentTurn(round.getCurrentTurn());
        }
    }

    public Round getCurrentRound() {
        return currentRound;
    }

    public List<Integer> getAllDice() {
        return currentTurn != null ? currentTurn.getDice() : Collections.emptyList();
    }

    public List<Integer> getHeldDiceIndices() {
        return currentTurn != null ? currentTurn.getHeldDiceIndices() : new ArrayList<>();
    }

    public void holdDice(List<Integer> indices) {
        if (currentTurn == null) return;
        for (Integer index : indices) {
            currentTurn.holdDie(index);
        }
    }

    public void releaseDice(List<Integer> indices) {
        if (currentTurn == null) return;
        for (Integer index : indices) {
            currentTurn.releaseDie(index);
        }
    }

    public void rollDice() {
        if (currentTurn != null) {
            currentTurn.roll();
        }
    }

    public void setLastDecisionExplanation(String explanation) {
        this.lastDecisionExplanation = explanation;
    }

    public String getLastDecisionExplanation() {
        return lastDecisionExplanation;
    }

    public abstract boolean isComputer();
    
    public abstract void takeTurn(Round round);
    
    public abstract boolean shouldRollAgain(Round round);
    
    public abstract ScoreCategory chooseCategory(Round round, List<ScoreCategory> availableCategories);
    
    public abstract ScoreCategory determineNextMove(ScoreCard scoreCard);
    
    public abstract List<Integer> determineDiceToHold();
    
    public abstract Map<ScoreCategory, SuggestionResult> analyzePossibleMoves(ScoreCard scoreCard);

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Player)) return false;
        Player player = (Player) o;
        return Objects.equals(name, player.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
