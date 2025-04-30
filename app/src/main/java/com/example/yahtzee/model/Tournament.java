package com.example.yahtzee.model;

import com.example.yahtzee.callbacks.GameStateCallback;
import java.io.*;
import java.util.*;

public class Tournament implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private Player humanPlayer;
    private ComputerPlayer computerPlayer;
    private Player firstPlayer;
    private Round currentRound;
    private GameStateCallback gameStateCallback;
    private int currentPlayerIndex;
    private int roundNumber;
    private boolean isGameOver;
    
    // Replace the map of scorecards with a single shared scorecard
    private ScoreCard sharedScoreCard;
    
    // Add a map to track which player scored which category
    private Map<ScoreCategory, Player> categoryScorers;

    public Tournament(GameStateCallback callback) {
        this.gameStateCallback = callback;
        this.humanPlayer = new HumanPlayer("Player");
        this.computerPlayer = new ComputerPlayer(callback);
        this.firstPlayer = humanPlayer;
        this.currentPlayerIndex = 0;
        this.roundNumber = 1;
        this.isGameOver = false;
        
        // Initialize the shared scorecard
        this.sharedScoreCard = new ScoreCard();
        
        // Initialize the map to track which player scored which category
        this.categoryScorers = new HashMap<>();
        
        startNewRound();
    }

    public Tournament(String filename) throws IOException {
        try {
            Tournament loaded = loadGame(filename);
            this.humanPlayer = loaded.humanPlayer;
            this.computerPlayer = (ComputerPlayer) loaded.computerPlayer;
            this.firstPlayer = loaded.firstPlayer;
            this.currentRound = loaded.currentRound;
            this.gameStateCallback = loaded.gameStateCallback;
            this.currentPlayerIndex = loaded.currentPlayerIndex;
            this.roundNumber = loaded.roundNumber;
            this.isGameOver = loaded.isGameOver;
            this.sharedScoreCard = loaded.sharedScoreCard;
            this.categoryScorers = loaded.categoryScorers;
        } catch (IOException e) {
            throw new IOException("Failed to load game: " + e.getMessage(), e);
        }
    }

    private void startNewRound() {
        Player firstPlayer;
        if (currentRound == null) {
            // First round, randomly select first player
            firstPlayer = humanPlayer;
        } else {
            // Subsequent rounds, next player after last player of previous round
            int lastPlayerIndex = 0;
            if (currentRound.getCurrentPlayer() == humanPlayer) {
                lastPlayerIndex = 1;
            }
            firstPlayer = lastPlayerIndex == 0 ? humanPlayer : computerPlayer;
        }
        
        currentRound = new Round(Arrays.asList(humanPlayer, computerPlayer), firstPlayer, roundNumber, sharedScoreCard);
        currentPlayerIndex = firstPlayer == humanPlayer ? 0 : 1;
    }

    public void completeTurn() {
        if (currentRound == null || isGameOver) {
            return;
        }
        
        // Complete the current turn in the round
        currentRound.completeTurn();
        
        if (currentRound.isComplete()) {
            // Round is complete, start a new round
            roundNumber++;
            if (roundNumber > ScoreCard.TOTAL_ROUNDS) {
                // Game is over
                isGameOver = true;
                Player winner = determineWinner();
                Map<Player, Integer> finalScores = calculatePlayerScores();
                gameStateCallback.onGameOver(winner, finalScores);
            } else {
                // Start a new round
                startNewRound();
            }
        } else {
            // Switch to the next player
            switchToNextPlayer();
        }
    }

    /**
     * Directly switches to the next player.
     * This method is useful when we need to explicitly force a player change.
     */
    public void switchToNextPlayer() {
        // Switch to the next player
        currentPlayerIndex = (currentPlayerIndex + 1) % 2;
        
        // Get the updated current player
        Player nextPlayer = getCurrentPlayer();
        
        // Make sure the round knows about the updated player
        if (currentRound != null && currentRound.getCurrentPlayer() != nextPlayer) {
            currentRound.nextTurn();
        }
        
        // Debug print
        System.out.println("Switched to player: " + nextPlayer.getName());
    }

    public Player getCurrentPlayer() {
        return currentPlayerIndex == 0 ? humanPlayer : computerPlayer;
    }

    public Turn getCurrentTurn() {
        return currentRound != null ? currentRound.getCurrentTurn() : null;
    }

    public List<Player> getPlayers() {
        return Collections.unmodifiableList(Arrays.asList(humanPlayer, computerPlayer));
    }

    /**
     * Get the shared scorecard for the game
     */
    public ScoreCard getScoreCard() {
        return sharedScoreCard;
    }
    
    /**
     * For backwards compatibility - now returns the shared scorecard regardless of player
     */
    public ScoreCard getScoreCard(Player player) {
        return sharedScoreCard;
    }
    
    /**
     * Record which player scored a category
     */
    public void recordCategoryScorer(ScoreCategory category, Player player) {
        categoryScorers.put(category, player);
    }
    
    /**
     * Get the player who scored a specific category
     */
    public Player getCategoryScorer(ScoreCategory category) {
        return categoryScorers.get(category);
    }
    
    /**
     * Check if a player has a score for a category
     * 
     * @param category The category to check
     * @param player The player to check
     * @return true if the player has a score in this category
     */
    public boolean hasPlayerScoredCategory(ScoreCategory category, Player player) {
        // Check if player is primary scorer
        return player == categoryScorers.get(category);
    }
    
    /**
     * Get the score for a player in a category
     * 
     * @param category The category to get score for
     * @param player The player to get score for
     * @return The score, or 0 if not found
     */
    public int getPlayerCategoryScore(ScoreCategory category, Player player) {
        // Check if this player is the primary scorer for this category
        Player primaryScorer = categoryScorers.get(category);
        if (player == primaryScorer) {
            return sharedScoreCard.getScore(category);
        }
        
        // No score found
        return 0;
    }
    
    /**
     * Calculate the scores for each player based on categories they've scored
     */
    public Map<Player, Integer> calculatePlayerScores() {
        Map<Player, Integer> playerScores = new HashMap<>();
        playerScores.put(humanPlayer, 0);
        playerScores.put(computerPlayer, 0);
        
        // Go through each scored category and add points to the player who scored it
        for (ScoreCategory category : ScoreCategory.values()) {
            if (sharedScoreCard.isScored(category)) {
                Player scorer = categoryScorers.get(category);
                if (scorer != null) {
                    int currentScore = playerScores.get(scorer);
                    int categoryScore = sharedScoreCard.getScore(category);
                    playerScores.put(scorer, currentScore + categoryScore);
                }
            }
        }
        
        return playerScores;
    }

    public boolean isGameOver() {
        return isGameOver;
    }

    public Round getCurrentRound() {
        return currentRound;
    }

    public Player determineWinner() {
        Map<Player, Integer> scores = calculatePlayerScores();
        Player winner = null;
        int highestScore = -1;

        for (Player player : Arrays.asList(humanPlayer, computerPlayer)) {
            int totalScore = scores.get(player);
            if (totalScore > highestScore) {
                highestScore = totalScore;
                winner = player;
            }
        }

        return winner;
    }

    private static Tournament loadGame(String filename) throws IOException {
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(filename))) {
            return (Tournament) in.readObject();
        } catch (ClassNotFoundException e) {
            throw new IOException("Invalid save file format", e);
        }
    }

    public void saveGame(String filename) throws IOException {
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(filename))) {
            out.writeObject(this);
        }
    }

    public ComputerPlayer getComputerPlayer() {
        return computerPlayer;
    }

    public Player getHumanPlayer() {
        return humanPlayer;
    }

    /**
     * Directly sets the current player and updates the Round accordingly.
     * This ensures player switching works even when normal mechanisms fail.
     */
    public void setCurrentPlayer(Player player) {
        if (player != humanPlayer && player != computerPlayer) {
            throw new IllegalArgumentException("Player must be in the tournament");
        }
        
        // Update the current player index
        currentPlayerIndex = (player == humanPlayer) ? 0 : 1;
        
        // Make sure the round is in sync if it exists
        if (currentRound != null && currentRound.getCurrentPlayer() != player) {
            // If the round's player doesn't match, force a sync
            // We need a special approach since Round doesn't have a direct setCurrentPlayer
            
            // This is a hack: we'll call nextTurn() until the player matches
            // This assumes there are only 2 players
            if (currentRound.getCurrentPlayer() != player) {
                currentRound.nextTurn();
            }
        }
    }

    public void setFirstPlayer(Player player) {
        if (player != humanPlayer && player != computerPlayer) {
            throw new IllegalArgumentException("Player must be in the tournament");
        }
        firstPlayer = player;
        startNewRound();
    }
}
