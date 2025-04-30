package com.example.yahtzee.model;

import java.util.*;

/**
 * Represents a round in the Yahtzee game.
 * A round consists of each player taking one turn.
 */
public class Round {
    private final int roundNumber;
    private final List<Player> players;
    private final Player firstPlayer;
    private int currentPlayerIndex;
    private Turn currentTurn;
    private boolean isComplete;
    private final ScoreCard sharedScoreCard;

    /**
     * Create a new round with the specified players, first player, and round number.
     * Uses a shared scorecard for both players.
     */
    public Round(List<Player> players, Player firstPlayer, int roundNumber, ScoreCard sharedScoreCard) {
        this.players = new ArrayList<>(players);
        this.firstPlayer = firstPlayer;
        this.roundNumber = roundNumber;
        this.currentPlayerIndex = players.indexOf(firstPlayer);
        this.isComplete = false;
        this.sharedScoreCard = sharedScoreCard;
        startNewTurn();
    }

    private void startNewTurn() {
        Player currentPlayer = getCurrentPlayer();
        currentTurn = new Turn(this);
        currentPlayer.setCurrentTurn(currentTurn);
    }

    public void completeTurn() {
        currentTurn.setComplete();
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
        
        if (currentPlayerIndex == players.indexOf(firstPlayer)) {
            isComplete = true;
        } else {
            startNewTurn();
        }
    }

    public void nextTurn() {
        if (isComplete) {
            throw new IllegalStateException("Round is already complete");
        }
        
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
        if (currentPlayerIndex == players.indexOf(firstPlayer)) {
            isComplete = true;
        } else {
            currentTurn = new Turn(this);
            getCurrentPlayer().setCurrentTurn(currentTurn);
        }
    }

    public Player getCurrentPlayer() {
        return players.get(currentPlayerIndex);
    }

    public Turn getCurrentTurn() {
        return currentTurn;
    }

    public Turn getTurn() {
        return currentTurn;
    }

    public List<Integer> getDice() {
        return currentTurn != null ? currentTurn.getDice() : Collections.emptyList();
    }

    public List<Integer> getDiceValues() {
        if (currentTurn == null) {
            return new ArrayList<>();
        }
        return currentTurn.getDiceValues();
    }

    /**
     * Get the shared scorecard for all players.
     */
    public ScoreCard getScoreCard() {
        return sharedScoreCard;
    }
    
    /**
     * For compatibility with existing code, returns the shared scorecard regardless of player.
     */
    public ScoreCard getScoreCard(Player player) {
        return sharedScoreCard;
    }

    public int getRoundNumber() {
        return roundNumber;
    }

    public boolean isComplete() {
        return isComplete;
    }

    public Player getFirstPlayer() {
        return firstPlayer;
    }

    public List<Player> getPlayers() {
        return Collections.unmodifiableList(players);
    }
}
