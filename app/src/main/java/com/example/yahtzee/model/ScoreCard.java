package com.example.yahtzee.model;

import java.util.*;

public class ScoreCard {
    public static final int TOTAL_ROUNDS = 13;
    private static final int UPPER_BONUS_THRESHOLD = 63;
    private static final int UPPER_BONUS_POINTS = 35;
    
    private final Map<ScoreCategory, ScoreEntry> scores;
    private final Map<ScoreCategory, Integer> roundScores;
    private int upperBonus;
    private int totalScore;

    public ScoreCard() {
        this.scores = new HashMap<>();
        this.roundScores = new HashMap<>();
        this.upperBonus = 0;
        this.totalScore = 0;
    }

    public void setScore(ScoreCategory category, int score, Player player, Round round) {
        if (category == null) {
            throw new IllegalArgumentException("Category cannot be null");
        }
        if (isScored(category)) {
            throw new IllegalStateException("Category already scored");
        }

        scores.put(category, new ScoreEntry(score, player, round.getRoundNumber()));
        roundScores.put(category, round.getRoundNumber());
        updateTotalScore();
    }

    public int calculateScore(ScoreCategory category, List<Integer> dice) {
        if (category == null) {
            throw new IllegalArgumentException("Category cannot be null");
        }
        if (dice == null || dice.size() != 5) {
            throw new IllegalArgumentException("Must provide exactly 5 dice values");
        }

        return category.calculateScore(dice);
    }

    public boolean isScored(ScoreCategory category) {
        return scores.containsKey(category);
    }

    public boolean isCategoryFilled(ScoreCategory category) {
        return scores.containsKey(category);
    }

    public List<ScoreCategory> getAvailableCategories() {
        List<ScoreCategory> available = new ArrayList<>();
        for (ScoreCategory category : ScoreCategory.values()) {
            if (!isScored(category)) {
                available.add(category);
            }
        }
        return available;
    }

    public int getScore(ScoreCategory category) {
        ScoreEntry entry = scores.get(category);
        return entry != null ? entry.getScore() : 0;
    }

    public int getUpperSectionScore() {
        int sum = 0;
        for (ScoreCategory category : ScoreCategory.values()) {
            if (category.isUpperSection()) {
                sum += getScore(category);
            }
        }
        return sum;
    }

    public int getLowerSectionScore() {
        int sum = 0;
        for (ScoreCategory category : ScoreCategory.values()) {
            if (!category.isUpperSection()) {
                sum += getScore(category);
            }
        }
        return sum;
    }

    public int getUpperBonus() {
        return upperBonus;
    }

    public int getTotalScore() {
        return totalScore;
    }

    public int getRoundScore(int roundNumber) {
        int score = 0;
        for (Map.Entry<ScoreCategory, ScoreEntry> entry : scores.entrySet()) {
            if (roundScores.containsKey(entry.getKey()) && roundScores.get(entry.getKey()) == roundNumber) {
                score += entry.getValue().getScore();
            }
        }
        return score;
    }

    private void updateTotalScore() {
        int upperScore = getUpperSectionScore();
        if (upperScore >= UPPER_BONUS_THRESHOLD) {
            upperBonus = UPPER_BONUS_POINTS;
        }
        
        totalScore = upperScore + upperBonus + getLowerSectionScore();
    }

    public Map<ScoreCategory, ScoreEntry> getScores() {
        return Collections.unmodifiableMap(scores);
    }

    public ScoreEntry getEntry(ScoreCategory category) {
        return scores.get(category);
    }

    public boolean isComplete() {
        return scores.size() == TOTAL_ROUNDS;
    }
}
