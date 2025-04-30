package com.example.yahtzee.controller;

import com.example.yahtzee.callbacks.*;
import com.example.yahtzee.model.*;
import java.util.*;

public class GameController {
    private Tournament tournament;
    private Round currentRound;
    private Turn currentTurn;
    private GameStateCallback gameStateCallback;
    private TurnCallback turnCallback;

    // Fields to track computer turn state
    private boolean isComputerTurnInProgress = false;
    private boolean skipComputerExplanations = false;
    private Thread computerTurnThread;

    public GameController(Tournament tournament, GameStateCallback gameStateCallback) {
        if (tournament == null) {
            throw new IllegalArgumentException("Tournament cannot be null");
        }
        if (gameStateCallback == null) {
            throw new IllegalArgumentException("GameStateCallback cannot be null");
        }
        
        this.tournament = tournament;
        this.gameStateCallback = gameStateCallback;
        
        try {
            startNewRound();
        } catch (Exception e) {
            gameStateCallback.onError("Failed to start new round: " + e.getMessage());
        }
    }

    public void startNewRound() {
        if (tournament == null) {
            throw new IllegalStateException("Tournament not initialized");
        }
        currentRound = tournament.getCurrentRound();
        if (currentRound == null) {
            throw new IllegalStateException("Failed to create new round");
        }
        Player firstPlayer = currentRound.getFirstPlayer();
        gameStateCallback.onRoundStarted(currentRound, firstPlayer);
        startNewTurn();
    }

    public void startNewTurn() {
        // Make sure currentRound is set
        currentRound = tournament.getCurrentRound();
        
        // Get the current turn
        currentTurn = currentRound.getCurrentTurn();
        
        // Get the current player from the tournament
        Player currentPlayer = tournament.getCurrentPlayer();
        
        // Initialize dice with random values for the new turn
        Random random = new Random();
        List<Integer> initialDiceValues = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            initialDiceValues.add(random.nextInt(6) + 1);
        }
        currentTurn.setDiceValues(initialDiceValues);
        
        // Clear any previously held dice
        List<Integer> heldIndices = new ArrayList<>(currentTurn.getHeldDiceIndices());
        for (Integer index : heldIndices) {
            currentTurn.releaseDie(index);
        }
        
        // Log the current player for debugging
        System.out.println("Starting new turn for player: " + currentPlayer.getName());
        
        // Notify UI about the new turn
        gameStateCallback.onTurnStarted(currentPlayer, currentTurn.getRollsLeft());
        gameStateCallback.onDiceRolled(initialDiceValues, new ArrayList<>());
        
        currentTurn.setCallback(new TurnCallback() {
            @Override
            public void onRollsUpdated(int rollsLeft) {
                if (turnCallback != null) {
                    turnCallback.onRollsUpdated(rollsLeft);
                }
            }

            @Override
            public void onDiceValuesChanged(List<Integer> values) {
                if (turnCallback != null) {
                    turnCallback.onDiceValuesChanged(values);
                }
                gameStateCallback.onDiceRolled(values, currentTurn.getHeldDiceIndices());
            }

            @Override
            public void onPredictionMade(ScoreCategory category, String reason, int minPoints, int maxPoints) {
                if (turnCallback != null) {
                    turnCallback.onPredictionMade(category, reason, minPoints, maxPoints);
                }
            }

            @Override
            public void onTurnComplete(ScoreCategory selectedCategory, int score) {
                if (turnCallback != null) {
                    turnCallback.onTurnComplete(selectedCategory, score);
                }
                gameStateCallback.onScoreSelected(selectedCategory, score, currentRound);
            }
        });

        // Check if player has rolls left but all potential scores would be 0
        // If that's the case, don't start computer turn yet as it would auto-end
        boolean autoEndedTurn = currentTurn.getRollsLeft() <= 0 && checkAndAutoEndTurn();
        
        // Start computer turn if it's the computer's turn and we didn't auto-end the turn
        if (currentPlayer.isComputer() && !autoEndedTurn) {
            playComputerTurn();
        }
    }

    /**
     * Rolls the dice for the current turn.
     * @return true if the roll was successful, false otherwise
     */
    public boolean rollDice() {
            if (currentTurn == null) {
            if (gameStateCallback != null) {
                gameStateCallback.onError("Cannot roll dice - no active turn");
            }
            return false;
            }
            
            if (currentTurn.getRollsLeft() <= 0) {
            if (gameStateCallback != null) {
                gameStateCallback.onError("No rolls left! Select a category to score");
            }
            return false;
        }

        currentTurn.rollDice();

        if (gameStateCallback != null) {
            gameStateCallback.onDiceRolled(currentTurn.getDice(), currentTurn.getHeldDiceIndices());
        }
        
        // Check if we need to auto-end the turn after this roll
        checkAndAutoEndTurn();
        
        return true;
    }

    /**
     * Rolls all non-held dice with random values.
     * @return true if the roll was successful, false otherwise
     */
    public boolean rollRandomDice() {
        if (currentTurn == null || currentTurn.getRollsLeft() <= 0) {
            if (gameStateCallback != null) {
                gameStateCallback.onError("No rolls left! Select a category to score");
            }
            return false;
        }

        // Get the current dice and held dice info
        List<Integer> dice = new ArrayList<>(currentTurn.getDice());
        List<Integer> heldIndices = currentTurn.getHeldDiceIndices();
        
        // Roll all non-held dice
            Random random = new Random();
        for (int i = 0; i < dice.size(); i++) {
            if (!heldIndices.contains(i)) {
                dice.set(i, random.nextInt(6) + 1);
            }
        }
        
        // Update dice values and decrement rolls
        currentTurn.setDiceValues(dice);
            currentTurn.decrementRolls();
            
            // Notify about the roll
            if (gameStateCallback != null) {
            gameStateCallback.onDiceRolled(dice, heldIndices);
        }
        
        // Check if we need to auto-end the turn after this roll
        if (currentTurn.getRollsLeft() <= 0) {
            checkAndAutoEndTurn();
        }
        
        return true;
    }

    /**
     * Rolls dice for the computer player's turn, keeping any held dice.
     * @return true if the roll was successful, false otherwise
     */
    public boolean rollRandomComputerDice() {
        if (currentTurn == null || currentTurn.getRollsLeft() <= 0) {
            if (gameStateCallback != null) {
                gameStateCallback.onError("Computer has no rolls left");
            }
            return false;
        }
        
        // Get the current dice and held indices
        List<Integer> currentDice = currentTurn.getDice();
            List<Integer> heldIndices = currentTurn.getHeldDiceIndices();
        List<Integer> newDiceValues = new ArrayList<>();
        
        // Roll non-held dice, keep held dice
            Random random = new Random();
        for (int i = 0; i < currentDice.size(); i++) {
                if (heldIndices.contains(i)) {
                newDiceValues.add(currentDice.get(i));
                } else {
                newDiceValues.add(random.nextInt(6) + 1);
            }
        }
        
        // Update dice values and decrement rolls
        currentTurn.setDiceValues(newDiceValues);
            currentTurn.decrementRolls();
            
        // Notify about the roll and update UI
            if (gameStateCallback != null) {
            gameStateCallback.onDiceRolled(newDiceValues, heldIndices);
                gameStateCallback.onTurnStarted(tournament.getCurrentPlayer(), currentTurn.getRollsLeft());
        }
        
        // Check if we need to auto-end the turn after this roll
        if (currentTurn.getRollsLeft() <= 0) {
            if (checkAndAutoEndTurn()) {
                // If the turn was auto-ended, close computer dialogs
                if (gameStateCallback != null) {
                    gameStateCallback.onComputerTurnEnd();
                }
                return true;
            }
        }
        
        return true;
    }

    public void holdDie(int index) {
        try {
            currentTurn.holdDie(index);
            gameStateCallback.onDiceHeld(currentTurn.getHeldDiceIndices());
        } catch (IllegalArgumentException e) {
            gameStateCallback.onError(e.getMessage());
        }
    }

    public void releaseDie(int index) {
        try {
            currentTurn.releaseDie(index);
            gameStateCallback.onDiceHeld(currentTurn.getHeldDiceIndices());
        } catch (IllegalArgumentException e) {
            gameStateCallback.onError(e.getMessage());
        }
    }

    public boolean isCategoryValid(ScoreCategory category) {
        if (tournament == null || currentTurn == null) return false;
        ScoreCard scoreCard = tournament.getScoreCard(); // Get shared scorecard
        if (scoreCard == null) return false;
        
        // Check if category is already scored
        if (scoreCard.isScored(category)) {
            return false;
        }
        
        return true;
    }

    public List<ScoreCategory> getAvailableCategories() {
        if (tournament == null || currentTurn == null) return new ArrayList<>();
        
        ScoreCard scoreCard = tournament.getScoreCard(); // Get shared scorecard
        if (scoreCard == null) return new ArrayList<>();
        
        List<ScoreCategory> available = new ArrayList<>();
        for (ScoreCategory category : ScoreCategory.values()) {
            if (!scoreCard.isScored(category)) {
                available.add(category);
            }
        }
        return available;
    }

    public Map<ScoreCategory, Integer> getPotentialScores() {
        if (tournament == null || currentTurn == null) return new HashMap<>();
        
        ScoreCard scoreCard = tournament.getScoreCard(); // Get shared scorecard
        if (scoreCard == null) return new HashMap<>();
        
        Map<ScoreCategory, Integer> scores = new HashMap<>();
        List<Integer> currentDice = currentTurn.getDice();
        
        for (ScoreCategory category : getAvailableCategories()) {
            scores.put(category, scoreCard.calculateScore(category, currentDice));
        }
        return scores;
    }

    public void selectCategory(ScoreCategory category) {
        try {
            if (!isCategoryValid(category)) {
                gameStateCallback.onError("Category " + category.name() + " is not available");
                return;
            }

            // Get current player and calculate score
            Player currentPlayer = tournament.getCurrentPlayer();
            ScoreCard scoreCard = tournament.getScoreCard(); // Now using shared scorecard
            int score = scoreCard.calculateScore(category, currentTurn.getDice());
            
            // Record the score
            scoreCard.setScore(category, score, currentPlayer, currentRound);
            
            // Record which player scored this category
            tournament.recordCategoryScorer(category, currentPlayer);
            
            // Notify about score selection
            gameStateCallback.onScoreSelected(category, score, currentRound);
            
            // Remember who the current player is before switching
            boolean wasComputerTurn = currentPlayer.isComputer();
            Player previousPlayer = currentPlayer;
            
            // Complete the turn in the tournament
            // Note: This should change the current player but we're seeing issues with this
            tournament.completeTurn();
            
            // IMPORTANT FIX: Directly verify the player changed and force it if not
            if (tournament.getCurrentPlayer() == previousPlayer) {
                // Player didn't change - explicitly switch player
                if (previousPlayer == tournament.getHumanPlayer()) {
                    // Switch from human to computer
                    tournament.setCurrentPlayer(tournament.getComputerPlayer());
                } else {
                    // Switch from computer to human
                    tournament.setCurrentPlayer(tournament.getHumanPlayer());
                }
                
                // Log for debugging
                System.out.println("FORCED PLAYER SWITCH from " + previousPlayer.getName() + 
                                   " to " + tournament.getCurrentPlayer().getName());
            } else {
                System.out.println("Player switched normally from " + previousPlayer.getName() + 
                                  " to " + tournament.getCurrentPlayer().getName());
            }
            
            // Check if the shared scorecard is complete
            boolean isGameComplete = scoreCard.isComplete();
            
            if (isGameComplete) {
                // Get player scores
                Map<Player, Integer> playerScores = tournament.calculatePlayerScores();
                
                // Notify round completion
                gameStateCallback.onRoundComplete(currentRound, playerScores);
                
                // The game is over when all categories are filled
                Player winner = tournament.determineWinner();
                gameStateCallback.onGameOver(winner, playerScores);
            } else {
                // Start next turn with the switched player
                startNewTurn();
                
                // If we switched to computer and it wasn't previously the computer's turn,
                // we need to explicitly start the computer's turn
                if (tournament.getCurrentPlayer().isComputer() && !wasComputerTurn) {
                    playComputerTurn();
                }
            }
            
        } catch (IllegalArgumentException e) {
            gameStateCallback.onError(e.getMessage());
        }
    }

    public void setComputerDiceValues(List<Integer> values) {
        if (currentTurn == null) return;
        currentTurn.setDiceValues(values);
        currentTurn.decrementRolls();
        if (gameStateCallback != null) {
            gameStateCallback.onTurnStarted(tournament.getCurrentPlayer(), currentTurn.getRollsLeft());
        }
    }

    public void setManualDiceValues(List<Integer> values) {
        if (currentTurn != null) {
            try {
                currentTurn.setDiceValues(values);
                currentTurn.decrementRolls();
                if (gameStateCallback != null) {
                    gameStateCallback.onTurnStarted(tournament.getCurrentPlayer(), currentTurn.getRollsLeft());
                }
            } catch (IllegalArgumentException e) {
                gameStateCallback.onError(e.getMessage());
            }
        }
    }

    /**
     * Handles the computer player's turn with step-by-step user control.
     */
    public void playComputerTurn() {
        // Initialize state
        isComputerTurnInProgress = true;
        skipComputerExplanations = false;
        
        // Create and start computer turn thread
        computerTurnThread = new Thread(() -> {
            try {
                Player computer = tournament.getCurrentPlayer();
                if (!computer.isComputer()) {
                    isComputerTurnInProgress = false;
                    return;
                }

                ComputerPlayer computerPlayer = (ComputerPlayer) computer;
                ComputerPlayerActions computerActions = computerPlayer;
                
                // 1. Initialize turn and show start message
                computerPlayer.takeTurn(currentRound);
                
                // Main turn loop
                while (currentTurn.getRollsLeft() > 0 && !skipComputerExplanations) {
                    // 2. Wait for user to click Next before rolling
                    waitForUserInput("Roll Dice", () -> {
                        try {
                            // Show roll request and perform roll
                    gameStateCallback.onComputerRollRequest();
                            rollRandomComputerDice();
                            
                            // If turn was auto-ended, exit
                            if (currentTurn == null || currentTurn.isComplete()) {
                                gameStateCallback.onComputerTurnEnd();
                                isComputerTurnInProgress = false;
                                return;
                            }
                            
                            // Wait for user to click Next before analyzing
                            waitForUserInput("Analyze Roll", () -> {
                                // Analyze current roll
                    computerPlayer.analyzeCurrentState();
                                
                                // Wait for user to click Next before making roll decision
                                waitForUserInput("Make Decision", () -> {
                                    // Decide whether to roll again
                                    boolean rollAgain = computerPlayer.shouldRollAgain(currentRound);
                                    
                                    if (!rollAgain) {
                                        // If not rolling again, exit the loop
                                        continueComputerTurnAfterRolls();
                                    }
                                });
                            });
                        } catch (Exception e) {
                            gameStateCallback.onError("Error during computer turn: " + e.getMessage());
                            isComputerTurnInProgress = false;
                        }
                    });
                    
                    // If skip flag was set or we break from the loop, exit
                    if (skipComputerExplanations || !computerPlayer.shouldRollAgain(currentRound)) {
                        break;
                    }
                }
                
                // If skip flag set, continue immediately to finalizing the turn
                if (skipComputerExplanations) {
                    continueComputerTurnAfterRolls();
                }
                
            } catch (Exception e) {
                gameStateCallback.onError("Error during computer turn: " + e.getMessage());
                gameStateCallback.onComputerTurnEnd();
                isComputerTurnInProgress = false;
            }
        });
        
        computerTurnThread.start();
    }
    
    /**
     * Continues the computer turn after all rolls are complete.
     */
    private void continueComputerTurnAfterRolls() {
        try {
            // Check if the turn was already auto-ended
            if (currentTurn == null || currentTurn.isComplete()) {
                gameStateCallback.onComputerTurnEnd();
                isComputerTurnInProgress = false;
                return;
            }
            
            // Get current player and scorecard
            Player computer = tournament.getCurrentPlayer();
            if (!computer.isComputer()) {
                isComputerTurnInProgress = false;
                return;
            }
            
            ComputerPlayer computerPlayer = (ComputerPlayer) computer;
            ComputerPlayerActions computerActions = computerPlayer;
            ScoreCard sharedScoreCard = tournament.getScoreCard();
            
            // Check if all potential scores are 0
            Map<ScoreCategory, Integer> potentialScores = getPotentialScores();
            boolean allZeros = potentialScores.values().stream().allMatch(score -> score == 0);
            
            if (allZeros && !getAvailableCategories().isEmpty()) {
                // Handle all-zero case
                ScoreCategory categoryToScore = getAvailableCategories().stream()
                    .filter(ScoreCategory::isUpperSection)
                    .findFirst()
                    .orElse(getAvailableCategories().get(0));
                
                // Add explanation
                computerActions.addExplanationMessage("Auto-Scoring", 
                    "All categories would score 0. Auto-selecting " + categoryToScore.getDisplayName());
                
                // Wait for user to click Next before scoring
                waitForUserInput("Score Category", () -> {
                    // Score the category and end turn
                    selectCategory(categoryToScore);
                    gameStateCallback.onComputerTurnEnd();
                    isComputerTurnInProgress = false;
                });
                return;
            }
            
            // Determine best category choice
            ScoreCategory bestCategory = computerPlayer.determineNextMove(sharedScoreCard);
            
            // Fallback if no best category found
            if (bestCategory == null && !getAvailableCategories().isEmpty()) {
                bestCategory = getAvailableCategories().get(0);
                computerActions.addExplanationMessage("Error Recovery", 
                    "Could not determine best category, selecting " + bestCategory.getDisplayName() + " as fallback.");
            }
            
            if (bestCategory == null) {
                gameStateCallback.onError("Computer turn error: No categories available");
                gameStateCallback.onComputerTurnEnd();
                isComputerTurnInProgress = false;
                return;
            }
            
            // Get score for chosen category
            final ScoreCategory finalCategory = bestCategory;
            int score = sharedScoreCard.calculateScore(finalCategory, currentTurn.getDice());
            
            // Show final decision
            computerActions.addExplanationMessage("Final Decision",
                String.format("Selected %s for %d points with dice %s", 
                    finalCategory.getDisplayName(), 
                    score,
                    computerActions.formatDiceValuesString(currentTurn.getDice())));
            
            // Wait for user to click Next before finalizing
            waitForUserInput("Finalize Turn", () -> {
                // Score the category and end turn
                selectCategory(finalCategory);
                gameStateCallback.onComputerTurnEnd();
                isComputerTurnInProgress = false;
            });
            
        } catch (Exception e) {
            gameStateCallback.onError("Error finalizing computer turn: " + e.getMessage());
            gameStateCallback.onComputerTurnEnd();
            isComputerTurnInProgress = false;
        }
    }
    
    /**
     * Waits for the user to click Next before continuing the computer turn.
     * 
     * @param nextStepDescription Description of the next step
     * @param nextAction Runnable to execute when user clicks Next
     */
    private void waitForUserInput(String nextStepDescription, Runnable nextAction) {
        if (skipComputerExplanations) {
            // Skip waiting and execute next action immediately
            nextAction.run();
            return;
        }
        
        // Request user input through callback
        gameStateCallback.onComputerTurnWaitingForUser(nextStepDescription, nextAction);
    }

    public void setTurnCallback(TurnCallback callback) {
        this.turnCallback = callback;
    }

    public Tournament getTournament() {
        return tournament;
    }

    public boolean isGameInProgress() {
        return tournament != null && currentRound != null;
    }

    public List<Integer> getDiceValues() {
        if (!isGameInProgress()) {
            return new ArrayList<>(); // Return empty list if no game is in progress
        }
        return currentRound.getDiceValues();
    }

    public SuggestionResult getSuggestion() {
        if (currentRound == null || tournament == null) {
            return null;
        }
        
        Player currentPlayer = tournament.getCurrentPlayer();
        if (currentPlayer == null || currentPlayer.isComputer()) {
            return null;
        }

        Map<ScoreCategory, SuggestionResult> suggestions = 
            currentPlayer.analyzePossibleMoves(tournament.getScoreCard(currentPlayer));
        
        if (suggestions.isEmpty()) {
            return null;
        }

        // Find the best suggestion based on points
        return suggestions.values().stream()
            .max((s1, s2) -> Integer.compare(s1.getMaxPoints(), s2.getMaxPoints()))
            .orElse(null);
    }

    /**
     * Checks if the current turn should automatically end and handles it if needed.
     * This happens when the player has no rolls left and all unselected categories would score 0.
     * @return true if turn was auto-ended, false otherwise
     */
    private boolean checkAndAutoEndTurn() {
        if (currentTurn == null || currentTurn.getRollsLeft() > 0) {
            return false;
        }
        
        // Get the current player and available categories
        Player currentPlayer = tournament.getCurrentPlayer();
        ScoreCard scoreCard = tournament.getScoreCard(); // Get shared scorecard
        if (scoreCard == null) return false;
        
        List<Integer> diceValues = currentTurn.getDice();
        
        // Check if all unselected categories would score 0
        boolean allZeros = true;
        for (ScoreCategory category : getAvailableCategories()) {
            int potentialScore = scoreCard.calculateScore(category, diceValues);
            if (potentialScore > 0) {
                // Found at least one category that would score points
                allZeros = false;
                break;
            }
        }
        
        // If all potential scores are zero, automatically end the turn
        if (allZeros && !getAvailableCategories().isEmpty()) {
            // Find the first available category to assign zero (preferably choose a lower value upper section)
            ScoreCategory categoryToAssign = null;
            
            // First try to find an upper section category (they're typically worth less)
            for (ScoreCategory category : getAvailableCategories()) {
                if (category.isUpperSection()) {
                    categoryToAssign = category;
                    break;
                }
            }
            
            // If no upper section category is available, take the first lower section category
            if (categoryToAssign == null && !getAvailableCategories().isEmpty()) {
                categoryToAssign = getAvailableCategories().get(0);
            }
            
            if (categoryToAssign != null) {
                // Notify the user that turn is being auto-ended
                if (gameStateCallback != null) {
                    gameStateCallback.onError("No possible points available. Automatically scoring " + 
                            categoryToAssign.getDisplayName() + " with 0 points.");
                }
                
                // Also record this player as the scorer for this category
                tournament.recordCategoryScorer(categoryToAssign, currentPlayer);
                
                // Select the category (which will handle switching to the next player)
                selectCategory(categoryToAssign);
                return true;
            }
        }
        
        return false;
    }

    public void skipComputerTurnExplanation() {
        skipComputerExplanations = true;
        
        // If there's a computer turn thread running, interrupt it
        if (computerTurnThread != null && computerTurnThread.isAlive()) {
            computerTurnThread.interrupt();
        }
        
        // If computer turn was in progress, fast-forward to the end
        if (isComputerTurnInProgress && currentTurn != null && !currentTurn.isComplete()) {
            try {
                // Get the best category and score it
                Player computer = tournament.getCurrentPlayer();
                if (computer.isComputer()) {
                    ComputerPlayer computerPlayer = (ComputerPlayer) computer;
                    ScoreCard scoreCard = tournament.getScoreCard();
                    ScoreCategory bestCategory = computerPlayer.determineNextMove(scoreCard);
                    
                    if (bestCategory != null) {
                        // Score the category and end turn
                        selectCategory(bestCategory);
                    }
                }
            } catch (Exception e) {
                gameStateCallback.onError("Error skipping computer turn: " + e.getMessage());
            } finally {
                // Clean up and end the turn
                gameStateCallback.onComputerTurnEnd();
                isComputerTurnInProgress = false;
            }
        }
    }
}
