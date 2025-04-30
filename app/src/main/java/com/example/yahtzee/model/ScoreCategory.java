package com.example.yahtzee.model;

import java.util.*;

public enum ScoreCategory {
    // Upper section
    ONES(true, "Ones", "Sum of all ones"),
    TWOS(true, "Twos", "Sum of all twos"),
    THREES(true, "Threes", "Sum of all threes"),
    FOURS(true, "Fours", "Sum of all fours"),
    FIVES(true, "Fives", "Sum of all fives"),
    SIXES(true, "Sixes", "Sum of all sixes"),
    
    // Lower section
    THREE_OF_A_KIND(false, "Three of a Kind", "Sum of all dice if 3+ of one number"),
    FOUR_OF_A_KIND(false, "Four of a Kind", "Sum of all dice if 4+ of one number"),
    FULL_HOUSE(false, "Full House", "25 points for 2 of one number and 3 of another"),
    SMALL_STRAIGHT(false, "Small Straight", "30 points for 4 consecutive numbers"),
    LARGE_STRAIGHT(false, "Large Straight", "40 points for 5 consecutive numbers"),
    YAHTZEE(false, "Yahtzee", "50 points for 5 of the same number");

    private final boolean upperSection;
    private final String displayName;
    private final String description;

    ScoreCategory(boolean upperSection, String displayName, String description) {
        this.upperSection = upperSection;
        this.displayName = displayName;
        this.description = description;
    }

    public boolean isUpperSection() {
        return upperSection;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public int calculateScore(List<Integer> dice) {
        if (dice == null || dice.size() != 5) {
            throw new IllegalArgumentException("Must provide exactly 5 dice values");
        }

        switch (this) {
            case ONES:
                return sumMatchingDice(dice, 1);
            case TWOS:
                return sumMatchingDice(dice, 2);
            case THREES:
                return sumMatchingDice(dice, 3);
            case FOURS:
                return sumMatchingDice(dice, 4);
            case FIVES:
                return sumMatchingDice(dice, 5);
            case SIXES:
                return sumMatchingDice(dice, 6);
            case THREE_OF_A_KIND:
                return hasNOfAKind(dice, 3) ? sumAllDice(dice) : 0;
            case FOUR_OF_A_KIND:
                return hasNOfAKind(dice, 4) ? sumAllDice(dice) : 0;
            case FULL_HOUSE:
                return hasFullHouse(dice) ? 25 : 0;
            case SMALL_STRAIGHT:
                return hasSmallStraight(dice) ? 30 : 0;
            case LARGE_STRAIGHT:
                return hasLargeStraight(dice) ? 40 : 0;
            case YAHTZEE:
                return hasYahtzee(dice) ? 50 : 0;
            default:
                throw new IllegalStateException("Unknown category: " + this);
        }
    }

    private static int sumMatchingDice(List<Integer> dice, int value) {
        return dice.stream()
                  .filter(d -> d == value)
                  .mapToInt(Integer::intValue)
                  .sum();
    }

    private static int sumAllDice(List<Integer> dice) {
        return dice.stream()
                  .mapToInt(Integer::intValue)
                  .sum();
    }

    private static boolean hasNOfAKind(List<Integer> dice, int n) {
        Map<Integer, Long> counts = dice.stream()
            .collect(HashMap::new, (m, d) -> m.merge(d, 1L, Long::sum), HashMap::putAll);
        return counts.values().stream().anyMatch(count -> count >= n);
    }

    private static boolean hasFullHouse(List<Integer> dice) {
        Map<Integer, Long> counts = dice.stream()
            .collect(HashMap::new, (m, d) -> m.merge(d, 1L, Long::sum), HashMap::putAll);
        return counts.size() == 2 && counts.containsValue(2L) && counts.containsValue(3L);
    }

    private static boolean hasSmallStraight(List<Integer> dice) {
        Set<Integer> uniqueDice = new HashSet<>(dice);
        return uniqueDice.containsAll(Arrays.asList(1, 2, 3, 4)) ||
               uniqueDice.containsAll(Arrays.asList(2, 3, 4, 5)) ||
               uniqueDice.containsAll(Arrays.asList(3, 4, 5, 6));
    }

    private static boolean hasLargeStraight(List<Integer> dice) {
        Set<Integer> uniqueDice = new HashSet<>(dice);
        return uniqueDice.containsAll(Arrays.asList(1, 2, 3, 4, 5)) ||
               uniqueDice.containsAll(Arrays.asList(2, 3, 4, 5, 6));
    }

    private static boolean hasYahtzee(List<Integer> dice) {
        return dice.stream().distinct().count() == 1;
    }
}
