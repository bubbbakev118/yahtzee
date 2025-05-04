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

        // Run the actual dice rolling in a background thread to keep UI responsive
        new Thread(() -> {
            try {
                // Roll the dice
                currentTurn.rollDice();
                
                // Notify UI on the main thread
                if (gameStateCallback != null) {
                    gameStateCallback.onDiceRolled(currentTurn.getDice(), currentTurn.getHeldDiceIndices());
                }
                
                // Check if we need to auto-end the turn after this roll
                checkAndAutoEndTurn();
            } catch (Exception e) {
                if (gameStateCallback != null) {
                    gameStateCallback.onError("Error rolling dice: " + e.getMessage());
                }
            }
        }).start();
        
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
                
                // If we switched from human to computer, we need to ensure the computer's turn starts properly
                if (!wasComputerTurn && tournament.getCurrentPlayer().isComputer()) {
                    // Add a short delay to ensure UI has time to update before starting computer turn
                    new Thread(() -> {
                        try {
                            // Give the UI thread time to update
                            Thread.sleep(500);
                            
                            // Start computer turn in a new thread
                            if (tournament.getCurrentPlayer().isComputer()) {
                                playComputerTurn();
                            }
                        } catch (InterruptedException e) {
                            // Ignore
                        }
                    }).start();
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
        // Validate current state first
        if (tournament == null || currentRound == null || currentTurn == null) {
            if (gameStateCallback != null) {
                gameStateCallback.onError("Cannot start computer turn - invalid game state");
                gameStateCallback.onComputerTurnEnd();
            }
            return;
        }
        
        // Make sure the current player is the computer
        Player currentPlayer = tournament.getCurrentPlayer();
        if (currentPlayer == null || !currentPlayer.isComputer()) {
            if (gameStateCallback != null) {
                gameStateCallback.onError("Cannot start computer turn - current player is not computer");
                gameStateCallback.onComputerTurnEnd();
            }
            return;
        }
        
        // Set state flags
        isComputerTurnInProgress = true;
        skipComputerExplanations = false;
        
        // Show initial UI update on main thread
        if (gameStateCallback != null) {
            gameStateCallback.onComputerTurnAnnouncement(Collections.singletonList("Computer's turn starting..."));
        }
        
        // Create and start computer turn thread with a meaningful name
        computerTurnThread = new Thread(() -> {
            try {
                // Double check that we're still on the computer's turn
                if (tournament.getCurrentPlayer() == null || !tournament.getCurrentPlayer().isComputer()) {
                    gameStateCallback.onError("Computer turn thread started but current player is not computer");
                    gameStateCallback.onComputerTurnEnd();
                    isComputerTurnInProgress = false;
                    return;
                }

                ComputerPlayer computerPlayer = (ComputerPlayer) tournament.getCurrentPlayer();
                
                // Initialize turn - this should populate the UI with initial explanations
                computerPlayer.takeTurn(currentRound);
                
                // Delay before showing the first prompt to give UI time to update
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    // Ignore
                }
                
                // Main turn loop - only run while we have rolls left and aren't skipping
                while (currentTurn != null && !currentTurn.isComplete() && 
                       currentTurn.getRollsLeft() > 0 && !skipComputerExplanations) {
                    
                    // User interface breaks the turn into discrete steps with user confirmation
                    processComputerTurnStep(computerPlayer);
                    
                    // Break if we should stop rolling or the user skipped
                    if (skipComputerExplanations || currentTurn == null || 
                        currentTurn.isComplete() || !computerPlayer.shouldRollAgain(currentRound)) {
                        break;
                    }
                }
                
                // Process final step if we didn't skip
                if (!skipComputerExplanations && currentTurn != null && !currentTurn.isComplete()) {
                    // Make category selection
                    finishComputerTurn();
                } else if (skipComputerExplanations) {
                    // Fast-track finish if skipped
                    finishComputerTurn();
                }
                
            } catch (Exception e) {
                System.err.println("Error during computer turn: " + e.getMessage());
                e.printStackTrace();
                
                if (gameStateCallback != null) {
                    gameStateCallback.onError("Error during computer turn: " + e.getMessage());
                    gameStateCallback.onComputerTurnEnd();
                }
                
                isComputerTurnInProgress = false;
            }
        }, "ComputerTurnThread");
        
        // Start as a daemon thread to ensure it doesn't prevent app exit
        computerTurnThread.setDaemon(true);
        computerTurnThread.start();
    }
    
    /**
     * Process a single step of the computer's turn.
     */
    private void processComputerTurnStep(ComputerPlayer computerPlayer) {
        // Use a CountDownLatch to wait for the step to complete
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        final boolean[] stepCompleted = {false};
        
        // Request the user to click Next before rolling
        waitForUserInput("Roll Dice", () -> {
            try {
                // Show rolling message
                gameStateCallback.onComputerRollRequest();
                
                // Short delay for UI feedback
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    // Ignore
                }
                
                // Roll the dice
                rollRandomComputerDice();
                
                // Check if the turn ended after rolling
                if (currentTurn == null || currentTurn.isComplete()) {
                    stepCompleted[0] = true;
                    latch.countDown();
                    return;
                }
                
                // Another short delay for UI to update
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    // Ignore
                }
                
                // Request user to click Next before analyzing
                waitForUserInput("Analyze Roll", () -> {
                    try {
                        // Analyze the current roll
                        computerPlayer.analyzeCurrentState();
                        
                        // Short delay for UI to update
                        try {
                            Thread.sleep(300);
                        } catch (InterruptedException e) {
                            // Ignore
                        }
                        
                        // Wait for user to click Next before making roll decision
                        waitForUserInput("Make Decision", () -> {
                            try {
                                // The computer decides whether to roll again
                                boolean rollAgain = computerPlayer.shouldRollAgain(currentRound);
                                
                                // Mark the step as complete
                                stepCompleted[0] = true;
                                latch.countDown();
                            } catch (Exception e) {
                                gameStateCallback.onError("Error during decision: " + e.getMessage());
                                latch.countDown();
                            }
                        });
                    } catch (Exception e) {
                        gameStateCallback.onError("Error during analysis: " + e.getMessage());
                        latch.countDown();
                    }
                });
            } catch (Exception e) {
                gameStateCallback.onError("Error during roll: " + e.getMessage());
                latch.countDown();
            }
        });
        
        // Wait for the entire step to complete or timeout after 30 seconds
        try {
            latch.await(30, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            // If interrupted, just continue
        }
        
        // If step didn't complete and we're not skipping, something went wrong
        if (!stepCompleted[0] && !skipComputerExplanations) {
            gameStateCallback.onError("Computer turn step timed out");
        }
    }
    
    /**
     * Complete the computer's turn by making the final category selection.
     */
    private void finishComputerTurn() {
        try {
            // Verify we still have a valid game state
            if (tournament == null || currentRound == null || currentTurn == null || 
                tournament.getCurrentPlayer() == null || !tournament.getCurrentPlayer().isComputer()) {
                isComputerTurnInProgress = false;
                gameStateCallback.onComputerTurnEnd();
                return;
            }
            
            // Get computer player
            ComputerPlayer computerPlayer = (ComputerPlayer) tournament.getCurrentPlayer();
            
            // First analyze the current state to make the best decision
            computerPlayer.analyzeCurrentState();
            
            // Get the best category to score
            ScoreCategory bestCategory = computerPlayer.determineNextMove(tournament.getScoreCard());
            
            // EXTRA VALIDATION: Make sure computer never chooses a zero-scoring category
            // when there are other scoring options available
            if (bestCategory != null) {
                ScoreCard scoreCard = tournament.getScoreCard();
                List<Integer> dice = currentTurn.getDice();
                int score = scoreCard.calculateScore(bestCategory, dice);
                
                // Only proceed with additional checks if the selected category would score zero
                if (score == 0) {
                    // Check if there are any non-zero scoring categories available
                    List<ScoreCategory> nonZeroCategories = new ArrayList<>();
                    Map<ScoreCategory, Integer> categoryScores = new HashMap<>();
                    
                    for (ScoreCategory category : getAvailableCategories()) {
                        int categoryScore = scoreCard.calculateScore(category, dice);
                        categoryScores.put(category, categoryScore);
                        if (categoryScore > 0) {
                            nonZeroCategories.add(category);
                        }
                    }
                    
                    // If we have non-zero options, use the highest scoring one instead
                    if (!nonZeroCategories.isEmpty()) {
                        ScoreCategory highestScoringCategory = null;
                        int highestScore = -1;
                        
                        for (ScoreCategory category : nonZeroCategories) {
                            int categoryScore = categoryScores.get(category);
                            if (categoryScore > highestScore) {
                                highestScore = categoryScore;
                                highestScoringCategory = category;
                            }
                        }
                        
                        if (highestScoringCategory != null) {
                            System.out.println("GameController overriding computer choice from " + 
                                bestCategory.getDisplayName() + " (0 pts) to " + 
                                highestScoringCategory.getDisplayName() + " (" + 
                                categoryScores.get(highestScoringCategory) + " pts)");
                            bestCategory = highestScoringCategory;
                        }
                    }
                }
            }
            
            // Fallback to first available category if needed
            if (bestCategory == null && !getAvailableCategories().isEmpty()) {
                bestCategory = getAvailableCategories().get(0);
            }
            
            // If we have a category, score it
            if (bestCategory != null) {
                // Get score for logging
                int score = tournament.getScoreCard().calculateScore(bestCategory, currentTurn.getDice());
                
                // Log the decision
                System.out.println("Computer scored " + bestCategory + " for " + score + " points");
                
                // Score the category
                final ScoreCategory finalCategory = bestCategory;
                selectCategory(finalCategory);
            }
            
            // End the computer turn
            gameStateCallback.onComputerTurnEnd();
            isComputerTurnInProgress = false;
            
        } catch (Exception e) {
            System.err.println("Error finishing computer turn: " + e.getMessage());
            e.printStackTrace();
            
            gameStateCallback.onError("Error finishing computer turn: " + e.getMessage());
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
        
        // If all potential scores are zero, automatically end the turn without scoring
        if (allZeros && !getAvailableCategories().isEmpty()) {
            skipTurn("No possible points available. Skipping turn.");
            return true;
        }
        
        return false;
    }
    
    /**
     * Skips the current player's turn without scoring in any category.
     * This should only be used when all categories would yield zero points.
     * 
     * @param reason The reason for skipping, shown to the player
     * @return true if the turn was successfully skipped
     */
    public boolean skipTurn(String reason) {
        if (tournament == null || currentRound == null) {
            gameStateCallback.onError("Game not properly initialized");
            return false;
        }
        
        // Add notification that turn is being skipped
        if (gameStateCallback != null) {
            gameStateCallback.onError(reason);
        }
        
        // Switch to the next player (this will advance the turn in the tournament)
        tournament.switchToNextPlayer();
        
        // Update the current turn reference
        if (tournament.getCurrentTurn() != null) {
            currentTurn = tournament.getCurrentTurn();
        }
        
        // Start the next player's turn
        boolean autoEndedTurn = currentTurn.getRollsLeft() <= 0 && checkAndAutoEndTurn();
        
        // Start computer turn if it's now the computer's turn and we didn't auto-end the turn
        if (tournament.getCurrentPlayer().isComputer() && !autoEndedTurn) {
            playComputerTurn();
        }
        
        return true;
    }

    /**
     * Called when the user wants to skip computer turn explanations.
     * This will mark the explanations to be skipped and execute the next action immediately.
     */
    public void skipComputerTurnExplanation() {
        System.out.println("User requested to skip computer turn explanation");
        
        // Set the flag to skip explanations
        skipComputerExplanations = true;
        
        // Create a background thread to handle the skip logic
        new Thread(() -> {
            try {
                // Make sure we're actually in a computer turn
                if (!isComputerTurnInProgress) {
                    System.out.println("Skip requested but no computer turn in progress");
                    return;
                }
                
                // Verify we have a valid game state
                if (tournament == null || currentRound == null || currentTurn == null || 
                    tournament.getCurrentPlayer() == null || !tournament.getCurrentPlayer().isComputer()) {
                    System.out.println("Skip requested but invalid game state");
                    isComputerTurnInProgress = false;
                    gameStateCallback.onComputerTurnEnd();
                    return;
                }
                
                System.out.println("Processing skip request...");
                
                // Finish the turn immediately
                finishComputerTurn();
                
            } catch (Exception e) {
                System.err.println("Error during skip: " + e.getMessage());
                e.printStackTrace();
                
                if (gameStateCallback != null) {
                    gameStateCallback.onError("Error skipping: " + e.getMessage());
                    gameStateCallback.onComputerTurnEnd();
                }
                
                isComputerTurnInProgress = false;
            }
        }, "SkipComputerTurnThread").start();
    }

    /**
     * Legacy method kept for backward compatibility.
     * @deprecated Use finishComputerTurn() instead
     */
    private void continueComputerTurnAfterRolls() {
        // Redirect to the new method
        finishComputerTurn();
    }

    /**
     * Checks if a computer turn is currently in progress.
     * @return true if computer turn is in progress, false otherwise
     */
    public boolean isComputerTurnInProgress() {
        return isComputerTurnInProgress;
    }
}
