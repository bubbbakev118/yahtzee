package com.example.yahtzee.model;

public class SuggestionResult {
    private final ScoreCategory category;
    private final int score;
    private final String reason;
    private final int minPoints;
    private final int maxPoints;

    public SuggestionResult(ScoreCategory category, int score, String reason) {
        this(category, score, reason, score, score);
    }

    public SuggestionResult(ScoreCategory category, int score, String reason, int minPoints, int maxPoints) {
        this.category = category;
        this.score = score;
        this.reason = reason;
        this.minPoints = minPoints;
        this.maxPoints = maxPoints;
    }

    public ScoreCategory getCategory() {
        return category;
    }

    public int getScore() {
        return score;
    }

    public String getReason() {
        return reason;
    }

    public int getMinPoints() {
        return minPoints;
    }

    public int getMaxPoints() {
        return maxPoints;
    }

    public String getExplanation() {
        if (minPoints == maxPoints) {
            return String.format("%s (%d points)", reason, score);
        } else {
            return String.format("%s (%d-%d points)", reason, minPoints, maxPoints);
        }
    }

    @Override
    public String toString() {
        return getExplanation();
    }
}
