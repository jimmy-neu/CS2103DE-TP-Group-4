package ui.control;

import country.Country;
import exceptions.TimeIntervalConflictException;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import trip.Trip;
import trip.TripManager;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Controller for trip CRUD dialogs and trip-level lifecycle actions.
 *
 * <p>This class collaborates with {@link TripManager}, {@link MainWindowLookupCrudController},
 * and {@link expense.ExpenseRepository} to persist trip changes and clean orphaned expenses.</p>
 */
public class MainWindowTripCrudController {
    private final TripManager tripManager;
    private final expense.ExpenseRepository expenseRepository;
    private final MainWindowLookupCrudController lookupController;
    private final Runnable refreshTripListAction;
    private final Runnable refreshHeaderAction;
    private final Consumer<String> errorSink;

    /**
         * Creates a trip CRUD coordinator.
         *
         * @param tripManager trip lifecycle service
         * @param expenseRepository expense repository for orphan cleanup
         * @param lookupController lookup CRUD controller for country dialogs
         * @param refreshTripListAction callback to refresh trip list UI
         * @param refreshHeaderAction callback to refresh header summary UI
         * @param errorSink callback for surfacing error messages
     */
    public MainWindowTripCrudController(
            TripManager tripManager,
            expense.ExpenseRepository expenseRepository,
            MainWindowLookupCrudController lookupController,
            Runnable refreshTripListAction,
            Runnable refreshHeaderAction,
            Consumer<String> errorSink) {
        this.tripManager = tripManager;
        this.expenseRepository = expenseRepository;
        this.lookupController = lookupController;
        this.refreshTripListAction = refreshTripListAction;
        this.refreshHeaderAction = refreshHeaderAction;
        this.errorSink = errorSink;
    }

    /**
     * Opens the add-trip dialog and persists the created trip.
     */
    public void showAddTripDialog() {
        Dialog<Trip> dialog = new Dialog<>();
        dialog.setTitle("Add Trip");
        dialog.setHeaderText("Enter trip details");
        MainWindowDialogSupport.applyDialogTheme(dialog, "form-dialog", "delete-button");
        ButtonType addButtonType = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        TextField nameField = new TextField();
        DatePicker startDatePicker = new DatePicker(LocalDate.now());
        DatePicker endDatePicker = new DatePicker(LocalDate.now().plusDays(3));
        TextField startTimeField = new TextField("09:00");
        TextField endTimeField = new TextField("18:00");

        ComboBox<Country> countryCombo = new ComboBox<>();
        Runnable refreshCountries = () -> refreshCountryCombo(countryCombo);
        refreshCountries.run();
        countryCombo.setConverter(MainWindowDialogSupport.createCountryConverter());
        lookupController.configureCountryComboForDelete(countryCombo, refreshCountries);
        if (!countryCombo.getItems().isEmpty()) {
            countryCombo.getSelectionModel().selectFirst();
        }

        Button newCountryButton = MainWindowDialogSupport.createAddButton("New...");
        newCountryButton.setOnAction(e -> {
            Country country = lookupController.promptAddCountry();
            if (country != null) {
                refreshCountries.run();
                countryCombo.getSelectionModel().select(country);
            }
        });

        Button editCountryButton = MainWindowDialogSupport.createEditButton("Edit...");
        editCountryButton.setOnAction(e -> {
            Country edited = lookupController.promptEditCountry(countryCombo.getValue());
            if (edited != null) {
                refreshCountries.run();
                countryCombo.getSelectionModel().select(edited);
            }
        });

        Button deleteCountryButton = MainWindowDialogSupport.createDeleteButton("Delete");
        deleteCountryButton.setOnAction(e -> lookupController.deleteCountryFromUi(countryCombo.getValue(), refreshCountries));

        grid.add(new Label("Name*"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Start Date"), 0, 1);
        grid.add(startDatePicker, 1, 1);
        grid.add(new Label("Start Time (HH:mm)"), 0, 2);
        grid.add(startTimeField, 1, 2);
        grid.add(new Label("End Date"), 0, 3);
        grid.add(endDatePicker, 1, 3);
        grid.add(new Label("End Time (HH:mm)"), 0, 4);
        grid.add(endTimeField, 1, 4);
        grid.add(new Label("Country"), 0, 5);
        grid.add(MainWindowDialogSupport.createResponsiveActionRow(
            countryCombo, newCountryButton, editCountryButton, deleteCountryButton
        ), 1, 5);

        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton != addButtonType) {
                return null;
            }
            try {
                String name = nameField.getText();
                LocalDateTime start = LocalDateTime.of(
                    startDatePicker.getValue(),
                    MainWindowDialogSupport.parseTimeOrDefault(startTimeField.getText(), LocalTime.of(0, 0))
                );
                LocalDateTime end = LocalDateTime.of(
                    endDatePicker.getValue(),
                    MainWindowDialogSupport.parseTimeOrDefault(endTimeField.getText(), LocalTime.of(23, 59))
                );
                Country country = countryCombo.getValue();
                if (country == null) {
                    throw new IllegalArgumentException("Country is required");
                }
                return new Trip(tripManager.nextAvailableId(), name, start, end, country);
            } catch (Exception ex) {
                showError("Invalid input: " + ex.getMessage());
                return null;
            }
        });

        dialog.showAndWait().ifPresent(trip -> {
            try {
                tripManager.addTrip(trip);
                tripManager.saveToFile();
                refreshTripListAction.run();
                refreshHeaderAction.run();
            } catch (IllegalArgumentException e) {
                showError("Invalid trip: " + e.getMessage());
            } catch (TimeIntervalConflictException e) {
                showError("Trip time conflict: " + e.getMessage());
            } catch (IOException e) {
                showError("Failed to save: " + e.getMessage());
            }
        });
    }

    /**
     * Opens the edit-trip dialog for a selected trip.
     *
     * @param trip trip to edit
     * @return {@code true} when a save occurred
     */
    public boolean promptEditTrip(Trip trip) {
        if (trip == null) {
            showError("Please select a trip to edit.");
            return false;
        }
        return openEditTripDialog(trip);
    }

    /**
     * Deletes a trip and cleans up orphaned expenses.
     *
     * @param trip trip to delete
     * @return {@code true} when deletion succeeds
     */
    public boolean deleteTripFromUi(Trip trip) {
        if (trip == null) {
            showError("Please select a trip to delete.");
            return false;
        }
        try {
            tripManager.deleteTripById(trip.getId());
            tripManager.saveToFile();
            removeExpensesOwnedByTrip(trip);
            refreshTripListAction.run();
            refreshHeaderAction.run();
            return true;
        } catch (Exception e) {
            showError("Failed to delete trip: " + e.getMessage());
            return false;
        }
    }

    /**
     * Deletes an expense if it is no longer referenced by any trip or activity.
     *
     * @param expenseId expense identifier
     */
    public void cleanupExpenseIfOrphaned(int expenseId) {
        if (isExpenseReferencedAnywhere(expenseId)) {
            return;
        }
        try {
            expenseRepository.deleteExpenseById(expenseId);
            expenseRepository.save();
        } catch (IllegalArgumentException ignored) {
            // Already removed from repository.
        } catch (Exception e) {
            showError("Failed to clean up expense: " + e.getMessage());
        }
    }

    private boolean openEditTripDialog(Trip selected) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit Trip");
        dialog.setHeaderText("Update trip details");
        MainWindowDialogSupport.applyDialogTheme(dialog, "form-dialog", "delete-button");
        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        TextField nameField = new TextField(selected.getName());
        DatePicker startDatePicker = new DatePicker(selected.getStartDateTime().toLocalDate());
        DatePicker endDatePicker = new DatePicker(selected.getEndDateTime().toLocalDate());
        TextField startTimeField = new TextField(selected.getStartDateTime().toLocalTime().toString());
        TextField endTimeField = new TextField(selected.getEndDateTime().toLocalTime().toString());

        ComboBox<Country> countryCombo = new ComboBox<>();
        Runnable refreshCountries = () -> refreshCountryCombo(countryCombo);
        refreshCountries.run();
        countryCombo.setConverter(MainWindowDialogSupport.createCountryConverter());
        lookupController.configureCountryComboForDelete(countryCombo, refreshCountries);
        if (selected.getCountry() != null && countryCombo.getItems().contains(selected.getCountry())) {
            countryCombo.getSelectionModel().select(selected.getCountry());
        }

        Button newCountryButton = MainWindowDialogSupport.createAddButton("New...");
        newCountryButton.setOnAction(e -> {
            Country created = lookupController.promptAddCountry();
            if (created != null) {
                refreshCountries.run();
                countryCombo.getSelectionModel().select(created);
            }
        });

        Button editCountryButton = MainWindowDialogSupport.createEditButton("Edit...");
        editCountryButton.setOnAction(e -> {
            Country edited = lookupController.promptEditCountry(countryCombo.getValue());
            if (edited != null) {
                refreshCountries.run();
                countryCombo.getSelectionModel().select(edited);
            }
        });

        Button deleteCountryButton = MainWindowDialogSupport.createDeleteButton("Delete");
        deleteCountryButton.setOnAction(e -> lookupController.deleteCountryFromUi(countryCombo.getValue(), refreshCountries));

        grid.add(new Label("Name*"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Start Date"), 0, 1);
        grid.add(startDatePicker, 1, 1);
        grid.add(new Label("Start Time (HH:mm)"), 0, 2);
        grid.add(startTimeField, 1, 2);
        grid.add(new Label("End Date"), 0, 3);
        grid.add(endDatePicker, 1, 3);
        grid.add(new Label("End Time (HH:mm)"), 0, 4);
        grid.add(endTimeField, 1, 4);
        grid.add(new Label("Country"), 0, 5);
        grid.add(MainWindowDialogSupport.createResponsiveActionRow(
            countryCombo, newCountryButton, editCountryButton, deleteCountryButton
        ), 1, 5);

        dialog.getDialogPane().setContent(grid);
        final boolean[] updated = new boolean[]{false};
        dialog.showAndWait().ifPresent(result -> {
            if (result != saveButtonType) {
                return;
            }
            try {
                Country updatedCountry = countryCombo.getValue();
                if (updatedCountry == null) {
                    throw new IllegalArgumentException("Country is required");
                }
                LocalDateTime start = LocalDateTime.of(
                    startDatePicker.getValue(),
                    MainWindowDialogSupport.parseTimeOrDefault(startTimeField.getText(), LocalTime.of(0, 0))
                );
                LocalDateTime end = LocalDateTime.of(
                    endDatePicker.getValue(),
                    MainWindowDialogSupport.parseTimeOrDefault(endTimeField.getText(), LocalTime.of(23, 59))
                );
                tripManager.updateTrip(selected.getId(), nameField.getText(), start, end, updatedCountry);
                tripManager.saveToFile();
                refreshTripListAction.run();
                refreshHeaderAction.run();
                updated[0] = true;
            } catch (Exception e) {
                showError("Failed to edit trip: " + e.getMessage());
            }
        });
        return updated[0];
    }

    private void removeExpensesOwnedByTrip(Trip trip) {
        List<Integer> removableExpenseIds = new ArrayList<>();
        for (expense.Expense expense : trip.getExpenses()) {
            removableExpenseIds.add(expense.getId());
        }
        for (activity.Activity activity : trip.getActivities()) {
            for (expense.Expense expense : activity.getExpenses()) {
                removableExpenseIds.add(expense.getId());
            }
        }

        for (Integer expenseId : removableExpenseIds.stream().distinct().toList()) {
            if (isExpenseReferencedAnywhere(expenseId)) {
                continue;
            }
            try {
                expenseRepository.deleteExpenseById(expenseId);
            } catch (IllegalArgumentException ignored) {
                // Expense may already be removed by a previous pass.
            }
        }

        if (!removableExpenseIds.isEmpty()) {
            try {
                expenseRepository.save();
            } catch (IOException e) {
                throw new IllegalStateException("Failed to save expenses after trip deletion", e);
            }
        }
    }

    private boolean isExpenseReferencedAnywhere(int expenseId) {
        for (Trip trip : tripManager.getTrips()) {
            for (expense.Expense expense : trip.getExpenses()) {
                if (expense.getId() == expenseId) {
                    return true;
                }
            }
            for (activity.Activity activity : trip.getActivities()) {
                for (expense.Expense expense : activity.getExpenses()) {
                    if (expense.getId() == expenseId) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void refreshCountryCombo(ComboBox<Country> countryCombo) {
        Country selectedCountry = countryCombo.getValue();
        countryCombo.getItems().setAll(lookupController.getAvailableCountries());
        if (countryCombo.getItems().isEmpty()) {
            countryCombo.getSelectionModel().clearSelection();
            return;
        }
        if (selectedCountry == null || !countryCombo.getItems().contains(selectedCountry)) {
            countryCombo.getSelectionModel().selectFirst();
        }
    }

    private void showError(String message) {
        errorSink.accept(message);
    }
}
