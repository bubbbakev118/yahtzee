package com.example.yahtzee.callbacks;

import com.example.yahtzee.model.*;
import java.util.List;

public interface TurnCallback {
    void onRollsUpdated(int rollsLeft);
    void onDiceValuesChanged(List<Integer> values);
    void onPredictionMade(ScoreCategory category, String reason, int minPoints, int maxPoints);
    void onTurnComplete(ScoreCategory selectedCategory, int score);
}
