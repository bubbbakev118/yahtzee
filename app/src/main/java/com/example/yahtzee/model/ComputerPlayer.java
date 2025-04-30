package com.example.yahtzee.model;

import com.example.yahtzee.callbacks.GameStateCallback;
import java.util.*;

public class ComputerPlayer extends Player implements ComputerPlayerActions {
    private static final double YAHTZEE_THRESHOLD = 0.8;
    private static final double STRAIGHT_THRESHOLD = 0.6;
    private static final double FULL_HOUSE_THRESHOLD = 0.5;
    private static final double KIND_THRESHOLD = 0.4;

    private List<String> explanations = new ArrayList<>();
    private GameStateCallback gameStateCallback;
    private ScoreCard scoreCard;
    private ScoreCategory currentTargetCategory = null;

    public ComputerPlayer(GameStateCallback callback) {
        super("Computer");
        this.gameStateCallback = callback;
    }

    private void addExplanation(String title, String explanation) {
        String fullExplanation = title + ": " + explanation;
        explanations.add(fullExplanation);
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
        explanations.clear();
        setCurrentRound(round);
        Turn turn = round.getCurrentTurn();
        scoreCard = round.getScoreCard(this);
        
        currentTargetCategory = null;
        
        // First roll - don't analyze until we have actual dice values
        addExplanation("Turn", "Computer's turn started");
    }

    /**
     * Public method to analyze current dice and update strategy.
     * Called by GameController to show analysis during computer turn.
     */
    public void analyzeCurrentState() {
        analyzeDiceAndUpdateStrategy();
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
        
        // Analyze current roll
        Map<ScoreCategory, Double> probabilities = analyzePossibleMoves(diceValues);
        
        // Find best available categories
        List<Map.Entry<ScoreCategory, Double>> bestCategories = probabilities.entrySet().stream()
            .filter(e -> !scoreCard.isScored(e.getKey()))
            .sorted((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()))
            .limit(3)
            .collect(java.util.stream.Collectors.toList());
            
        // Build compact analysis
        StringBuilder analysis = new StringBuilder();
        analysis.append("Top options: ");
        for (int i = 0; i < bestCategories.size(); i++) {
            Map.Entry<ScoreCategory, Double> entry = bestCategories.get(i);
            int score = calculateScore(diceValues, entry.getKey());
            if (i > 0) analysis.append(" | ");
            analysis.append(String.format("%s: %d pts (%.0f%%)", 
                entry.getKey().getDisplayName(),
                score,
                entry.getValue() * 100));
        }
        
        addExplanation("Analysis", analysis.toString());
        
        // Decide strategy
        ScoreCategory targetCategory = decideBestCategory(probabilities);
        if (targetCategory != null) {
            int potentialScore = calculateScore(diceValues, targetCategory);
            
            // Update held dice based on strategy
            releaseDice(getHeldDiceIndices()); // Clear previous holds
            List<Integer> diceToHold = decideDiceToHold(diceValues, targetCategory);
            
            StringBuilder strategy = new StringBuilder();
            strategy.append(String.format("Target: %s (%d pts)", 
                targetCategory.getDisplayName(), potentialScore));
            
            if (!diceToHold.isEmpty()) {
                holdDice(diceToHold);
                strategy.append(String.format(" - Holding dice: %s", 
                    formatDicePositions(diceToHold, diceValues)));
            } else {
                strategy.append(" - Not holding any dice");
            }
            
            addExplanation("Strategy", strategy.toString());
        }
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
        
        // Analyze current state and update strategy
        analyzeDiceAndUpdateStrategy();
        
        // Get current dice values and analyze
        Map<ScoreCategory, Double> probabilities = analyzePossibleMoves(diceValues);
        double bestProbability = 0;
        ScoreCategory bestCategory = null;
        
        for (Map.Entry<ScoreCategory, Double> entry : probabilities.entrySet()) {
            if (entry.getValue() > bestProbability) {
                bestProbability = entry.getValue();
                bestCategory = entry.getKey();
            }
        }
        
        // Adjust probability threshold based on category value
        double probabilityThreshold = 0.8; // Default threshold
        
        if (bestCategory != null) {
            // Lower the threshold for high-value categories
            switch (bestCategory) {
                case YAHTZEE:
                    probabilityThreshold = 0.6; // More aggressive for Yahtzee (keep rolling up to 60%)
                    break;
                case LARGE_STRAIGHT:
                    probabilityThreshold = 0.65; // More aggressive for Large Straight
                    break;
                case SMALL_STRAIGHT:
                case FULL_HOUSE:
                    probabilityThreshold = 0.7; // Somewhat more aggressive for these
                    break;
                case FOUR_OF_A_KIND:
                    probabilityThreshold = 0.75;
                    break;
                default:
                    // Keep default threshold for other categories
                    break;
            }
            
            // Also consider current score vs. max possible score
            int maxScore = getMaxPossibleScore(bestCategory);
            int currentScore = scoreCard.calculateScore(bestCategory, diceValues);
            double percentOfMax = (double) currentScore / maxScore;
            
            // If we're getting close to max score, be less eager to roll again
            if (percentOfMax > 0.8) {
                probabilityThreshold -= 0.1; // More likely to keep if already at 80% of max
            } else if (percentOfMax < 0.5) {
                probabilityThreshold += 0.1; // More likely to roll if below 50% of max
            }
        }
        
        // Adjust for number of rolls left - be more aggressive on first roll
        if (currentTurn.getRollsLeft() == 2) { // Just rolled once
            probabilityThreshold += 0.1; // More likely to roll again on first roll
        }
        
        // Clamp threshold to reasonable values
        probabilityThreshold = Math.max(0.5, Math.min(0.9, probabilityThreshold));
        
        boolean shouldRoll = bestProbability < probabilityThreshold && currentTurn.getRollsLeft() > 0;
        
        StringBuilder decision = new StringBuilder();
        if (shouldRoll) {
            decision.append(String.format("Rolling again (best: %.0f%%, threshold: %.0f%%, rolls left: %d)", 
                bestProbability * 100, probabilityThreshold * 100, currentTurn.getRollsLeft()));
        } else {
            decision.append(String.format("Keeping roll (best: %.0f%%, threshold: %.0f%%)", 
                bestProbability * 100, probabilityThreshold * 100));
        }
        
        addExplanation("Decision", decision.toString());
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
            if (i > 0) sb.append(",");
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
        
        // Calculate probability based on current dice values and category
        int currentScore = calculateScore(diceValues, category);
        int maxPossibleScore = getMaxPossibleScore(category);
        
        if (maxPossibleScore == 0) return 0;
        return (double) currentScore / maxPossibleScore;
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
        double maxProbability = 0;
        double highestExpectedValue = 0;
        
        // If we already have a target category with at least 25% probability, stick with it
        if (currentTargetCategory != null && 
            probabilities.containsKey(currentTargetCategory) && 
            probabilities.get(currentTargetCategory) >= 0.25) {
            return currentTargetCategory;
        }
        
        // Otherwise, find the category with highest expected value and at least 25% probability
        for (Map.Entry<ScoreCategory, Double> entry : probabilities.entrySet()) {
            ScoreCategory category = entry.getKey();
            double probability = entry.getValue();
            
            // Skip if probability is below threshold
            if (probability < 0.25) continue;
            
            int categoryValue = getMaxPossibleScore(category);
            
            // Apply bonus weight to high-value categories to prioritize them
            double valueMultiplier = 1.0;
            switch (category) {
                case YAHTZEE:
                    valueMultiplier = 1.5; // Extra weight for Yahtzee
                    break;
                case LARGE_STRAIGHT:
                    valueMultiplier = 1.3; // Extra weight for Large Straight
                    break;
                case SMALL_STRAIGHT:
                case FULL_HOUSE:
                    valueMultiplier = 1.2; // Some extra weight for other high-scoring categories
                    break;
                default:
                    // For n-of-a-kind categories, give some extra weight too
                    if (category == ScoreCategory.FOUR_OF_A_KIND) {
                        valueMultiplier = 1.15;
                    } else if (category == ScoreCategory.THREE_OF_A_KIND) {
                        valueMultiplier = 1.1;
                    }
                    break;
            }
            
            // Calculate expected value with the value multiplier
            double expectedValue = probability * categoryValue * valueMultiplier;
            
            // Track the highest expected value
            if (expectedValue > highestExpectedValue) {
                highestExpectedValue = expectedValue;
                maxProbability = probability;
                bestCategory = category;
            }
        }
        
        // If we didn't find any category with sufficient probability,
        // fall back to the highest probability category with a preference for high values
        if (bestCategory == null) {
            double bestScore = 0;
            for (Map.Entry<ScoreCategory, Double> entry : probabilities.entrySet()) {
                ScoreCategory category = entry.getKey();
                double probability = entry.getValue();
                int value = getMaxPossibleScore(category);
                
                // Score is a combination of probability and value
                double score = probability * Math.sqrt(value);
                
                if (score > bestScore) {
                    bestScore = score;
                    bestCategory = category;
                    maxProbability = probability;
                }
            }
        }
        
        // Log the decision
        if (bestCategory != null) {
            currentTargetCategory = bestCategory;
            
            // Calculate how likely we are to achieve the target
            int maxValue = getMaxPossibleScore(bestCategory);
            int currentScore = scoreCard.calculateScore(bestCategory, getAllDice());
            double percentOfMax = (double) currentScore / maxValue * 100;
            
            addExplanation("Target", String.format("%s selected - %.0f%% probability, %.0f%% of max possible", 
                bestCategory.getDisplayName(), maxProbability * 100, percentOfMax));
        }
        
        return bestCategory;
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
                
                int maxCount = 0;
                int maxValue = 0;
                for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
                    if (entry.getValue() > maxCount || 
                        (entry.getValue() == maxCount && entry.getKey() > maxValue)) {
                        maxCount = entry.getValue();
                        maxValue = entry.getKey();
                    }
                }
                
                for (int i = 0; i < diceValues.size(); i++) {
                    if (diceValues.get(i) == maxValue) {
                        diceToHold.add(i);
                    }
                }
                break;
                
            case FULL_HOUSE:
                counts = new HashMap<>();
                for (int die : diceValues) {
                    counts.put(die, counts.getOrDefault(die, 0) + 1);
                }
                
                int threeOfAKindValue = -1;
                int pairValue = -1;
                
                for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
                    if (entry.getValue() >= 3) {
                        threeOfAKindValue = entry.getKey();
                    } else if (entry.getValue() == 2) {
                        pairValue = entry.getKey();
                    }
                }
                
                if (threeOfAKindValue != -1) {
                    for (int i = 0; i < diceValues.size(); i++) {
                        int value = diceValues.get(i);
                        if (value == threeOfAKindValue || (pairValue != -1 && value == pairValue)) {
                            diceToHold.add(i);
                        }
                    }
                }
                break;
                
            case SMALL_STRAIGHT:
            case LARGE_STRAIGHT:
                Set<Integer> uniqueDice = new HashSet<>(diceValues);
                
                if (category == ScoreCategory.SMALL_STRAIGHT) {
                    if (uniqueDice.containsAll(Arrays.asList(1, 2, 3, 4)) ||
                        uniqueDice.containsAll(Arrays.asList(2, 3, 4, 5)) ||
                        uniqueDice.containsAll(Arrays.asList(3, 4, 5, 6))) {
                        
                        for (int i = 0; i < diceValues.size(); i++) {
                            int value = diceValues.get(i);
                            if ((uniqueDice.containsAll(Arrays.asList(1, 2, 3, 4)) && value >= 1 && value <= 4) ||
                                (uniqueDice.containsAll(Arrays.asList(2, 3, 4, 5)) && value >= 2 && value <= 5) ||
                                (uniqueDice.containsAll(Arrays.asList(3, 4, 5, 6)) && value >= 3 && value <= 6)) {
                                diceToHold.add(i);
                            }
                        }
                    } else {
                        int[] sequences = {
                            uniqueDice.containsAll(Arrays.asList(1, 2, 3)) ? 3 : 0,
                            uniqueDice.containsAll(Arrays.asList(2, 3, 4)) ? 3 : 0,
                            uniqueDice.containsAll(Arrays.asList(3, 4, 5)) ? 3 : 0,
                            uniqueDice.containsAll(Arrays.asList(4, 5, 6)) ? 3 : 0
                        };
                        
                        int bestSequence = 0;
                        for (int i = 0; i < sequences.length; i++) {
                            if (sequences[i] > sequences[bestSequence]) {
                                bestSequence = i;
                            }
                        }
                        
                        for (int i = 0; i < diceValues.size(); i++) {
                            int value = diceValues.get(i);
                            if ((bestSequence == 0 && value >= 1 && value <= 3) ||
                                (bestSequence == 1 && value >= 2 && value <= 4) ||
                                (bestSequence == 2 && value >= 3 && value <= 5) ||
                                (bestSequence == 3 && value >= 4 && value <= 6)) {
                                diceToHold.add(i);
                            }
                        }
                    }
                } else {
                    if (uniqueDice.containsAll(Arrays.asList(1, 2, 3, 4, 5)) ||
                        uniqueDice.containsAll(Arrays.asList(2, 3, 4, 5, 6))) {
                        
                        for (int i = 0; i < diceValues.size(); i++) {
                            int value = diceValues.get(i);
                            if ((uniqueDice.containsAll(Arrays.asList(1, 2, 3, 4, 5)) && value >= 1 && value <= 5) ||
                                (uniqueDice.containsAll(Arrays.asList(2, 3, 4, 5, 6)) && value >= 2 && value <= 6)) {
                                diceToHold.add(i);
                            }
                        }
                    } else {
                        int[] sequences = {
                            uniqueDice.containsAll(Arrays.asList(1, 2, 3, 4)) ? 4 : 0,
                            uniqueDice.containsAll(Arrays.asList(2, 3, 4, 5)) ? 4 : 0,
                            uniqueDice.containsAll(Arrays.asList(3, 4, 5, 6)) ? 4 : 0
                        };
                        
                        int bestSequence = 0;
                        for (int i = 0; i < sequences.length; i++) {
                            if (sequences[i] > sequences[bestSequence]) {
                                bestSequence = i;
                            }
                        }
                        
                        for (int i = 0; i < diceValues.size(); i++) {
                            int value = diceValues.get(i);
                            if ((bestSequence == 0 && value >= 1 && value <= 4) ||
                                (bestSequence == 1 && value >= 2 && value <= 5) ||
                                (bestSequence == 2 && value >= 3 && value <= 6)) {
                                diceToHold.add(i);
                            }
                        }
                    }
                }
                break;
                
            default:
                break;
        }
        
        return diceToHold;
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
        return counts.values().contains(2) && counts.values().contains(3);
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
        Map<ScoreCategory, Double> probabilities = analyzePossibleMoves(diceValues);
        return decideBestCategory(probabilities);
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
            String fullExplanation = "Dice: " + diceStr + " | " + explanation;
            gameStateCallback.onComputerTurnAnnouncement(Collections.singletonList(fullExplanation));
        }
    }
}
