package com.example.yahtzee.view;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Switch;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.widget.EditText;
import android.widget.GridLayout;
import com.example.yahtzee.R;
import com.example.yahtzee.controller.GameController;
import com.example.yahtzee.model.*;
import com.example.yahtzee.callbacks.*;
import java.io.IOException;
import android.os.Handler;
import android.widget.Toast;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

public class MainActivity extends AppCompatActivity implements GameStateCallback {
    private GameController gameController;
    private TextView currentPlayerText;
    private TextView rollsLeftText;
    private TextView suggestionText;
    private Button rollButton;
    private Button endTurnButton;
    private ImageButton[] diceButtons;
    private Switch helpModeSwitch;
    private TableLayout scoreTable;
    private AlertDialog computerTurnDialog;
    private AlertDialog computerRollDialog;
    private View computerInfoPanel;
    private TextView computerInfoText;
    private Button acknowledgeButton;
    private Button nextButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initializeUI();
        setupClickListeners();
        showStartGameDialog();
    }

    private void showStartGameDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_load_game, null);
        builder.setView(dialogView);

        EditText filenameInput = dialogView.findViewById(R.id.filenameInput);
        Button loadButton = dialogView.findViewById(R.id.loadButton);
        Button newGameButton = dialogView.findViewById(R.id.newGameButton);

        AlertDialog dialog = builder.create();
        dialog.setCancelable(false);

        loadButton.setOnClickListener(v -> {
            String filename = filenameInput.getText().toString().trim();
            if (!filename.isEmpty()) {
                try {
                    Tournament tournament = new Tournament(filename);
                    gameController = new GameController(tournament, this);
                    updateUI();
                    dialog.dismiss();
                } catch (IOException e) {
                    Toast.makeText(this, "Error loading game: " + e.getMessage(), 
                                 Toast.LENGTH_SHORT).show();
                }
            }
        });

        newGameButton.setOnClickListener(v -> {
            dialog.dismiss();
            startNewGameWithFirstPlayerDetermination();
        });

        dialog.show();
    }

    private void startNewGameWithFirstPlayerDetermination() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_first_player, null);
        builder.setView(dialogView);

        TextView humanRollText = dialogView.findViewById(R.id.humanRollText);
        TextView computerRollText = dialogView.findViewById(R.id.computerRollText);
        TextView resultText = dialogView.findViewById(R.id.resultText);
        Button rollButton = dialogView.findViewById(R.id.rollButton);

        AlertDialog dialog = builder.create();
        dialog.setCancelable(false);

        rollButton.setOnClickListener(v -> {
            int humanRoll = new java.util.Random().nextInt(6) + 1;
            int computerRoll = new java.util.Random().nextInt(6) + 1;

            humanRollText.setText(String.valueOf(humanRoll));
            computerRollText.setText(String.valueOf(computerRoll));

            if (humanRoll > computerRoll) {
                resultText.setText("Human plays first!");
                rollButton.setEnabled(false);
                new Handler().postDelayed(() -> {
                    dialog.dismiss();
                    startNewGame(true);
                }, 2000);
            } else if (computerRoll > humanRoll) {
                resultText.setText("Computer plays first!");
                rollButton.setEnabled(false);
                new Handler().postDelayed(() -> {
                    dialog.dismiss();
                    startNewGame(false);
                }, 2000);
            } else {
                resultText.setText("It's a tie! Roll again.");
            }
        });

        dialog.show();
    }

    private void loadGame(String filename) {
        try {
            Tournament tournament = new Tournament(filename);
            gameController = new GameController(tournament, this);
            updateUI();
        } catch (IOException e) {
            Toast.makeText(this, "Error loading game: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showSaveGameDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_save_game, null);
        builder.setView(dialogView);

        EditText filenameInput = dialogView.findViewById(R.id.filenameInput);
        Button saveButton = dialogView.findViewById(R.id.saveButton);
        Button cancelButton = dialogView.findViewById(R.id.cancelButton);

        AlertDialog dialog = builder.create();

        saveButton.setOnClickListener(v -> {
            String filename = filenameInput.getText().toString().trim();
            if (!filename.isEmpty()) {
                saveGame(filename);
                dialog.dismiss();
                finish();
            }
        });

        cancelButton.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void saveGame(String filename) {
        try {
            gameController.getTournament().saveGame(filename);
            Toast.makeText(this, "Game saved successfully", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "Error saving game: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void startNewGame(boolean humanFirst) {
        try {
            Tournament tournament = new Tournament(this); // Use default constructor that creates human and computer players
            if (!humanFirst) {
                tournament.setFirstPlayer(tournament.getComputerPlayer());
            }
            
            // Clean up old game controller if it exists
            if (gameController != null) {
                // Add any necessary cleanup here
            }
            
            gameController = new GameController(tournament, this);
            
            // Initialize UI state
            helpModeSwitch.setChecked(false);
            suggestionText.setVisibility(View.GONE);
            updateUI();
        } catch (Exception e) {
            onError("Failed to start new game: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void initializeUI() {
        currentPlayerText = findViewById(R.id.currentPlayerText);
        rollsLeftText = findViewById(R.id.rollsLeftText);
        suggestionText = findViewById(R.id.suggestionText);
        rollButton = findViewById(R.id.rollButton);
        endTurnButton = findViewById(R.id.endTurnButton);
        helpModeSwitch = findViewById(R.id.helpModeSwitch);
        scoreTable = findViewById(R.id.scoreTable);
        
        // Initialize Computer Info Panel with special attention to visibility
        computerInfoPanel = findViewById(R.id.computerInfoPanel);
        computerInfoText = findViewById(R.id.computerInfoText);
        acknowledgeButton = findViewById(R.id.acknowledgeButton);
        nextButton = findViewById(R.id.nextButton);
        
        // Make sure Next button has a large touch target
        nextButton.setMinimumWidth(dpToPx(120));
        nextButton.setMinimumHeight(dpToPx(48));
        
        // Add a bright background to make it stand out
        nextButton.setBackgroundTintList(getColorStateList(android.R.color.holo_blue_light));
        
        // Set up Acknowledge button listener - make sure this is responsive
        acknowledgeButton.setOnClickListener(v -> {
            // Disable button immediately to prevent multiple clicks
            acknowledgeButton.setEnabled(false);
            acknowledgeButton.setText("Skipping...");
            
            // Hide panel visually first for immediate feedback
            computerInfoPanel.setVisibility(View.GONE);
            
            // Create a new thread to handle the skip action
            new Thread(() -> {
                try {
            if (gameController != null) {
                // Skip the rest of the computer's turn
                gameController.skipComputerTurnExplanation();
            }
                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this,
                                      "Error skipping: " + e.getMessage(),
                                      Toast.LENGTH_SHORT).show();
                    });
                } finally {
                    // Re-enable button on UI thread
                    runOnUiThread(() -> {
                        acknowledgeButton.setText("Skip");
                        acknowledgeButton.setEnabled(true);
                    });
                }
            }).start();
        });
        
        // Reset Next button to invisible
        nextButton.setVisibility(View.GONE);
        
        diceButtons = new ImageButton[5];
        diceButtons[0] = findViewById(R.id.dice1);
        diceButtons[1] = findViewById(R.id.dice2);
        diceButtons[2] = findViewById(R.id.dice3);
        diceButtons[3] = findViewById(R.id.dice4);
        diceButtons[4] = findViewById(R.id.dice5);

        Button manualDiceButton = findViewById(R.id.manualDiceButton);
        manualDiceButton.setOnClickListener(v -> showManualDiceDialog());
    }

    private void setupClickListeners() {
        rollButton.setOnClickListener(v -> {
            if (gameController != null) {
                // Disable button temporarily to prevent multiple clicks
                rollButton.setEnabled(false);
                rollButton.setText("Rolling...");
                
                // Roll the dice
                gameController.rollDice();
                
                // Re-enable button after a short delay
                new Handler().postDelayed(() -> {
                    rollButton.setEnabled(true);
                    rollButton.setText("Roll");
                }, 500);
            }
        });

        // Set up dice click listeners
        for (int i = 0; i < diceButtons.length; i++) {
            final int index = i;
            diceButtons[i].setOnClickListener(v -> toggleDieHold(index));
        }

        endTurnButton.setOnClickListener(v -> showCategorySelectionDialog());

        helpModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> toggleHelpMode(isChecked));
    }

    private void toggleDieHold(int index) {
        List<Integer> heldIndices = gameController.getTournament().getCurrentPlayer().getHeldDiceIndices();
        if (heldIndices.contains(index)) {
            gameController.releaseDie(index);
        } else {
            gameController.holdDie(index);
        }
    }

    private void showManualDiceDialog() {
        if (gameController == null || gameController.getTournament() == null) {
            onError("Game not properly initialized");
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_manual_dice, null);
        if (dialogView == null) {
            onError("Could not create dialog");
            return;
        }
        builder.setView(dialogView);

        // Find all EditTexts
        EditText[] diceInputs = new EditText[5];
        TextView errorText = dialogView.findViewById(R.id.errorText);
        
        // Validate UI elements
        boolean uiValid = true;
        for (int i = 0; i < 5; i++) {
            int inputId = getResources().getIdentifier("dice" + (i + 1) + "Input", "id", getPackageName());
            diceInputs[i] = dialogView.findViewById(inputId);
            if (diceInputs[i] == null) {
                uiValid = false;
                break;
            }
        }
        
        if (!uiValid || errorText == null) {
            onError("Dialog layout is missing required elements");
            return;
        }

        builder.setPositiveButton("Set Values", null);
        builder.setNegativeButton("Cancel", (dialog, which) -> {
            if (dialog != null) {
                dialog.dismiss();
            }
        });

        final AlertDialog dialog = builder.create();
        if (dialog == null) {
            onError("Could not create dialog");
            return;
        }
        
        dialog.setOnShowListener(dialogInterface -> {
            Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (positiveButton == null) {
                onError("Dialog buttons not properly initialized");
                dialog.dismiss();
                return;
            }

            positiveButton.setOnClickListener(v -> {
                List<Integer> values = new ArrayList<>();
                boolean validInput = true;

                for (EditText input : diceInputs) {
                    String text = input.getText() != null ? input.getText().toString().trim() : "";
                    try {
                        int value = Integer.parseInt(text);
                        if (value < 1 || value > 6) {
                            throw new NumberFormatException();
                        }
                        values.add(value);
                    } catch (NumberFormatException e) {
                        validInput = false;
                        errorText.setText("Please enter numbers between 1 and 6");
                        errorText.setVisibility(View.VISIBLE);
                        break;
                    }
                }

                if (validInput) {
                    Tournament tournament = gameController.getTournament();
                    if (tournament != null) {
                        Player currentPlayer = tournament.getCurrentPlayer();
                        if (currentPlayer != null) {
                            if (currentPlayer.isComputer()) {
                                gameController.setComputerDiceValues(values);
                            } else {
                                gameController.setManualDiceValues(values);
                            }
                            dialog.dismiss();
                        } else {
                            onError("Current player not set");
                        }
                    } else {
                        onError("Tournament not properly initialized");
                    }
                }
            });
        });

        dialog.show();
    }

    private void showCategorySelectionDialog() {
        if (gameController == null) {
            onError("Game not properly initialized");
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Category");

        List<ScoreCategory> availableCategories = gameController.getAvailableCategories();
        if (availableCategories.isEmpty()) {
            onError("No categories available to score");
            return;
        }

        Map<ScoreCategory, Integer> potentialScores = gameController.getPotentialScores();
        String[] items = new String[availableCategories.size()];

        // Count non-zero scoring categories
        final int nonZeroCount = countNonZeroScoringCategories(availableCategories, potentialScores);

        for (int i = 0; i < availableCategories.size(); i++) {
            ScoreCategory category = availableCategories.get(i);
            int potentialScore = potentialScores.getOrDefault(category, 0);
            items[i] = String.format("%s (%d points)", 
                category.getDisplayName(), 
                potentialScore);
        }

        builder.setItems(items, (dialog, which) -> {
            ScoreCategory selectedCategory = availableCategories.get(which);
            
            // Check if trying to score 0 when other non-zero options exist
            int score = potentialScores.getOrDefault(selectedCategory, 0);
            if (score == 0 && nonZeroCount > 0) {
                // Warn the player and ask for confirmation
                new AlertDialog.Builder(this)
                    .setTitle("Confirm Zero Score")
                    .setMessage("You're about to score 0 points when other categories would score points. Are you sure?")
                    .setPositiveButton("Yes, Score Zero", (confirmDialog, confirmWhich) -> {
                        if (gameController.isCategoryValid(selectedCategory)) {
                            gameController.selectCategory(selectedCategory);
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            } else {
                // Normal case - proceed with selection
                if (gameController.isCategoryValid(selectedCategory)) {
                    gameController.selectCategory(selectedCategory);
                } else {
                    onError("Category " + selectedCategory.name() + " is not available");
                }
            }
        });

        builder.setNegativeButton("Cancel", null);
        
        // Add "Skip Turn" button if all scores would be zero
        if (nonZeroCount == 0 && !availableCategories.isEmpty()) {
            builder.setNeutralButton("Skip Turn", (dialog, which) -> {
                // Confirm skip
                new AlertDialog.Builder(this)
                    .setTitle("Confirm Skip")
                    .setMessage("Skip your turn? You won't use any category this round.")
                    .setPositiveButton("Yes, Skip Turn", (confirmDialog, confirmWhich) -> {
                        gameController.skipTurn("Turn skipped by player.");
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            });
        }
        
        AlertDialog dialog = builder.create();
        if (dialog != null) {
            dialog.show();
        } else {
            onError("Could not create category selection dialog");
        }
    }

    /**
     * Count the number of categories that would score non-zero points
     */
    private int countNonZeroScoringCategories(List<ScoreCategory> categories, Map<ScoreCategory, Integer> scores) {
        int count = 0;
        for (ScoreCategory category : categories) {
            int score = scores.getOrDefault(category, 0);
            if (score > 0) count++;
        }
        return count;
    }

    private void toggleHelpMode(boolean enabled) {
        if (!gameController.isGameInProgress()) {
            helpModeSwitch.setChecked(false);
            Toast.makeText(this, "Start a game first to enable help mode", Toast.LENGTH_SHORT).show();
            return;
        }
        
        suggestionText.setVisibility(enabled ? View.VISIBLE : View.GONE);
        if (enabled) {
            updateSuggestions();
        }
    }

    private void updateSuggestions() {
        if (!helpModeSwitch.isChecked() || gameController == null || !gameController.isGameInProgress()) {
            suggestionText.setVisibility(View.GONE);
            return;
        }

        try {
            List<Integer> diceValues = gameController.getDiceValues();
            if (diceValues.isEmpty()) {
                suggestionText.setText("Roll the dice to get suggestions");
                return;
            }

            // Get suggestions for current dice values
            SuggestionResult suggestion = gameController.getSuggestion();
            if (suggestion != null) {
                String suggestionText = String.format("Suggested category: %s\nPotential points: %d-%d\nReason: %s",
                    suggestion.getCategory().getDisplayName(),
                    suggestion.getMinPoints(),
                    suggestion.getMaxPoints(),
                    suggestion.getReason());
                this.suggestionText.setText(suggestionText);
            } else {
                this.suggestionText.setText("No suggestions available");
            }
        } catch (Exception e) {
            suggestionText.setText("Unable to provide suggestions at this time");
            e.printStackTrace();
        }
    }

    private void updateScoreTable() {
        if (gameController == null) return;
        
        Tournament tournament = gameController.getTournament();
        if (tournament == null) return;
        
        scoreTable.removeAllViews();
        
        // Add header row
        TableRow headerRow = new TableRow(this);
        addCell(headerRow, "Category");
        for (Player player : tournament.getPlayers()) {
            addCell(headerRow, player.getName());
        }
        scoreTable.addView(headerRow);
        
        // Add score rows
        for (ScoreCategory category : ScoreCategory.values()) {
            TableRow row = new TableRow(this);
            addCell(row, category.getDisplayName());
            
            // Get the shared scorecard
            ScoreCard scoreCard = tournament.getScoreCard();
            
            for (Player player : tournament.getPlayers()) {
                String scoreText = "-";
                
                // Check if this category is scored for this player, either as primary or other
                if (scoreCard != null && scoreCard.isCategoryFilled(category)) {
                    if (tournament.hasPlayerScoredCategory(category, player)) {
                        // Get the player's score for this category
                        int playerScore = tournament.getPlayerCategoryScore(category, player);
                        scoreText = String.valueOf(playerScore);
                    }
                }
                
                addCell(row, scoreText);
            }
            
            scoreTable.addView(row);
        }
        
        // Add total score row
        TableRow totalRow = new TableRow(this);
        addCell(totalRow, "Total");
        for (Player player : tournament.getPlayers()) {
            Map<Player, Integer> playerScores = tournament.calculatePlayerScores();
            String totalText = playerScores.containsKey(player) ? 
                String.valueOf(playerScores.get(player)) : "0";
            addCell(totalRow, totalText);
        }
        scoreTable.addView(totalRow);
    }

    private void addCell(TableRow row, String text) {
        TextView cell = new TextView(this);
        cell.setText(text);
        cell.setTextSize(14);
        cell.setPadding(16, 8, 16, 8);
        row.addView(cell);
    }

    private void updateDiceDisplay(List<Integer> diceValues, List<Integer> heldIndices) {
        for (int i = 0; i < diceButtons.length; i++) {
            ImageButton diceButton = diceButtons[i];
            int value = i < diceValues.size() ? diceValues.get(i) : 1;
            boolean isHeld = heldIndices.contains(i);
            
            // Set the background resource based on dice value
            int resourceId;
            switch (value) {
                case 1: resourceId = R.drawable.dice_1; break;
                case 2: resourceId = R.drawable.dice_2; break;
                case 3: resourceId = R.drawable.dice_3; break;
                case 4: resourceId = R.drawable.dice_4; break;
                case 5: resourceId = R.drawable.dice_5; break;
                case 6: resourceId = R.drawable.dice_6; break;
                default: resourceId = R.drawable.dice_1; break;
            }
            
            diceButton.setBackgroundResource(resourceId);
            diceButton.setSelected(isHeld);
            
            // Apply held state overlay if held
            if (isHeld) {
                diceButton.setForeground(getDrawable(R.drawable.dice_held_selector));
            } else {
                diceButton.setForeground(null);
            }
        }
    }

    // GameStateCallback Implementation
    @Override
    public void onRoundStarted(final Round round, final Player firstPlayer) {
        final MainActivity activity = this;
        runOnUiThread(() -> {
            String message = String.format("Round %d started! %s goes first", 
                round.getRoundNumber(), firstPlayer.getName());
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
            updateUI();
        });
    }

    @Override
    public void onTurnStarted(final Player player, final int rollsLeft) {
        final MainActivity activity = this;
        runOnUiThread(() -> {
            // Update player and rolls left information
            currentPlayerText.setText("Current Player: " + player.getName());
            rollsLeftText.setText("Rolls Left: " + rollsLeft);
            
            String message = String.format("%s's turn (%d rolls left)", 
                player.getName(), rollsLeft);
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
            
            updateUI();
        });
    }

    @Override
    public void onDiceRolled(final List<Integer> diceValues, final List<Integer> heldIndices) {
        runOnUiThread(() -> {
            updateDiceDisplay(diceValues, heldIndices);
            updateUI();
        });
    }

    @Override
    public void onDiceHeld(final List<Integer> heldIndices) {
        runOnUiThread(() -> {
            List<Integer> diceValues = gameController.getDiceValues();
            updateDiceDisplay(diceValues, heldIndices);
            updateUI();
        });
    }

    @Override
    public void onScoreSelected(final ScoreCategory category, final int score, final Round round) {
        final MainActivity activity = this;
        runOnUiThread(() -> {
            String message = String.format("Scored %d points in %s", score, category);
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
            updateUI();
        });
    }

    @Override
    public void onRoundComplete(final Round round, final Map<Player, Integer> roundScores) {
        final MainActivity activity = this;
        runOnUiThread(() -> {
            StringBuilder message = new StringBuilder("Round complete!\n");
            for (Map.Entry<Player, Integer> entry : roundScores.entrySet()) {
                message.append(String.format("%s: %d points\n", 
                    entry.getKey().getName(), entry.getValue()));
            }
            Toast.makeText(activity, message.toString(), Toast.LENGTH_LONG).show();
            updateUI();
        });
    }

    @Override
    public void onGameOver(final Player winner, final Map<Player, Integer> finalScores) {
        final MainActivity activity = this;
        runOnUiThread(() -> {
            StringBuilder message = new StringBuilder("Game Over!\n");
            message.append(String.format("Winner: %s\n\nFinal Scores:\n", winner.getName()));
            for (Map.Entry<Player, Integer> entry : finalScores.entrySet()) {
                message.append(String.format("%s: %d points\n", 
                    entry.getKey().getName(), entry.getValue()));
            }
            new AlertDialog.Builder(activity)
                .setTitle("Game Over")
                .setMessage(message.toString())
                .setPositiveButton("New Game", (dialog, which) -> showStartGameDialog())
                .setNegativeButton("Exit", (dialog, which) -> finish())
                .setCancelable(false)
                .show();
        });
    }

    @Override
    public void onComputerTurnAnnouncement(final List<String> announcements) {
        // Always use runOnUiThread to ensure UI updates happen on the main thread
        runOnUiThread(() -> {
            try {
                // Make sure the panel is visible first
                computerInfoPanel.setVisibility(View.VISIBLE);
                
                // Enable buttons
                acknowledgeButton.setEnabled(true);
                
                // Limit the number of announcements to prevent memory issues
                List<String> limitedAnnouncements = announcements;
                if (announcements.size() > 10) {
                    limitedAnnouncements = announcements.subList(announcements.size() - 10, announcements.size());
                }
                
                // Build the message from limited announcements
            StringBuilder message = new StringBuilder();
                for (int i = 0; i < limitedAnnouncements.size(); i++) {
                    String announcement = limitedAnnouncements.get(i);
                    // Limit each announcement to 100 characters to prevent memory issues
                    if (announcement.length() > 100) {
                        announcement = announcement.substring(0, 97) + "...";
                    }
                    message.append(announcement);
                // Only add newline if not the last announcement
                    if (i < limitedAnnouncements.size() - 1) {
                    message.append("\n");
                }
            }
            
                // Ensure total message size is reasonable
                String finalMessage = message.toString();
                if (finalMessage.length() > 1000) {
                    finalMessage = finalMessage.substring(finalMessage.length() - 1000) + "...";
                }
                
                // Update the text on the UI thread
                computerInfoText.setText(finalMessage);
            } catch (Exception e) {
                e.printStackTrace();
                // If there's an error, at least show something in the panel
                computerInfoText.setText("Error displaying computer turn information: " + e.getMessage());
            }
        });
    }

    @Override
    public void onError(final String message) {
        runOnUiThread(() -> {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onComputerTurnEnd() {
        runOnUiThread(() -> {
            // Hide the computer info panel
            computerInfoPanel.setVisibility(View.GONE);
            
            // Reset any computer turn related UI state
            computerInfoText.setText("");
            
            // Update UI after computer turn ends
            updateUI();
        });
    }

    @Override
    public void onComputerRollRequest() {
        if (gameController == null) {
            onError("Game not properly initialized");
            return;
        }
        runOnUiThread(() -> {
            // Show rolling message in the info panel
            computerInfoText.setText("Rolling dice...");
            computerInfoPanel.setVisibility(View.VISIBLE);
        });
    }

    @Override
    public void onComputerTurnWaitingForUser(final String stepDescription, final Runnable callback) {
        // Immediately make a copy of the callback to prevent GC issues
        final Runnable callbackCopy = callback;
        
        // Ensure this runs on the UI thread
        runOnUiThread(() -> {
            try {
                // Log for debugging
                System.out.println("Setting up Next button: " + stepDescription);
                
                // Make sure computer info panel is fully visible first
            computerInfoPanel.setVisibility(View.VISIBLE);
            
                // Reset Next button to default state before configuring
                nextButton.setOnClickListener(null);
                nextButton.setEnabled(true);
            nextButton.setText("Next: " + stepDescription);
                
                // Make the button visible with a slight delay to ensure rendering
                // This helps with Android's rendering pipeline
                nextButton.post(() -> {
            nextButton.setVisibility(View.VISIBLE);
                    nextButton.setEnabled(true);
            
                    // Force layout to ensure visibility
                    computerInfoPanel.requestLayout();
                    nextButton.requestLayout();
                });
                
                // Create a new OnClickListener each time to prevent stale references
                nextButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            // Show immediate feedback
                            nextButton.setText("Processing...");
                            nextButton.setEnabled(false);
                            
                            // Run the callback in a new thread to keep UI responsive
                            new Thread(() -> {
                                try {
                                    // First update UI to hide button
                                    runOnUiThread(() -> {
                nextButton.setVisibility(View.GONE);
                                    });
                                    
                                    // Small delay to let UI update
                                    try {
                                        Thread.sleep(100);
                                    } catch (InterruptedException e) {
                                        // Ignore
                                    }
                                    
                                    // Call the actual callback
                                    if (callbackCopy != null) {
                                        callbackCopy.run();
                                    }
                                } catch (Exception e) {
                                    // Handle errors
                                    e.printStackTrace();
                                    runOnUiThread(() -> {
                                        Toast.makeText(MainActivity.this, 
                                                      "Error: " + e.getMessage(), 
                                                      Toast.LENGTH_SHORT).show();
                                        // Reset button to allow retry
                                        nextButton.setText("Retry: " + stepDescription);
                                        nextButton.setEnabled(true);
                                        nextButton.setVisibility(View.VISIBLE);
                                    });
                                }
                            }, "NextButtonThread").start();
                        } catch (Exception e) {
                            // Handle immediate UI errors
                            e.printStackTrace();
                            Toast.makeText(MainActivity.this, 
                                          "UI Error: " + e.getMessage(), 
                                          Toast.LENGTH_SHORT).show();
                            // Reset button to allow retry
                            nextButton.setText("Retry: " + stepDescription);
                            nextButton.setEnabled(true);
                        }
                    }
                });
                
                // Additional logging for debugging
                System.out.println("Next button setup complete: " + (nextButton.getVisibility() == View.VISIBLE ? "VISIBLE" : "HIDDEN"));
                
            } catch (Exception e) {
                // Handle errors in the setup itself
                e.printStackTrace();
                Toast.makeText(MainActivity.this, 
                              "Setup Error: " + e.getMessage(), 
                              Toast.LENGTH_SHORT).show();
                
                // Emergency fallback - make a simple enabled button
                nextButton.setText("EMERGENCY NEXT");
                nextButton.setEnabled(true);
                nextButton.setVisibility(View.VISIBLE);
                nextButton.setOnClickListener(v -> {
                    if (callbackCopy != null) {
                        callbackCopy.run();
                    }
                });
            }
        });
    }

    private void updateUI() {
        if (gameController == null) {
            // Disable all controls if no game is in progress
            for (ImageButton diceButton : diceButtons) {
                diceButton.setEnabled(false);
            }
            endTurnButton.setEnabled(false);
            helpModeSwitch.setChecked(false);
            suggestionText.setVisibility(View.GONE);
            // Keep roll button enabled for random rolling
            rollButton.setEnabled(true);
            return;
        }

        Tournament tournament = gameController.getTournament();
        if (tournament == null) {
            return;
        }

        Player currentPlayer = tournament.getCurrentPlayer();
        if (currentPlayer == null) {
            return;
        }
        
        // Update player and rolls left text
        currentPlayerText.setText("Current Player: " + currentPlayer.getName());
        
        // Get current turn from the tournament and update rolls left
        Turn currentTurn = tournament.getCurrentTurn();
        if (currentTurn != null) {
            rollsLeftText.setText("Rolls Left: " + currentTurn.getRollsLeft());
        }
        
        // Update score table
        updateScoreTable();
        
        // Update suggestions if help mode is on
        if (helpModeSwitch.isChecked()) {
            updateSuggestions();
        }
        
        // Enable/disable controls based on current player
        boolean isHumanTurn = !currentPlayer.isComputer();
        for (ImageButton diceButton : diceButtons) {
            diceButton.setEnabled(isHumanTurn);
        }
        // Keep roll button always enabled
        rollButton.setEnabled(true);
        endTurnButton.setEnabled(isHumanTurn);
    }

    private int getDiceResource(int value, boolean isHeld) {
        String resourceName = String.format("dice_%d%s", value, isHeld ? "_held" : "");
        return getResources().getIdentifier(resourceName, "drawable", getPackageName());
    }

    // Add a helper method to update UI elements safely
    private void updateUIElement(final Runnable action) {
        if (Thread.currentThread() == getMainLooper().getThread()) {
            // Already on UI thread
            action.run();
        } else {
            // Post to UI thread
            runOnUiThread(action);
        }
    }

    // Helper method to convert dp to pixels
    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    // Check if the Next button is actually visible in the onResume method
    @Override
    protected void onResume() {
        super.onResume();
        
        // Restore Next button state if needed
        if (gameController != null && 
            gameController.isComputerTurnInProgress() && 
            nextButton.getVisibility() != View.VISIBLE) {
            nextButton.setVisibility(View.VISIBLE);
            nextButton.setEnabled(true);
        }
    }

    // XML click handler for Next button
    public void onNextButtonClick(View view) {
        // This is a backup handler for the XML onClick attribute
        // It should only be triggered if the programmatic listener isn't working
        
        // Log the backup click
        System.out.println("Backup Next button handler triggered");
        
        // Enable the button in case it was disabled
        nextButton.setEnabled(true);
        
        // Force a click event on the button
        if (nextButton.hasOnClickListeners()) {
            nextButton.performClick();
        } else {
            // If there's no listener at all, tell the user
            Toast.makeText(this, "Next action not available, please try again", Toast.LENGTH_SHORT).show();
        }
    }
    
    // XML click handler for Skip button
    public void onSkipButtonClick(View view) {
        // This is a backup handler for the XML onClick attribute
        
        // Log the backup click
        System.out.println("Backup Skip button handler triggered");
        
        // Enable the button in case it was disabled
        acknowledgeButton.setEnabled(true);
        
        // Force a click on the actual button
        acknowledgeButton.performClick();
    }
}
