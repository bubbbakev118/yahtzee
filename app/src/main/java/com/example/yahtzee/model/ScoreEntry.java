package com.example.yahtzee.model;

import java.io.Serializable;

public class ScoreEntry implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final int score;
    private final Player player;
    private final int round;

    public ScoreEntry(int score, Player player, int round) {
        this.score = score;
        this.player = player;
        this.round = round;
    }

    public int getScore() {
        return score;
    }

    public Player getPlayer() {
        return player;
    }

    public int getRound() {
        return round;
    }

    @Override
    public String toString() {
        return String.format("%d points (Player: %s, Round: %d)", 
            score, player.getName(), round);
    }
}
