package com.example.yahtzee.model;

import com.example.yahtzee.callbacks.GameStateCallback;
import java.util.*;

public class ComputerPlayer extends Player implements ComputerPlayerActions {
    // Thresholds for different category types
    private static final double YAHTZEE_THRESHOLD = 0.7;  // More aggressive for Yahtzee
    private static final double STRAIGHT_THRESHOLD = 0.5; // More aggressive for straights
    private static final double FULL_HOUSE_THRESHOLD = 0.5;
    private static final double KIND_THRESHOLD = 0.4;

    // Upper section bonus strategy - prioritize completing upper section
    private static final double UPPER_SECTION_BONUS_WEIGHT = 1.2;

    // Track explanations and prevent repetition
    private List<String> explanations = new ArrayList<>();
    private Set<String> explanationTopics = new HashSet<>();
    private static final int MAX_EXPLANATIONS = 10;
    
    // Game state
    private GameStateCallback gameStateCallback;
    private ScoreCard scoreCard;
    private ScoreCategory currentTargetCategory = null;
    private int rollCount = 0;
    private Map<String, Object> turnContext = new HashMap<>();

    // Track game progress
    private int turnNumber = 0;
    private boolean earlyGame = true;
    private boolean isFirstRoll = true;

    public ComputerPlayer(GameStateCallback callback) {
        super("Computer");
        this.gameStateCallback = callback;
    }

    private void addExplanation(String title, String explanation) {
        // Don't repeat the same topic within a turn
        String topicKey = title + ":" + explanation.substring(0, Math.min(20, explanation.length()));
        if (explanationTopics.contains(topicKey)) {
            return;
        }
        explanationTopics.add(topicKey);
        
        // Format explanation concisely
        String fullExplanation = title + ": " + explanation;
        
        // Trim long explanations
        if (fullExplanation.length() > 60) {
            fullExplanation = fullExplanation.substring(0, 57) + "...";
        }
        
        explanations.add(fullExplanation);
        
        // Trim explanations list if it exceeds the maximum size
        if (explanations.size() > MAX_EXPLANATIONS) {
            explanations = new ArrayList<>(explanations.subList(explanations.size() - MAX_EXPLANATIONS, explanations.size()));
        }
        
        if (gameStateCallback != null) {
            gameStateCallback.onComputerTurnAnnouncement(explanations);
        }
    }

    @Override
    public void addExplanationMessage(String title, String explanation) {
        addExplanation(title, explanation);
    }

    @Override
    public String formatDiceValuesString(List<Integer> diceValues) {
        return formatDiceValues(diceValues);
    }

    @Override
    public boolean isComputer() {
        return true;
    }

    @Override
    public void takeTurn(Round round) {
        // Reset state for new turn
        explanations.clear();
        explanationTopics.clear();
        turnContext.clear();
        isFirstRoll = true;
        rollCount = 0;
        
        // Set up turn
        setCurrentRound(round);
        Turn turn = round.getCurrentTurn();
        scoreCard = round.getScoreCard(this);
        
        // Reset target category for new turn
        currentTargetCategory = null;
        
        // Update game progress tracking
        turnNumber++;
        earlyGame = turnNumber <= 6; // First 6 turns are considered early game
        
        // Simple turn start message
        addExplanation("Turn", "Turn " + turnNumber + "/13 - " + (earlyGame ? "Early" : "Late") + " game");
        
        // Add game state context
        addGameStateContext();
        
        // Add pre-roll strategy analysis
        performPreRollAnalysis();
    }
    
    /**
     * Add context about the current game state to help the player understand the computer's strategy
     */
    private void addGameStateContext() {
        if (scoreCard == null) return;
        
        // Check for upper section bonus potential
        int upperSectionScore = 0;
        int upperSectionFilled = 0;
        for (ScoreCategory category : ScoreCategory.values()) {
            if (category.isUpperSection() && scoreCard.isCategoryFilled(category)) {
                upperSectionScore += scoreCard.getScore(category);
                upperSectionFilled++;
            }
        }
        
        // Only show upper section bonus info if relevant
        if (upperSectionFilled >= 3) {
            int remaining = 63 - upperSectionScore; // 63 is the threshold for bonus
            if (remaining > 0) {
                addExplanation("Bonus", String.format("Need %d pts for upper bonus", remaining));
            } else {
                addExplanation("Bonus", "Upper bonus secured");
            }
        }
        
        // Count remaining categories
        int remainingCount = 0;
        for (ScoreCategory category : ScoreCategory.values()) {
            if (!scoreCard.isCategoryFilled(category)) {
                remainingCount++;
            }
        }
        addExplanation("Status", String.format("%d of 13 categories left", remainingCount));
    }
    
    /**
     * Provide analysis of strategic priorities before the first roll
     */
    private void performPreRollAnalysis() {
        if (scoreCard == null) return;
        
        // Count remaining categories
        List<ScoreCategory> remainingCategories = new ArrayList<>();
        for (ScoreCategory category : ScoreCategory.values()) {
            if (!scoreCard.isCategoryFilled(category)) {
                remainingCategories.add(category);
            }
        }
        
        // Determine strategic priorities based on what's left
        List<String> priorities = new ArrayList<>();
        
        // Check high-value categories remaining
        if (!scoreCard.isCategoryFilled(ScoreCategory.YAHTZEE)) {
            priorities.add("Yahtzee (50 pts)");
        }
        if (!scoreCard.isCategoryFilled(ScoreCategory.LARGE_STRAIGHT)) {
            priorities.add("Large Straight (40 pts)");
        }
        if (!scoreCard.isCategoryFilled(ScoreCategory.SMALL_STRAIGHT)) {
            priorities.add("Small Straight (30 pts)");
        }
        if (!scoreCard.isCategoryFilled(ScoreCategory.FULL_HOUSE)) {
            priorities.add("Full House (25 pts)");
        }
        
        // Check for upper section bonus potential
        int upperSectionScore = 0;
        int upperSectionFilled = 0;
        int upperSectionRemaining = 0;
        for (ScoreCategory category : ScoreCategory.values()) {
            if (category.isUpperSection()) {
                if (scoreCard.isCategoryFilled(category)) {
                    upperSectionScore += scoreCard.getScore(category);
                    upperSectionFilled++;
                } else {
                    upperSectionRemaining++;
                }
            }
        }
        
        // Build strategic analysis message
        StringBuilder strategyMsg = new StringBuilder();
        
        // Add remaining turn count
        strategyMsg.append(String.format("Turn %d of 13, ", turnNumber));
        
        // Add bonus status if relevant
        if (upperSectionScore >= 63) {
            strategyMsg.append("Upper bonus secured! ");
        } else if (upperSectionFilled > 0) {
            int needed = 63 - upperSectionScore;
            double avgNeeded = upperSectionRemaining > 0 ? 
                (double)needed / upperSectionRemaining : 0;
                
            if (upperSectionRemaining > 0 && avgNeeded <= 10.5) {
                strategyMsg.append(String.format("On track for bonus (need avg %.1f per category). ", avgNeeded));
            } else if (upperSectionRemaining > 0) {
                strategyMsg.append(String.format("Need avg %.1f per remaining upper category for bonus. ", avgNeeded));
            }
        }
        
        // Add priority list
        if (!priorities.isEmpty()) {
            strategyMsg.append("Priorities: ");
            for (int i = 0; i < Math.min(3, priorities.size()); i++) {
                if (i > 0) strategyMsg.append(", ");
                strategyMsg.append(priorities.get(i));
            }
        }
        
        addExplanation("Pre-Roll Strategy", strategyMsg.toString());
        
        // Select initial category to pursue
        selectPreRollTargetCategory();
    }
    
    /**
     * Select a target category before the first roll based on game state
     */
    private void selectPreRollTargetCategory() {
        if (scoreCard == null) return;
        
        // Simple scoring for each category based on game context
        Map<ScoreCategory, Integer> categoryScores = new HashMap<>();
        
        // Score remaining categories based on value and game state
        for (ScoreCategory category : ScoreCategory.values()) {
            if (scoreCard.isCategoryFilled(category)) continue;
            
            int baseScore = 0;
            
            // Base value of the category
            switch (category) {
                case YAHTZEE:
                    baseScore = 50;
                    break;
                case FOUR_OF_A_KIND:
                    baseScore = 35; // Increased from 25
                    break;
                case THREE_OF_A_KIND:
                    baseScore = 30; // Increased from 20
                    break;
                case FULL_HOUSE:
                    baseScore = 28; // Increased from 25
                    break;
                case LARGE_STRAIGHT:
                    baseScore = 25; // Decreased from 40
                    break;
                case SMALL_STRAIGHT:
                    baseScore = 20; // Decreased from 30
                    break;
                case ONES:
                    baseScore = 5;
                    break;
                case TWOS:
                    baseScore = 10;
                    break;
                case THREES:
                    baseScore = 15;
                    break;
                case FOURS:
                    baseScore = 20;
                    break;
                case FIVES:
                    baseScore = 25;
                    break;
                case SIXES:
                    baseScore = 30;
                    break;
                default:
                    baseScore = 10;
                    break;
            }
            
            // Apply adjustments based on game state
            
            // Upper section bonus strategy - prioritize if needed
            if (needsUpperSectionBonus() && category.isUpperSection()) {
                baseScore = (int)(baseScore * 1.2);
            }
            
            // Early vs late game
            if (earlyGame && category.isUpperSection()) {
                baseScore = (int)(baseScore * 1.1); // Slightly favor upper section early
            } else if (!earlyGame && category == ScoreCategory.YAHTZEE) {
                baseScore = (int)(baseScore * 1.2); // More aggressive for Yahtzee later
            }
            
            // If we're getting close to the end, prioritize highest value categories
            int turnsRemaining = 13 - turnNumber;
            if (turnsRemaining <= 4) {
                if (category == ScoreCategory.YAHTZEE || 
                    category == ScoreCategory.LARGE_STRAIGHT ||
                    category == ScoreCategory.SMALL_STRAIGHT) {
                    baseScore = (int)(baseScore * 1.3);
                }
            }
            
            categoryScores.put(category, baseScore);
        }
        
        // Find highest scoring category as initial target
        ScoreCategory bestCategory = null;
        int bestScore = 0;
        
        for (Map.Entry<ScoreCategory, Integer> entry : categoryScores.entrySet()) {
            if (entry.getValue() > bestScore) {
                bestScore = entry.getValue();
                bestCategory = entry.getKey();
            }
        }
        
        // Set as target category
        if (bestCategory != null) {
            currentTargetCategory = bestCategory;
            
            // Explain the selected target category
            String categoryName = bestCategory.getDisplayName();
            String reason;
            
            if (bestCategory.isUpperSection()) {
                if (needsUpperSectionBonus()) {
                    reason = "important for upper section bonus";
                } else {
                    reason = "good early game strategy";
                }
            } else {
                switch (bestCategory) {
                    case YAHTZEE:
                        reason = "highest scoring category";
                        break;
                    case LARGE_STRAIGHT:
                    case SMALL_STRAIGHT:
                        reason = "high value straight";
                        break;
                    case FULL_HOUSE:
                        reason = "reliable 25 points";
                        break;
                    case FOUR_OF_A_KIND:
                        reason = "potentially high score";
                        break;
                    case THREE_OF_A_KIND:
                        reason = "easier to achieve";
                        break;
                    default:
                        reason = "strategic choice";
                        break;
                }
            }
            
            // Add explanation of target
            addExplanation("Initial Target", String.format("Will focus on %s (%s)", 
                categoryName, reason));
                
            // Add dice holding strategy explanation
            addPreRollHoldingStrategy(bestCategory);
        }
    }
    
    /**
     * Explain what dice we'll try to hold for this category
     */
    private void addPreRollHoldingStrategy(ScoreCategory category) {
        StringBuilder strategy = new StringBuilder();
        
        switch (category) {
            case ONES:
            case TWOS:
            case THREES:
            case FOURS:
            case FIVES:
            case SIXES:
                strategy.append("Hold all " + category.getDisplayName().toLowerCase());
                break;
            case THREE_OF_A_KIND:
                strategy.append("Hold all pairs and triplets (any value)");
                break;
            case FOUR_OF_A_KIND:
                strategy.append("Hold all pairs, triplets, quads (any value)");
                break;
            case FULL_HOUSE:
                strategy.append("Hold pairs and triplets");
                break;
            case SMALL_STRAIGHT:
            case LARGE_STRAIGHT:
                strategy.append("Hold sequences (no duplicates)");
                break;
            case YAHTZEE:
                strategy.append("Hold most frequent value");
                break;
            default:
                strategy.append("Hold valuable dice");
                break;
        }
        
        addExplanation("Hold", strategy.toString());
        
        // Actually apply the holding strategy for any non-zero dice
        List<Integer> diceValues = getAllDice();
        
        // Only attempt to hold dice if we have non-zero values
        if (!diceValues.isEmpty() && diceValues.stream().anyMatch(d -> d > 0)) {
            List<Integer> diceToHold = decideDiceToHold(diceValues, category);
            if (!diceToHold.isEmpty()) {
                holdDice(diceToHold);
                addExplanation("Holding", formatDicePositions(diceToHold, diceValues));
            }
        }
    }

    /**
     * Public method to analyze current dice and update strategy.
     * Called by GameController to show analysis during computer turn.
     */
    public void analyzeCurrentState() {
        rollCount++;
        isFirstRoll = rollCount == 1;
        
        // If this is the first roll and we already have a target category from pre-roll analysis,
        // check if there are any held dice that need to be released
        if (isFirstRoll && currentTargetCategory != null) {
            List<Integer> heldIndices = getHeldDiceIndices();
            List<Integer> diceValues = getAllDice();
            
            // If we have dice values and some are held, check if our strategy has changed
            if (!diceValues.isEmpty() && !heldIndices.isEmpty() && 
                diceValues.stream().anyMatch(d -> d > 0)) {
                // Get dice to hold based on current strategy
                List<Integer> diceToHold = decideDiceToHold(diceValues, currentTargetCategory);
                
                // If the strategy changed, release all dice and hold the new ones
                if (!heldIndices.equals(diceToHold)) {
                    releaseDice(heldIndices);
                    if (!diceToHold.isEmpty()) {
                        holdDice(diceToHold);
                        addExplanation("Updated Hold", String.format("Now holding %s", 
                            formatDicePositions(diceToHold, diceValues)));
                    }
                }
            }
        }
        
        // Analyze current dice and update strategy
        analyzeDiceAndUpdateStrategy();
        
        // Check if strategy has changed after analysis and update held dice
        updateHeldDiceBasedOnStrategy();
    }
    
    /**
     * Update the held dice based on the current strategy
     * This ensures the computer adapts to changing dice values and strategy
     */
    private void updateHeldDiceBasedOnStrategy() {
        if (currentTurn == null || currentTargetCategory == null) return;
        
        List<Integer> diceValues = getAllDice();
        if (diceValues.isEmpty() || diceValues.stream().allMatch(d -> d == 0)) {
            return; // No dice to hold
        }
        
        // Get current held dice
        List<Integer> currentlyHeldDice = getHeldDiceIndices();
        
        // Determine which dice should be held based on current strategy
        List<Integer> diceToHold = decideDiceToHold(diceValues, currentTargetCategory);
        
        // If the currently held dice are different from what our strategy suggests,
        // update them to match the strategy
        if (!currentlyHeldDice.equals(diceToHold)) {
            // Only proceed if we're actually changing something
            if (!Collections.disjoint(currentlyHeldDice, diceToHold) || 
                (!currentlyHeldDice.isEmpty() && !diceToHold.isEmpty())) {
                
                // Release all currently held dice
                if (!currentlyHeldDice.isEmpty()) {
                    releaseDice(currentlyHeldDice);
                }
                
                // Hold the dice according to our strategy
                if (!diceToHold.isEmpty()) {
                    holdDice(diceToHold);
                    addExplanation("Strategy Update", String.format("Changed to holding %s", 
                        formatDicePositions(diceToHold, diceValues)));
                } else {
                    addExplanation("Strategy Update", "Released all held dice");
                }
            }
        }
    }

    private void analyzeDiceAndUpdateStrategy() {
        if (currentTurn == null) return;
        
        List<Integer> currentDice = getCurrentTurn().getDice();
        
        // If all dice are 0, we need to roll first
        boolean allZeros = currentDice.stream().allMatch(d -> d == 0);
        if (allZeros) {
            return; // Don't analyze with zero dice
        }
        
        List<Integer> diceValues = getAllDice();
        if (diceValues.isEmpty() || diceValues.stream().allMatch(d -> d == 0)) {
            return; // Don't analyze with empty or zero dice
        }
        
        // Remember previous target category to detect changes
        ScoreCategory previousTarget = currentTargetCategory;
        
        // Check for Yahtzee as a special case
        boolean isYahtzee = isYahtzee(diceValues);
        if (isYahtzee && !scoreCard.isCategoryFilled(ScoreCategory.YAHTZEE)) {
            addExplanation("Analysis", "YAHTZEE! Will score for 50 points!");
            currentTargetCategory = ScoreCategory.YAHTZEE;
            holdAllDice(diceValues);
            return;
        }
        
        // Analyze current roll
        Map<ScoreCategory, Double> probabilities = analyzePossibleMoves(diceValues);
        
        // Find best available categories
        List<Map.Entry<ScoreCategory, Double>> bestCategories = probabilities.entrySet().stream()
            .filter(e -> !scoreCard.isScored(e.getKey()))
            .sorted((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()))
            .limit(3)
            .collect(java.util.stream.Collectors.toList());
            
        // Skip analysis if we've already explained this dice combination
        String diceKey = formatDiceValues(diceValues);
        if (!turnContext.containsKey("analyzed_" + diceKey)) {
            turnContext.put("analyzed_" + diceKey, true);
            
        // Build compact analysis
        StringBuilder analysis = new StringBuilder();
        for (int i = 0; i < Math.min(2, bestCategories.size()); i++) {
            Map.Entry<ScoreCategory, Double> entry = bestCategories.get(i);
            int score = calculateScore(diceValues, entry.getKey());
            if (i > 0) analysis.append(" | ");
            analysis.append(String.format("%s: %d pts", 
                entry.getKey().getDisplayName(),
                score));
        }
        
        if (!analysis.toString().isEmpty()) {
            addExplanation("Options", analysis.toString());
        }
        }
        
        // Decide strategy
        ScoreCategory targetCategory = decideBestCategory(probabilities);
        if (targetCategory != null) {
            int potentialScore = calculateScore(diceValues, targetCategory);
            
            // Only update strategy if it's changed or first analysis
            boolean strategyChanged = (currentTargetCategory != targetCategory);
            if (strategyChanged || !turnContext.containsKey("strategy_explained")) {
                turnContext.put("strategy_explained", true);
                currentTargetCategory = targetCategory;
                
                // Update held dice based on strategy - releaseDice first, then holdDice
                List<Integer> heldDiceIndices = getHeldDiceIndices();
            List<Integer> diceToHold = decideDiceToHold(diceValues, targetCategory);
                
                // Only make changes if the strategy has actually changed
                if (strategyChanged) {
                    // Update held dice only if they're different from what we want
                    if (!heldDiceIndices.equals(diceToHold)) {
                        if (!heldDiceIndices.isEmpty()) {
                            releaseDice(heldDiceIndices); // Clear previous holds
                        }
                        
                        if (!diceToHold.isEmpty()) {
                            holdDice(diceToHold);
                        }
                    }
                }
            
            StringBuilder strategy = new StringBuilder();
            strategy.append(String.format("%s (%d pts)", 
                targetCategory.getDisplayName(), potentialScore));
            
            if (!diceToHold.isEmpty()) {
                strategy.append(String.format(" - Hold: %s", 
                    formatDicePositions(diceToHold, diceValues)));
            }
            
            addExplanation("Target", strategy.toString());
                
                // Add helpful context for why this choice was made
                explainCategoryChoice(targetCategory, diceValues);
            }
        }
    }
    
    /**
     * Provide contextual explanation for category choice
     */
    private void explainCategoryChoice(ScoreCategory category, List<Integer> diceValues) {
        // Only explain if we haven't explained this category before
        if (turnContext.containsKey("explained_" + category)) {
            return;
        }
        turnContext.put("explained_" + category, true);
        
        // Special case for straights - use detailed straight explanation
        if (category == ScoreCategory.SMALL_STRAIGHT || category == ScoreCategory.LARGE_STRAIGHT) {
            explainStraightStrategy(diceValues, category);
            return;
        }
        
        // Special case for full house - use detailed full house explanation
        if (category == ScoreCategory.FULL_HOUSE) {
            explainFullHouseStrategy(diceValues);
            return;
        }
        
        // Continue with standard explanation for other categories
        StringBuilder explanation = new StringBuilder();
        
        switch (category) {
            case YAHTZEE:
                explanation.append("Worth 50 pts - highest possible");
                break;
            case LARGE_STRAIGHT:
                explanation.append("5 sequential dice = 40 pts");
                break;
            case SMALL_STRAIGHT:
                explanation.append("4 sequential dice = 30 pts");
                break;
            case FULL_HOUSE:
                explanation.append("Three + pair = 25 pts");
                break;
            case FOUR_OF_A_KIND:
                explanation.append("Four matching dice");
                break;
            case THREE_OF_A_KIND:
                explanation.append("Three matching dice");
                break;
            case ONES:
            case TWOS:
            case THREES:
            case FOURS:
            case FIVES:
            case SIXES:
                int value = category.ordinal() + 1;
                int count = countValue(diceValues, value);
                explanation.append(String.format("Have %d of %d", count, 5));
                break;
        }
        
        if (!explanation.toString().isEmpty()) {
            addExplanation("About " + category.getDisplayName(), explanation.toString());
        }
    }
    
    private int countValue(List<Integer> dice, int value) {
        return (int) dice.stream().filter(d -> d == value).count();
    }
    
    private boolean isYahtzee(List<Integer> dice) {
        if (dice.isEmpty()) return false;
        int first = dice.get(0);
        return dice.stream().allMatch(d -> d == first);
    }
    
    private void holdAllDice(List<Integer> diceValues) {
        List<Integer> allIndices = new ArrayList<>();
        for (int i = 0; i < diceValues.size(); i++) {
            allIndices.add(i);
        }
        holdDice(allIndices);
    }
    
    @Override
    public boolean shouldRollAgain(Round round) {
        if (currentTurn == null || currentTurn.getRollsLeft() <= 0) {
            return false;
        }
        
        // Check if we have actual dice values
        List<Integer> diceValues = getAllDice();
        if (diceValues.isEmpty() || diceValues.stream().allMatch(d -> d == 0)) {
            addExplanation("Decision", "First roll of turn");
            return true; // Always roll if we have no valid dice
        }
        
        // Check for Yahtzee - never reroll if we have a Yahtzee
        if (isYahtzee(diceValues) && !scoreCard.isCategoryFilled(ScoreCategory.YAHTZEE)) {
            addExplanation("Decision", "Keeping Yahtzee (50 points)!");
            return false;
        }
        
        // Special case for 4 of a kind when Yahtzee is still open
        boolean hasFourOfAKind = hasFourOfAKind(diceValues);
        if (hasFourOfAKind && !scoreCard.isCategoryFilled(ScoreCategory.YAHTZEE)) {
            // Always roll again if we have rolls left - try for Yahtzee
            if (currentTurn.getRollsLeft() > 0) {
                addExplanation("Decision", "Have four of a kind - rolling for Yahtzee!");
                return true;
            }
        }
        
        // Analyze current state and update strategy
        analyzeDiceAndUpdateStrategy();
        
        // Get current dice values and analyze
        Map<ScoreCategory, Double> probabilities = analyzePossibleMoves(diceValues);
        double bestProbability = 0;
        ScoreCategory bestCategory = null;
        
        for (Map.Entry<ScoreCategory, Double> entry : probabilities.entrySet()) {
            if (!scoreCard.isScored(entry.getKey()) && entry.getValue() > bestProbability) {
                bestProbability = entry.getValue();
                bestCategory = entry.getKey();
            }
        }
        
        // Adjust probability threshold based on category and game state
        double probabilityThreshold = 0.8; // Default threshold
        
        if (bestCategory != null) {
            // Adjust threshold based on category value
            switch (bestCategory) {
                case YAHTZEE:
                    probabilityThreshold = 0.5; // Even more aggressive for Yahtzee
                    break;
                case FOUR_OF_A_KIND:
                    probabilityThreshold = 0.55; // More aggressive for Four of a Kind (decreased from 0.7)
                    break;
                case THREE_OF_A_KIND:
                    probabilityThreshold = 0.6; // Aggressive for Three of a Kind (new value)
                    break;
                case FULL_HOUSE:
                    probabilityThreshold = 0.65; // Somewhat more aggressive for these
                    break;
                case LARGE_STRAIGHT:
                    probabilityThreshold = 0.75; // Less aggressive for Large Straight (increased from 0.6)
                    break;
                case SMALL_STRAIGHT:
                    probabilityThreshold = 0.8; // Less aggressive for Small Straight (increased from 0.65)
                    break;
                default:
                    // Keep default threshold for other categories
                    break;
            }
            
            // Calculate relative score (current vs max possible)
            int maxScore = getMaxPossibleScore(bestCategory);
            int currentScore = scoreCard.calculateScore(bestCategory, diceValues);
            double percentOfMax = (double) currentScore / maxScore;
            
            // Adjust based on current score relative to max
            if (percentOfMax > 0.8) {
                probabilityThreshold -= 0.1; // More likely to keep if already at 80% of max
            } else if (percentOfMax < 0.5) {
                probabilityThreshold += 0.1; // More likely to roll if below 50% of max
            }
            
            // Adjust based on rolls left
            if (currentTurn.getRollsLeft() == 1) {
                probabilityThreshold -= 0.2; // Last roll - be more willing to keep
            } else if (currentTurn.getRollsLeft() == 2) {
                probabilityThreshold -= 0.1; // First roll - be more willing to roll again
            }
            
            // Adjust for early vs late game
            if (!earlyGame && bestCategory.isUpperSection()) {
                probabilityThreshold -= 0.1; // Less picky about upper section later in game
            }
        }
        
        // Clamp threshold to reasonable values
        probabilityThreshold = Math.max(0.4, Math.min(0.9, probabilityThreshold));
        
        // Make the decision
        boolean shouldRoll = bestProbability < probabilityThreshold && currentTurn.getRollsLeft() > 0;
        
        // Only explain the decision if we haven't already for this exact situation
        String decisionKey = String.format("decision_%s_%.2f", 
            (bestCategory != null ? bestCategory.name() : "NONE"), 
            bestProbability);
            
        if (!turnContext.containsKey(decisionKey)) {
            turnContext.put(decisionKey, true);
        
        StringBuilder decision = new StringBuilder();
        if (shouldRoll) {
            decision.append(String.format("Roll again (best: %.0f%%)", bestProbability * 100));
        } else {
            decision.append(String.format("Keep roll (best: %.0f%%)", bestProbability * 100));
        }
        
        addExplanation("Decision", decision.toString());
        }
        
        return shouldRoll;
    }

    private String formatDiceValues(List<Integer> diceValues) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < diceValues.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(diceValues.get(i));
        }
        return sb.toString();
    }

    private String formatDicePositions(List<Integer> positions, List<Integer> diceValues) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < positions.size(); i++) {
            if (i > 0) sb.append(" ");
            int pos = positions.get(i);
            sb.append(String.format("D%d=%d", pos + 1, diceValues.get(pos)));
        }
        return sb.toString();
    }

    @Override
    public Map<ScoreCategory, SuggestionResult> analyzePossibleMoves(ScoreCard scoreCard) {
        Map<ScoreCategory, SuggestionResult> suggestions = new HashMap<>();
        List<Integer> diceValues = getAllDice();
        
        for (ScoreCategory category : ScoreCategory.values()) {
            if (!scoreCard.isScored(category)) {
                double probability = calculateProbability(diceValues, category);
                int score = calculateScore(diceValues, category);
                String reason = String.format("Probability: %.2f%%", probability * 100);
                suggestions.put(category, new SuggestionResult(category, score, reason));
            }
        }
        
        return suggestions;
    }

    private Map<ScoreCategory, Double> analyzePossibleMoves(List<Integer> diceValues) {
        Map<ScoreCategory, Double> probabilities = new HashMap<>();
        
        // Ensure scoreCard is initialized
        if (scoreCard == null && getCurrentRound() != null) {
            scoreCard = getCurrentRound().getScoreCard(this);
        }
        
        for (ScoreCategory category : ScoreCategory.values()) {
            if (scoreCard == null || !scoreCard.isScored(category)) {
                double probability = calculateProbability(diceValues, category);
                probabilities.put(category, probability);
            }
        }
        
        return probabilities;
    }

    protected Map<ScoreCategory, Double> getScoreProbabilities(List<Integer> diceValues) {
        Map<ScoreCategory, SuggestionResult> suggestions = analyzePossibleMoves(scoreCard);
        Map<ScoreCategory, Double> probabilities = new HashMap<>();
        
        for (Map.Entry<ScoreCategory, SuggestionResult> entry : suggestions.entrySet()) {
            // Convert suggestion scores to probabilities (0.0 to 1.0)
            int score = entry.getValue().getScore();
            int maxScore = getMaxPossibleScore(entry.getKey());
            double probability = maxScore > 0 ? (double) score / maxScore : 0.0;
            probabilities.put(entry.getKey(), probability);
        }
        
        return probabilities;
    }

    private double calculateProbability(List<Integer> diceValues, ScoreCategory category) {
        // Don't calculate probabilities for zero dice
        if (diceValues.stream().allMatch(d -> d == 0)) {
            return 0.0;
        }
        
        // Special case for straights: calculate better probability based on potential
        if (category == ScoreCategory.SMALL_STRAIGHT || category == ScoreCategory.LARGE_STRAIGHT) {
            return calculateStraightProbability(diceValues, category);
        }
        
        // Special case for full house: calculate better probability based on pairs/3 of a kind
        if (category == ScoreCategory.FULL_HOUSE) {
            return calculateFullHouseProbability(diceValues);
        }
        
        // Calculate probability based on current dice values and category
        int currentScore = calculateScore(diceValues, category);
        int maxPossibleScore = getMaxPossibleScore(category);
        
        if (maxPossibleScore == 0) return 0;
        return (double) currentScore / maxPossibleScore;
    }
    
    /**
     * Calculate probability for straights with smarter detection for potential straights
     */
    private double calculateStraightProbability(List<Integer> diceValues, ScoreCategory category) {
        // Check if we already have the straight
        if (category == ScoreCategory.SMALL_STRAIGHT && hasSmallStraight(diceValues)) {
            return 1.0;
        }
        if (category == ScoreCategory.LARGE_STRAIGHT && hasLargeStraight(diceValues)) {
            return 1.0;
        }
        
        // Count unique values
        Set<Integer> uniqueValues = new HashSet<>(diceValues);
        
        // Create a frequency map to identify duplicates
        Map<Integer, Integer> valueCounts = new HashMap<>();
        for (int die : diceValues) {
            valueCounts.put(die, valueCounts.getOrDefault(die, 0) + 1);
        }
        
        // Create a sorted list of unique values
        List<Integer> sortedUniqueValues = new ArrayList<>(uniqueValues);
        Collections.sort(sortedUniqueValues);
        
        // Check for potential small straight (1-2-3-4 or 2-3-4-5 or 3-4-5-6)
        if (category == ScoreCategory.SMALL_STRAIGHT) {
            // Check how many values we have in sequence
            int[] possibleSmallStraights = {
                countSequentialValues(sortedUniqueValues, 1, 4), // 1-2-3-4
                countSequentialValues(sortedUniqueValues, 2, 5), // 2-3-4-5
                countSequentialValues(sortedUniqueValues, 3, 6)  // 3-4-5-6
            };
            
            // Find the best potential straight
            int bestSequenceLength = Math.max(possibleSmallStraights[0], 
                                     Math.max(possibleSmallStraights[1], possibleSmallStraights[2]));
            
            // Calculate how many dice we need to reroll
            int rerollsNeeded = 4 - bestSequenceLength;
            
            // If we have duplicates we could reroll, increase the chance
            int duplicateCount = diceValues.size() - uniqueValues.size();
            
            // Probability is based on how many values we have and rerolls left
            if (bestSequenceLength == 3) {
                // We need only one more number for a small straight
                // If we have duplicates we can reroll, that's better
                if (duplicateCount > 0) {
                    // Higher chance with more rerolls, but lower than before
                    return 0.4 + (0.1 * Math.min(currentTurn.getRollsLeft(), 1)); // Reduced from 0.6
                } else {
                    // We need to reroll one die to get the right number
                    // Each die has a 1/6 chance of getting the needed value
                    // With 1 reroll of 1 die, probability is 1/6 = 0.167
                    // With 2 rerolls, probability increases
                    return 0.3 + (0.1 * Math.min(currentTurn.getRollsLeft(), 2)); // Reduced from 0.4
                }
            } else if (bestSequenceLength == 2) {
                // Need 2 more numbers
                return 0.15 + (0.05 * Math.min(currentTurn.getRollsLeft(), 2)); // Reduced from 0.2
            }
            
            // Default low probability
            return 0.05; // Reduced from 0.1
        }
        
        // Check for potential large straight (1-2-3-4-5 or 2-3-4-5-6)
        if (category == ScoreCategory.LARGE_STRAIGHT) {
            // Check how many values we have in sequence
            int sequenceLength1to5 = countSequentialValues(sortedUniqueValues, 1, 5); // 1-2-3-4-5
            int sequenceLength2to6 = countSequentialValues(sortedUniqueValues, 2, 6); // 2-3-4-5-6
            
            // Find the best potential straight
            int bestSequenceLength = Math.max(sequenceLength1to5, sequenceLength2to6);
            
            // Calculate how many dice we need to reroll
            int rerollsNeeded = 5 - bestSequenceLength;
            
            // If we have duplicates we could reroll, increase the chance
            int duplicateCount = diceValues.size() - uniqueValues.size();
            
            // Probability calculation based on sequence length
            if (bestSequenceLength == 4) {
                // We need only one more number
                if (duplicateCount > 0) {
                    // Better chance with duplicates to reroll, but lower than before
                    return 0.35 + (0.1 * Math.min(currentTurn.getRollsLeft(), 2)); // Reduced from 0.5
                } else {
                    // Need to get lucky with one die
                    return 0.2 + (0.1 * Math.min(currentTurn.getRollsLeft(), 2)); // Reduced from 0.3
                }
            } else if (bestSequenceLength == 3) {
                // Need 2 more specific numbers
                if (duplicateCount >= 2) {
                    return 0.2 + (0.05 * Math.min(currentTurn.getRollsLeft(), 2)); // Reduced from 0.3
                } else {
                    return 0.15 + (0.05 * Math.min(currentTurn.getRollsLeft(), 2)); // Reduced from 0.2
                }
            }
            
            // Default low probability
            return 0.05; // Reduced from 0.1
        }
        
        // Fallback to default calculation
        int currentScore = calculateScore(diceValues, category);
        int maxPossibleScore = getMaxPossibleScore(category);
        return (double) currentScore / maxPossibleScore;
    }
    
    /**
     * Count how many sequential values from the list are in the range [start, end]
     */
    private int countSequentialValues(List<Integer> sortedValues, int start, int end) {
        int count = 0;
        for (int i = start; i <= end; i++) {
            if (sortedValues.contains(i)) {
                count++;
            }
        }
        return count;
    }

    private int getMaxPossibleScore(ScoreCategory category) {
        switch (category) {
            case ONES: return 5;
            case TWOS: return 10;
            case THREES: return 15;
            case FOURS: return 20;
            case FIVES: return 25;
            case SIXES: return 30;
            case THREE_OF_A_KIND: return 30;
            case FOUR_OF_A_KIND: return 30;
            case FULL_HOUSE: return 25;
            case SMALL_STRAIGHT: return 30;
            case LARGE_STRAIGHT: return 40;
            case YAHTZEE: return 50;
            default: return 0;
        }
    }

    private ScoreCategory decideBestCategory(Map<ScoreCategory, Double> probabilities) {
        ScoreCategory bestCategory = null;
        double highestExpectedValue = 0;
        
        // When out of rolls, prioritize actual scores over probabilities
        if (currentTurn != null && currentTurn.getRollsLeft() == 0) {
            ScoreCategory highestScoringCategory = findHighestScoringCategory(getAllDice());
            if (highestScoringCategory != null) {
                return highestScoringCategory;
            }
        }
        
        // If we already have a target category with good probability, stick with it
        if (currentTargetCategory != null && 
            probabilities.containsKey(currentTargetCategory) && 
            probabilities.get(currentTargetCategory) >= 0.4) {
            return currentTargetCategory;
        }
        
        // Check for 4 of a kind and prioritize Yahtzee if available
        List<Integer> diceValues = getAllDice();
        boolean hasFourOfAKind = hasFourOfAKind(diceValues);
        
        // Handle special case: 4 of a kind with no rolls left
        if (hasFourOfAKind && !isYahtzee(diceValues) && currentTurn != null && currentTurn.getRollsLeft() == 0) {
            // Get the value of the 4 of a kind
            Map<Integer, Integer> valueCounts = new HashMap<>();
            for (int die : diceValues) {
                valueCounts.put(die, valueCounts.getOrDefault(die, 0) + 1);
            }
            
            int fourOfAKindValue = 0;
            for (Map.Entry<Integer, Integer> entry : valueCounts.entrySet()) {
                if (entry.getValue() >= 4) {
                    fourOfAKindValue = entry.getKey();
                    break;
                }
            }
            
            // First check if FOUR_OF_A_KIND is available - that's the best option
            if (!scoreCard.isCategoryFilled(ScoreCategory.FOUR_OF_A_KIND)) {
                addExplanation("Priority", String.format("Have four %ds - scoring as Four of a Kind (%d points)", 
                    fourOfAKindValue, fourOfAKindValue * 4 + (diceValues.stream().mapToInt(Integer::intValue).sum() - fourOfAKindValue * 4)));
                return ScoreCategory.FOUR_OF_A_KIND;
            }
            
            // Next, check the corresponding upper section category
            ScoreCategory upperCategory = null;
            switch (fourOfAKindValue) {
                case 1: upperCategory = ScoreCategory.ONES; break;
                case 2: upperCategory = ScoreCategory.TWOS; break;
                case 3: upperCategory = ScoreCategory.THREES; break;
                case 4: upperCategory = ScoreCategory.FOURS; break;
                case 5: upperCategory = ScoreCategory.FIVES; break;
                case 6: upperCategory = ScoreCategory.SIXES; break;
            }
            
            if (upperCategory != null && !scoreCard.isCategoryFilled(upperCategory)) {
                addExplanation("Priority", String.format("Have four %ds - scoring in %s category (%d points)", 
                    fourOfAKindValue, upperCategory.getDisplayName(), fourOfAKindValue * 4));
                return upperCategory;
            }
            
            // If we have THREE_OF_A_KIND available, use that
            if (!scoreCard.isCategoryFilled(ScoreCategory.THREE_OF_A_KIND)) {
                addExplanation("Priority", String.format("Have four %ds - scoring as Three of a Kind (%d points)", 
                    fourOfAKindValue, fourOfAKindValue * 3));
                return ScoreCategory.THREE_OF_A_KIND;
            }
            
            // Now check for FULL_HOUSE if available - it's a guaranteed 25 points
            if (!scoreCard.isCategoryFilled(ScoreCategory.FULL_HOUSE)) {
                addExplanation("Priority", "Using four of a kind to score Full House (25 points)");
                return ScoreCategory.FULL_HOUSE;
            }
            
            // Only use Yahtzee if nothing better is available and it's our last option
            // Find the count of unfilled categories
            long unfilledCount = probabilities.keySet().stream()
                .filter(cat -> !scoreCard.isCategoryFilled(cat))
                .count();
                
            if (unfilledCount <= 2 && !scoreCard.isCategoryFilled(ScoreCategory.YAHTZEE)) {
                addExplanation("Last Resort", "Using four of a kind in Yahtzee category (no better options)");
                return ScoreCategory.YAHTZEE;
            }
            
            // Otherwise, don't use Yahtzee and find the best option below
        }
        
        if (hasFourOfAKind && currentTurn != null && currentTurn.getRollsLeft() > 0) {
            // If we have 4 of a kind and rolls left, prioritize YAHTZEE first if it's not filled
            if (!scoreCard.isCategoryFilled(ScoreCategory.YAHTZEE)) {
                addExplanation("Priority", "Have four of a kind - going for Yahtzee with remaining rolls");
                return ScoreCategory.YAHTZEE;
            }
            // Otherwise continue with normal evaluation
        }
        
        // Check if we need upper section bonus
        boolean needUpperBonus = needsUpperSectionBonus();
        
        // First check if any category would currently give a non-zero score
        // and collect them for evaluation
        List<ScoreCategory> nonZeroCategories = new ArrayList<>();
        for (Map.Entry<ScoreCategory, Double> entry : probabilities.entrySet()) {
            ScoreCategory category = entry.getKey();
            
            // Skip already scored categories
            if (scoreCard.isScored(category)) continue;
            
            // Check if this category would currently score non-zero points
            int currentScore = calculateScore(diceValues, category);
            if (currentScore > 0) {
                nonZeroCategories.add(category);
            }
        }
        
        // Evaluate each category
        for (Map.Entry<ScoreCategory, Double> entry : probabilities.entrySet()) {
            ScoreCategory category = entry.getKey();
            double probability = entry.getValue();
            
            // Skip already scored categories
            if (scoreCard.isScored(category)) continue;
            
            // Avoid selecting YAHTZEE with 4 of a kind and no rolls left (would be 0 points)
            if (category == ScoreCategory.YAHTZEE && hasFourOfAKind && !isYahtzee(diceValues) && 
                currentTurn != null && currentTurn.getRollsLeft() == 0) {
                continue;
            }
            
            // If we have non-zero scoring categories available and out of rolls,
            // skip any that would score 0
            if (!nonZeroCategories.isEmpty() && currentTurn != null && currentTurn.getRollsLeft() == 0) {
                int currentScore = calculateScore(diceValues, category);
                if (currentScore == 0) continue;
            }
            
            // Skip if probability too low (except in last roll)
            if (probability < 0.2 && currentTurn.getRollsLeft() > 0) continue;
            
            int categoryValue = getMaxPossibleScore(category);
            
            // Apply bonus weight to high-value categories to prioritize them
            double valueMultiplier = 1.0;
            
            // Strategy adjustments based on category
            switch (category) {
                case YAHTZEE:
                    valueMultiplier = 1.8; // Highest priority for Yahtzee
                    break;
                case FOUR_OF_A_KIND:
                    valueMultiplier = 1.6; // Higher priority for Four of a Kind (increased from 1.2)
                    break;
                case THREE_OF_A_KIND:
                    valueMultiplier = 1.5; // Higher priority for Three of a Kind (new value)
                    break;
                case FULL_HOUSE:
                    valueMultiplier = 1.4; // Increased priority for Full House (increased from 1.2)
                    break;
                case LARGE_STRAIGHT:
                    valueMultiplier = 1.2; // Lower priority for Large Straight (decreased from 1.4)
                    break;
                case SMALL_STRAIGHT:
                    valueMultiplier = 1.1; // Lower priority for Small Straight (decreased from 1.3)
                    break;
                default:
                    // Upper section bonus strategy
                    if (category.isUpperSection() && needUpperBonus && earlyGame) {
                        valueMultiplier = UPPER_SECTION_BONUS_WEIGHT;
                    }
                    break;
            }
            
            // When out of rolls, heavily prioritize actual current score over potential
            if (currentTurn != null && currentTurn.getRollsLeft() == 0) {
                int currentScore = calculateScore(diceValues, category);
                double actualScoreRatio = (double) currentScore / categoryValue;
                valueMultiplier *= (1.0 + actualScoreRatio * 2.0); // More weight to actual score
            }
            
            // Late game strategy adjustments
            if (!earlyGame) {
                // In late game, be more willing to take what we can get
                valueMultiplier *= 1.1;
                
                // Especially for empty categories
                if (calculateScore(getAllDice(), category) == 0) {
                    valueMultiplier *= 0.8; // Less excited about scoring zero
                }
            }
            
            // Calculate expected value with the value multiplier
            double expectedValue = probability * categoryValue * valueMultiplier;
            
            // Track the highest expected value
            if (expectedValue > highestExpectedValue) {
                highestExpectedValue = expectedValue;
                bestCategory = category;
            }
        }
        
        // If we didn't find any category with sufficient probability,
        // fall back to the highest probability category
        if (bestCategory == null) {
            double bestScore = 0;
            
            // If we have non-zero scoring categories and are out of rolls, only consider those
            if (!nonZeroCategories.isEmpty() && currentTurn != null && currentTurn.getRollsLeft() == 0) {
                for (ScoreCategory category : nonZeroCategories) {
                    int score = calculateScore(diceValues, category);
                    if (score > bestScore) {
                        bestScore = score;
                        bestCategory = category;
                    }
                }
            }
            // Otherwise evaluate based on probability
            else {
            for (Map.Entry<ScoreCategory, Double> entry : probabilities.entrySet()) {
                    if (scoreCard.isScored(entry.getKey())) continue;
                    
                    // Skip Yahtzee with 4 of a kind and no rolls left
                    if (entry.getKey() == ScoreCategory.YAHTZEE && hasFourOfAKind && !isYahtzee(diceValues) && 
                        currentTurn != null && currentTurn.getRollsLeft() == 0) {
                        continue;
                    }
                    
                ScoreCategory category = entry.getKey();
                double probability = entry.getValue();
                int value = getMaxPossibleScore(category);
                
                // Score is a combination of probability and value
                double score = probability * Math.sqrt(value);
                
                if (score > bestScore) {
                    bestScore = score;
                    bestCategory = category;
                    }
                }
            }
        }
        
        // Log the decision only if it has changed or is new
        if (bestCategory != null && (currentTargetCategory != bestCategory || !turnContext.containsKey("target_explained"))) {
            turnContext.put("target_explained", true);
            currentTargetCategory = bestCategory;
            
            // Calculate how likely we are to achieve the target
            int maxValue = getMaxPossibleScore(bestCategory);
            int currentScore = scoreCard.calculateScore(bestCategory, getAllDice());
            double percentOfMax = (double) currentScore / maxValue * 100;
            
            addExplanation("Target", String.format("%s selected - %.0f%%", 
                bestCategory.getDisplayName(), percentOfMax));
        }
        
        // FINAL SAFETY CHECK: Never return a zero-scoring category if non-zero options are available
        if (bestCategory != null && currentTurn != null && currentTurn.getRollsLeft() == 0) {
            List<Integer> finalDice = getAllDice();
            int finalScore = scoreCard.calculateScore(bestCategory, finalDice);
            
            // If the selected category would score zero, check if we have better options
            if (finalScore == 0) {
                // Find any non-zero scoring categories
                List<ScoreCategory> nonZeroScoringCategories = new ArrayList<>();
                Map<ScoreCategory, Integer> categoryScores = new HashMap<>();
                
                for (ScoreCategory category : ScoreCategory.values()) {
                    if (!scoreCard.isCategoryFilled(category)) {
                        int score = scoreCard.calculateScore(category, finalDice);
                        categoryScores.put(category, score);
                        if (score > 0) {
                            nonZeroScoringCategories.add(category);
                        }
                    }
                }
                
                // If we have non-zero options, use the highest scoring one
                if (!nonZeroScoringCategories.isEmpty()) {
                    ScoreCategory betterCategory = null;
                    int highestScore = -1;
                    
                    for (ScoreCategory category : nonZeroScoringCategories) {
                        int score = categoryScores.get(category);
                        if (score > highestScore) {
                            highestScore = score;
                            betterCategory = category;
                        }
                    }
                    
                    if (betterCategory != null) {
                        addExplanation("Score Override", String.format("Changed from %s (0 pts) to %s (%d pts)",
                            bestCategory.getDisplayName(), betterCategory.getDisplayName(), highestScore));
                        bestCategory = betterCategory;
                    }
                }
            }
        }
        
        return bestCategory;
    }
    
    /**
     * Check if we need to prioritize the upper section bonus
     */
    private boolean needsUpperSectionBonus() {
        if (scoreCard == null) return false;
        
        // Count upper section points
        int upperTotal = 0;
        int upperFilled = 0;
        for (ScoreCategory category : ScoreCategory.values()) {
            if (category.isUpperSection()) {
                if (scoreCard.isScored(category)) {
                    upperTotal += scoreCard.getScore(category);
                    upperFilled++;
                }
            }
        }
        
        // If we've filled half or more of upper section but aren't on track for bonus
        // The minimum average needed is 10.5 points per category (63 ÷ 6)
        if (upperFilled >= 3) {
            double currentAverage = upperTotal / (double) upperFilled;
            return currentAverage < 10.5;
        }
        
        return true; // Early game, prioritize upper section by default
    }

    private List<Integer> decideDiceToHold(List<Integer> diceValues, ScoreCategory category) {
        List<Integer> diceToHold = new ArrayList<>();
        
        if (category == null) return diceToHold;
        
        switch (category) {
            case ONES:
                for (int i = 0; i < diceValues.size(); i++) {
                    if (diceValues.get(i) == 1) diceToHold.add(i);
                }
                break;
            case TWOS:
                for (int i = 0; i < diceValues.size(); i++) {
                    if (diceValues.get(i) == 2) diceToHold.add(i);
                }
                break;
            case THREES:
                for (int i = 0; i < diceValues.size(); i++) {
                    if (diceValues.get(i) == 3) diceToHold.add(i);
                }
                break;
            case FOURS:
                for (int i = 0; i < diceValues.size(); i++) {
                    if (diceValues.get(i) == 4) diceToHold.add(i);
                }
                break;
            case FIVES:
                for (int i = 0; i < diceValues.size(); i++) {
                    if (diceValues.get(i) == 5) diceToHold.add(i);
                }
                break;
            case SIXES:
                for (int i = 0; i < diceValues.size(); i++) {
                    if (diceValues.get(i) == 6) diceToHold.add(i);
                }
                break;
            case THREE_OF_A_KIND:
            case FOUR_OF_A_KIND:
            case YAHTZEE:
                Map<Integer, Integer> counts = new HashMap<>();
                for (int die : diceValues) {
                    counts.put(die, counts.getOrDefault(die, 0) + 1);
                }
                
                // Original strategy was only holding the highest frequency value
                // Now we need to prioritize based on frequency first
                Map<Integer, List<Integer>> valuesByFrequency = new HashMap<>();
                
                // Group values by their frequency
                for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
                    int value = entry.getKey();
                    int frequency = entry.getValue();
                    if (!valuesByFrequency.containsKey(frequency)) {
                        valuesByFrequency.put(frequency, new ArrayList<>());
                    }
                    valuesByFrequency.get(frequency).add(value);
                }
                
                // Find the highest frequency
                int maxFrequency = 0;
                for (Integer frequency : valuesByFrequency.keySet()) {
                    if (frequency > maxFrequency) {
                        maxFrequency = frequency;
                    }
                }
                
                // Hold ALL dice that have the highest frequency
                if (maxFrequency >= 2) { // Only hold if we have at least pairs
                    List<Integer> highestFrequencyValues = valuesByFrequency.get(maxFrequency);
                    
                    // For THREE_OF_A_KIND and FOUR_OF_A_KIND, hold ALL pairs and triplets
                    if (category == ScoreCategory.THREE_OF_A_KIND || category == ScoreCategory.FOUR_OF_A_KIND) {
                        // Hold all pairs and better
                        for (int i = 0; i < diceValues.size(); i++) {
                            int dieValue = diceValues.get(i);
                            int freq = counts.get(dieValue);
                            if (freq >= 2) { // Hold if it's part of a pair or better
                                diceToHold.add(i);
                            }
                        }
                    } 
                    // For YAHTZEE, only hold the highest frequency value
                    else if (category == ScoreCategory.YAHTZEE) {
                        // If multiple values have the same frequency, pick the highest value
                        Collections.sort(highestFrequencyValues, Collections.reverseOrder());
                        int bestValue = highestFrequencyValues.get(0);
                        
                        for (int i = 0; i < diceValues.size(); i++) {
                            if (diceValues.get(i) == bestValue) {
                                diceToHold.add(i);
                            }
                        }
                    }
                }
                // Fallback for when we don't have any pairs - hold the highest value
                else if (category == ScoreCategory.THREE_OF_A_KIND || category == ScoreCategory.FOUR_OF_A_KIND) {
                    // Find the highest single value
                    int highestValue = 0;
                    for (int value : counts.keySet()) {
                        if (value > highestValue) {
                            highestValue = value;
                        }
                    }
                    
                    // Hold the highest single value
                    for (int i = 0; i < diceValues.size(); i++) {
                        if (diceValues.get(i) == highestValue) {
                            diceToHold.add(i);
                            break; // Only hold one die
                        }
                    }
                }
                break;
                
            case FULL_HOUSE:
                return decideFullHouseDiceToHold(diceValues);
                
            case SMALL_STRAIGHT:
            case LARGE_STRAIGHT:
                // Enhanced straight holding logic
                return decideStraightDiceToHold(diceValues, category);
                
            default:
                break;
        }
        
        return diceToHold;
    }
    
    /**
     * Improved strategy for deciding which dice to hold for full house
     */
    private List<Integer> decideFullHouseDiceToHold(List<Integer> diceValues) {
        List<Integer> diceToHold = new ArrayList<>();
        
        // Create frequency map
        Map<Integer, Integer> valueCounts = new HashMap<>();
                for (int die : diceValues) {
            valueCounts.put(die, valueCounts.getOrDefault(die, 0) + 1);
                }
                
        // Check if we already have a full house
        if (hasFullHouse(diceValues)) {
            // Hold all dice
            for (int i = 0; i < diceValues.size(); i++) {
                diceToHold.add(i);
            }
            return diceToHold;
        }
        
        // Check for four of a kind - we should be pursuing Yahtzee or Four of a Kind instead
        int fourOfAKindValue = -1;
        for (Map.Entry<Integer, Integer> entry : valueCounts.entrySet()) {
            if (entry.getValue() >= 4) {
                fourOfAKindValue = entry.getKey();
                break;
            }
        }
        
        // If we have four of a kind, this is handled by the FOUR_OF_A_KIND and YAHTZEE cases
        // Return empty list to let those strategies take precedence
        if (fourOfAKindValue != -1) {
            return new ArrayList<>();
        }
        
        // Find three of a kind and pairs
                int threeOfAKindValue = -1;
        int highestPairValue = -1;
        int secondPairValue = -1;
                
        // First look for three of a kind
        for (Map.Entry<Integer, Integer> entry : valueCounts.entrySet()) {
                    if (entry.getValue() >= 3) {
                        threeOfAKindValue = entry.getKey();
                break;
            }
        }
        
        // Then look for pairs (or a second triplet)
        for (Map.Entry<Integer, Integer> entry : valueCounts.entrySet()) {
            if (entry.getValue() >= 2 && entry.getKey() != threeOfAKindValue) {
                if (highestPairValue == -1 || entry.getKey() > highestPairValue) {
                    secondPairValue = highestPairValue;
                    highestPairValue = entry.getKey();
                } else if (secondPairValue == -1 || entry.getKey() > secondPairValue) {
                    secondPairValue = entry.getKey();
                }
            }
        }
        
        // Strategy 1: If we have a three of a kind and a pair, hold both
        if (threeOfAKindValue != -1 && highestPairValue != -1) {
                    for (int i = 0; i < diceValues.size(); i++) {
                int die = diceValues.get(i);
                if (die == threeOfAKindValue || die == highestPairValue) {
                            diceToHold.add(i);
                        }
                    }
                }
        // Strategy 2: If we have a three of a kind but no pair, hold just the three
        else if (threeOfAKindValue != -1) {
            for (int i = 0; i < diceValues.size(); i++) {
                if (diceValues.get(i) == threeOfAKindValue) {
                    diceToHold.add(i);
                }
            }
        }
        // Strategy 3: If we have two pairs, hold both
        else if (highestPairValue != -1 && secondPairValue != -1) {
            for (int i = 0; i < diceValues.size(); i++) {
                int die = diceValues.get(i);
                if (die == highestPairValue || die == secondPairValue) {
                    diceToHold.add(i);
                }
            }
        }
        // Strategy 4: If we just have one pair, hold it
        else if (highestPairValue != -1) {
            for (int i = 0; i < diceValues.size(); i++) {
                if (diceValues.get(i) == highestPairValue) {
                    diceToHold.add(i);
                }
            }
        }
        
        return diceToHold;
    }
    
    /**
     * Improved strategy for deciding which dice to hold for straights
     */
    private List<Integer> decideStraightDiceToHold(List<Integer> diceValues, ScoreCategory category) {
        List<Integer> diceToHold = new ArrayList<>();
        Set<Integer> uniqueValues = new HashSet<>(diceValues);
        
        // Create a frequency map to identify duplicates
        Map<Integer, Integer> valueCounts = new HashMap<>();
                        for (int i = 0; i < diceValues.size(); i++) {
            int die = diceValues.get(i);
            valueCounts.put(die, valueCounts.getOrDefault(die, 0) + 1);
        }
        
        // Create a map of dice positions for each value
        Map<Integer, List<Integer>> valuePositions = new HashMap<>();
        for (int i = 0; i < diceValues.size(); i++) {
            int die = diceValues.get(i);
            if (!valuePositions.containsKey(die)) {
                valuePositions.put(die, new ArrayList<>());
            }
            valuePositions.get(die).add(i);
        }
        
        // Check if we already have a straight
        if (category == ScoreCategory.SMALL_STRAIGHT && hasSmallStraight(diceValues)) {
            // Hold all dice in the small straight
            Set<Integer> straightValues = findSmallStraightValues(diceValues);
            
            // For each unique value in the straight, hold exactly one die
            for (int value : straightValues) {
                if (valuePositions.containsKey(value) && !valuePositions.get(value).isEmpty()) {
                    diceToHold.add(valuePositions.get(value).get(0)); // Add just the first instance
                }
            }
            
            return diceToHold;
        } else if (category == ScoreCategory.LARGE_STRAIGHT && hasLargeStraight(diceValues)) {
            // Hold all dice in the large straight
            // For a large straight, all values must be unique, so just hold all dice
                        for (int i = 0; i < diceValues.size(); i++) {
                                diceToHold.add(i);
                            }
            return diceToHold;
        }
        
        // For potential straights, determine the best sequence to pursue
        int[] sequenceCounts = new int[3]; // For small straight: 1-2-3-4, 2-3-4-5, 3-4-5-6
        
        List<Integer> sortedUniqueValues = new ArrayList<>(uniqueValues);
        Collections.sort(sortedUniqueValues);
        
        if (category == ScoreCategory.SMALL_STRAIGHT) {
            // Check each potential small straight
            sequenceCounts[0] = countSequentialValues(sortedUniqueValues, 1, 4); // 1-2-3-4
            sequenceCounts[1] = countSequentialValues(sortedUniqueValues, 2, 5); // 2-3-4-5
            sequenceCounts[2] = countSequentialValues(sortedUniqueValues, 3, 6); // 3-4-5-6
            
            // Find best potential sequence
            int bestSequenceIndex = 0;
            for (int i = 1; i < sequenceCounts.length; i++) {
                if (sequenceCounts[i] > sequenceCounts[bestSequenceIndex]) {
                    bestSequenceIndex = i;
                }
            }
            
            // Get the range for the best potential sequence
            int startValue = bestSequenceIndex == 0 ? 1 : (bestSequenceIndex == 1 ? 2 : 3);
            int endValue = bestSequenceIndex == 0 ? 4 : (bestSequenceIndex == 1 ? 5 : 6);
            
            // Create set of desired values for this sequence
            Set<Integer> desiredValues = new HashSet<>();
            for (int i = startValue; i <= endValue; i++) {
                desiredValues.add(i);
            }
            
            // For each desired value, hold exactly one die
            for (int value : desiredValues) {
                if (valuePositions.containsKey(value) && !valuePositions.get(value).isEmpty()) {
                    diceToHold.add(valuePositions.get(value).get(0)); // Add just the first instance
                }
            }
        } 
        else if (category == ScoreCategory.LARGE_STRAIGHT) {
            // Check each potential large straight
            int sequence1to5 = countSequentialValues(sortedUniqueValues, 1, 5); // 1-2-3-4-5
            int sequence2to6 = countSequentialValues(sortedUniqueValues, 2, 6); // 2-3-4-5-6
            
            // Determine which sequence is more promising
            boolean prefer1to5 = sequence1to5 >= sequence2to6;
            
            // Get the range for the preferred sequence
            int startValue = prefer1to5 ? 1 : 2;
            int endValue = prefer1to5 ? 5 : 6;
            
            // Create set of desired values for this sequence
            Set<Integer> desiredValues = new HashSet<>();
            for (int i = startValue; i <= endValue; i++) {
                desiredValues.add(i);
            }
            
            // For each desired value, hold exactly one die
            for (int value : desiredValues) {
                if (valuePositions.containsKey(value) && !valuePositions.get(value).isEmpty()) {
                    diceToHold.add(valuePositions.get(value).get(0)); // Add just the first instance
                }
            }
        }
        
        // Log the decision for debugging
        if (!diceToHold.isEmpty()) {
            String description = category == ScoreCategory.SMALL_STRAIGHT ? "Small Straight" : "Large Straight";
            StringBuilder sb = new StringBuilder(description + " holding: ");
            for (int i : diceToHold) {
                sb.append("D").append(i + 1).append("=").append(diceValues.get(i)).append(" ");
            }
            System.out.println(sb.toString());
        }
        
        return diceToHold;
    }
    
    /**
     * Find the set of dice values that form a small straight
     */
    private Set<Integer> findSmallStraightValues(List<Integer> diceValues) {
        Set<Integer> uniqueValues = new HashSet<>(diceValues);
        Set<Integer> result = new HashSet<>();
        
        // Check for 1-2-3-4
        if (uniqueValues.containsAll(Arrays.asList(1, 2, 3, 4))) {
            result.addAll(Arrays.asList(1, 2, 3, 4));
        }
        // Check for 2-3-4-5
        else if (uniqueValues.containsAll(Arrays.asList(2, 3, 4, 5))) {
            result.addAll(Arrays.asList(2, 3, 4, 5));
        }
        // Check for 3-4-5-6
        else if (uniqueValues.containsAll(Arrays.asList(3, 4, 5, 6))) {
            result.addAll(Arrays.asList(3, 4, 5, 6));
        }
        
        return result;
    }
    
    /**
     * Provide detailed explanation about straight strategy
     */
    private void explainStraightStrategy(List<Integer> diceValues, ScoreCategory category) {
        // Only explain if we haven't explained this before
        String explanationKey = "straight_" + formatDiceValues(diceValues);
        if (turnContext.containsKey(explanationKey)) {
            return;
        }
        turnContext.put(explanationKey, true);
        
        Set<Integer> uniqueValues = new HashSet<>(diceValues);
        List<Integer> sortedUniqueValues = new ArrayList<>(uniqueValues);
        Collections.sort(sortedUniqueValues);
        
        StringBuilder explanation = new StringBuilder();
        
        // Small straight analysis
        if (category == ScoreCategory.SMALL_STRAIGHT) {
            // Check how close we are to different small straights
            int seq1to4 = countSequentialValues(sortedUniqueValues, 1, 4);
            int seq2to5 = countSequentialValues(sortedUniqueValues, 2, 5);
            int seq3to6 = countSequentialValues(sortedUniqueValues, 3, 6);
            
            if (seq1to4 == 4 || seq2to5 == 4 || seq3to6 == 4) {
                explanation.append("Have Small Straight!");
                } else {
                // Pick the best potential straight
                int bestSeq = Math.max(seq1to4, Math.max(seq2to5, seq3to6));
                
                if (bestSeq == 3) {
                    explanation.append("Need 1 more for Small Straight");
                } else {
                    explanation.append("Building toward Small Straight");
                }
            }
        }
        // Large straight analysis
        else if (category == ScoreCategory.LARGE_STRAIGHT) {
            int seq1to5 = countSequentialValues(sortedUniqueValues, 1, 5);
            int seq2to6 = countSequentialValues(sortedUniqueValues, 2, 6);
            
            if (seq1to5 == 5 || seq2to6 == 5) {
                explanation.append("Have Large Straight!");
                    } else {
                int bestSeq = Math.max(seq1to5, seq2to6);
                
                if (bestSeq == 4) {
                    explanation.append("Need 1 more for Large Straight");
                } else {
                    explanation.append("Building toward Large Straight");
                }
            }
        }
        
        if (!explanation.toString().isEmpty()) {
            addExplanation("Straight", explanation.toString());
        }
    }

    /**
     * Calculate probability for full house with better detection of potential full houses
     */
    private double calculateFullHouseProbability(List<Integer> diceValues) {
        // Check if we already have a full house
        if (hasFullHouse(diceValues)) {
            return 1.0;
        }
        
        // Create a frequency map to identify duplicates
        Map<Integer, Integer> valueCounts = new HashMap<>();
        for (int die : diceValues) {
            valueCounts.put(die, valueCounts.getOrDefault(die, 0) + 1);
        }
        
        // Check for four of a kind - should pursue Yahtzee or Four of a Kind instead!
        boolean hasFourOfAKind = valueCounts.values().stream().anyMatch(count -> count >= 4);
        if (hasFourOfAKind) {
            // Return low probability to discourage pursuing Full House with 4 of a kind
            return 0.1;
        }
        
        // Check for three of a kind
        boolean hasThreeOfAKind = valueCounts.values().stream().anyMatch(count -> count >= 3);
        
        // Check for two pairs
        long pairCount = valueCounts.values().stream().filter(count -> count >= 2).count();
        
        // Calculate probability based on current state
        if (hasThreeOfAKind) {
            // We have a three of a kind, need a pair
            // Higher chance with more rerolls left
            return 0.7 + (0.1 * Math.min(currentTurn.getRollsLeft(), 2));
        } else if (pairCount >= 2) {
            // We have two pairs, need to convert one to three of a kind
            // Each die has a 1/6 chance of matching our target value
            // With 1 reroll, probability is reasonable
            return 0.6 + (0.1 * Math.min(currentTurn.getRollsLeft(), 2));
        } else if (pairCount == 1) {
            // We have one pair, need another pair and to convert one to three
            // More difficult, but still possible
            return 0.4 + (0.1 * Math.min(currentTurn.getRollsLeft(), 2));
                            }
        
        // No pairs or three of a kind, low probability
        return 0.2 + (0.05 * Math.min(currentTurn.getRollsLeft(), 2));
    }

    /**
     * Provide detailed explanation for full house strategy
     */
    private void explainFullHouseStrategy(List<Integer> diceValues) {
        // Only explain if we haven't explained this before
        String explanationKey = "fullhouse_" + formatDiceValues(diceValues);
        if (turnContext.containsKey(explanationKey)) {
            return;
        }
        turnContext.put(explanationKey, true);
        
        // Create frequency map
        Map<Integer, Integer> valueCounts = new HashMap<>();
        for (int die : diceValues) {
            valueCounts.put(die, valueCounts.getOrDefault(die, 0) + 1);
        }
        
        StringBuilder explanation = new StringBuilder();
        
        // Check for four of a kind
        boolean hasFourOfAKind = valueCounts.values().stream().anyMatch(count -> count >= 4);
        if (hasFourOfAKind) {
            explanation.append("Using four of a kind for Full House");
        }
        else if (hasFullHouse(diceValues)) {
            explanation.append("Full House complete!");
        } else {
            // Find three of a kind and pairs
            boolean hasThreeOfAKind = valueCounts.values().stream().anyMatch(count -> count >= 3);
            long pairCount = valueCounts.values().stream().filter(count -> count >= 2).count();
            
            if (hasThreeOfAKind) {
                explanation.append("Have triplet, need pair");
            } else if (pairCount >= 2) {
                explanation.append("Have two pairs, need triplet");
            } else if (pairCount == 1) {
                explanation.append("Have pair, need triplet + pair");
            } else {
                explanation.append("Looking for pairs");
        }
        }
        
        if (!explanation.toString().isEmpty()) {
            addExplanation("Full House", explanation.toString());
        }
    }

    private int calculateScore(List<Integer> diceValues, ScoreCategory category) {
        return scoreCard.calculateScore(category, diceValues);
    }

    private boolean hasNOfAKind(List<Integer> diceValues, int n) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (int die : diceValues) {
            counts.put(die, counts.getOrDefault(die, 0) + 1);
        }
        return counts.values().stream().anyMatch(count -> count >= n);
    }

    private boolean hasFullHouse(List<Integer> diceValues) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (int die : diceValues) {
            counts.put(die, counts.getOrDefault(die, 0) + 1);
        }
        
        boolean hasThree = false;
        boolean hasTwo = false;
        
        for (int count : counts.values()) {
            if (count >= 3) hasThree = true;
            else if (count >= 2) hasTwo = true;
        }
        
        return hasThree && hasTwo;
    }

    private boolean hasSmallStraight(List<Integer> diceValues) {
        Set<Integer> uniqueDice = new HashSet<>(diceValues);
        return (uniqueDice.containsAll(Arrays.asList(1, 2, 3, 4)) ||
                uniqueDice.containsAll(Arrays.asList(2, 3, 4, 5)) ||
                uniqueDice.containsAll(Arrays.asList(3, 4, 5, 6)));
    }

    private boolean hasLargeStraight(List<Integer> diceValues) {
        Set<Integer> uniqueDice = new HashSet<>(diceValues);
        return (uniqueDice.containsAll(Arrays.asList(1, 2, 3, 4, 5)) ||
                uniqueDice.containsAll(Arrays.asList(2, 3, 4, 5, 6)));
    }

    @Override
    public ScoreCategory chooseCategory(Round round, List<ScoreCategory> availableCategories) {
        List<Integer> diceValues = getAllDice();
        Map<ScoreCategory, Double> probabilities = analyzePossibleMoves(diceValues);
        return decideBestCategory(probabilities);
    }

    @Override
    public ScoreCategory determineNextMove(ScoreCard scoreCard) {
        List<Integer> diceValues = getAllDice();
        
        // When out of rolls, directly choose the highest scoring category
        if (currentTurn != null && currentTurn.getRollsLeft() == 0) {
            return findHighestScoringCategory(diceValues);
        }
        
        // Otherwise use the standard probability-based approach
        Map<ScoreCategory, Double> probabilities = analyzePossibleMoves(diceValues);
        return decideBestCategory(probabilities);
    }
    
    /**
     * Find the category that would give the highest current score
     * This is used when out of rolls to ensure we maximize points
     */
    private ScoreCategory findHighestScoringCategory(List<Integer> diceValues) {
        if (scoreCard == null) return null;
        
        List<ScoreCategory> availableCategories = new ArrayList<>();
        for (ScoreCategory category : ScoreCategory.values()) {
            if (!scoreCard.isCategoryFilled(category)) {
                availableCategories.add(category);
            }
        }
        
        if (availableCategories.isEmpty()) return null;
        
        // Find categories with non-zero scores first
        List<ScoreCategory> nonZeroCategories = new ArrayList<>();
        Map<ScoreCategory, Integer> categoryScores = new HashMap<>();
        
        for (ScoreCategory category : availableCategories) {
            int score = scoreCard.calculateScore(category, diceValues);
            categoryScores.put(category, score);
            if (score > 0) {
                nonZeroCategories.add(category);
            }
        }
        
        // If we have any non-zero scoring categories, only consider those
        if (!nonZeroCategories.isEmpty()) {
            // Find the highest scoring category among non-zero options
            ScoreCategory bestCategory = null;
            int highestScore = -1;
            
            for (ScoreCategory category : nonZeroCategories) {
                int score = categoryScores.get(category);
                if (score > highestScore) {
                    highestScore = score;
                    bestCategory = category;
                }
            }
            
            addExplanation("Best Score", String.format("Selected %s for highest score (%d points)", 
                bestCategory.getDisplayName(), highestScore));
            return bestCategory;
        }
        
        // If all categories would score zero, try to pick the best category to sacrifice
        
        // Special case for 4 of a kind - prefer upper section matching the value
        boolean hasFourOfAKind = hasFourOfAKind(diceValues);
        if (hasFourOfAKind && !isYahtzee(diceValues)) {
            // Handle 4 of a kind case with fallback options
            Map<Integer, Integer> valueCounts = new HashMap<>();
            for (int die : diceValues) {
                valueCounts.put(die, valueCounts.getOrDefault(die, 0) + 1);
            }
            
            int fourOfAKindValue = 0;
            for (Map.Entry<Integer, Integer> entry : valueCounts.entrySet()) {
                if (entry.getValue() >= 4) {
                    fourOfAKindValue = entry.getKey();
                    break;
                }
            }
            
            // Try the corresponding upper section category for the 4 of a kind value
            ScoreCategory upperCategory = null;
            switch (fourOfAKindValue) {
                case 1: upperCategory = ScoreCategory.ONES; break;
                case 2: upperCategory = ScoreCategory.TWOS; break;
                case 3: upperCategory = ScoreCategory.THREES; break;
                case 4: upperCategory = ScoreCategory.FOURS; break;
                case 5: upperCategory = ScoreCategory.FIVES; break;
                case 6: upperCategory = ScoreCategory.SIXES; break;
            }
            
            if (upperCategory != null && !scoreCard.isCategoryFilled(upperCategory)) {
                addExplanation("Zero Score", String.format("Using %s since all categories would score zero", 
                    upperCategory.getDisplayName()));
                return upperCategory;
            }
        }
        
        // Prefer to sacrifice a lower section category first (except for high-value ones)
        for (ScoreCategory category : availableCategories) {
            if (!category.isUpperSection() && 
                category != ScoreCategory.YAHTZEE && 
                category != ScoreCategory.LARGE_STRAIGHT) {
                addExplanation("Zero Score", String.format("Sacrificing %s since all categories would score zero", 
                    category.getDisplayName()));
                return category;
            }
        }
        
        // If no good lower section sacrifice found, try an upper section category
        for (ScoreCategory category : availableCategories) {
            if (category.isUpperSection()) {
                addExplanation("Zero Score", String.format("Sacrificing %s since all categories would score zero", 
                    category.getDisplayName()));
                return category;
            }
        }
        
        // Last resort: first available category
        ScoreCategory fallbackCategory = availableCategories.get(0);
        addExplanation("Zero Score", String.format("Using %s as last resort (all categories score zero)", 
            fallbackCategory.getDisplayName()));
        return fallbackCategory;
    }

    @Override
    public List<Integer> determineDiceToHold() {
        if (currentTurn == null) {
            return Collections.emptyList();
        }
        
        List<Integer> diceValues = getAllDice();
        Map<ScoreCategory, Double> probabilities = analyzePossibleMoves(diceValues);
        ScoreCategory targetCategory = decideBestCategory(probabilities);
        return decideDiceToHold(diceValues, targetCategory);
    }

    protected void addExplanation(String explanation) {
        if (gameStateCallback != null) {
            List<Integer> currentDice = getCurrentTurn().getDice();
            
            // Only format dice values if they're not all zeros
            boolean allZeros = currentDice.stream().allMatch(d -> d == 0);
            String diceStr = allZeros ? "no dice" : formatDiceValues(currentDice);
            
            // Limit explanation length to avoid memory issues
            if (explanation.length() > 100) {
                explanation = explanation.substring(0, 97) + "...";
            }
            
            String fullExplanation = "Dice: " + diceStr + " | " + explanation;
            
            // Use the existing explanations list to maintain history
            addExplanation("Info", explanation);
        }
    }

    private boolean hasFourOfAKind(List<Integer> diceValues) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (int die : diceValues) {
            counts.put(die, counts.getOrDefault(die, 0) + 1);
        }
        return counts.values().stream().anyMatch(count -> count >= 4);
    }
}
