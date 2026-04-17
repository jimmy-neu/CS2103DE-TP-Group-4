package ui;

import activity.Activity;
import country.Country;
import country.CountryRepository;
import expense.ExpenseRepository;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import location.Location;
import location.LocationRepository;
import storage.ImageAssetStore;
import trip.Trip;
import trip.TripManager;
import ui.control.HomeActivitySnapshotController;
import ui.control.MainWindowControl;
import ui.control.MainWindowDialogSupport;
import ui.control.MainWindowLookupCrudController;
import ui.control.MainWindowTripCrudController;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/**
 * JavaFX controller for the application's top-level workspace and navigation.
 *
 * <p>This class wires repositories, trip services, and sub-controllers, and coordinates
 * transitions between home, trip, and activity pages.</p>
 */
public class MainWindow implements MainWindowControl {
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");
    private static final Comparator<Trip> TRIP_DISPLAY_COMPARATOR = Comparator
        .comparingInt(Trip::getPriority).reversed()
        .thenComparing(Trip::getStartDateTime)
        .thenComparing(Trip::getEndDateTime);

    /**
     * Identifies the active top-level page context for contextual help.
     */
    private enum PageContext {
        HOME,
        TRIP,
        ACTIVITY
    }

    @FXML
    private BorderPane rootPane;
    @FXML
    private ListView<Trip> tripListView;
    @FXML
    private HBox homeContent;
    @FXML
    private Button addTripButton;
    @FXML
    private Button editTripButton;
    @FXML
    private Button deleteTripButton;
    @FXML
    private Button helpButton;
    @FXML
    private VBox ongoingActivityContainer;
    @FXML
    private VBox upcomingActivityContainer;

    private final TripManager tripManager = new TripManager();
    private final CountryRepository countryRepository = new CountryRepository();
    private final LocationRepository locationRepository = new LocationRepository(countryRepository);
    private final ExpenseRepository expenseRepository = new ExpenseRepository();
    private final ImageAssetStore imageAssetStore = new ImageAssetStore();
    private final ObservableList<Trip> tripObservableList = FXCollections.observableArrayList();

    private HomeActivitySnapshotController activitySnapshotController;
    private MainWindowLookupCrudController lookupCrudController;
    private MainWindowTripCrudController tripCrudController;
    private PageContext currentPageContext = PageContext.HOME;

    /**
     * Initializes repositories, controller collaborators, and home-page bindings.
     */
    @FXML
    public void initialize() {
        try {
            countryRepository.load();
            locationRepository.load();
            expenseRepository.load();
            tripManager.loadFromFile(countryRepository, locationRepository, expenseRepository);
            expenseRepository.save();
            tripManager.saveToFile();
        } catch (IOException | IllegalStateException e) {
            showError("Could not load saved data: " + e.getMessage());
        }

        activitySnapshotController = new HomeActivitySnapshotController(ongoingActivityContainer, upcomingActivityContainer);
        lookupCrudController = new MainWindowLookupCrudController(
            rootPane,
            tripManager,
            countryRepository,
            locationRepository,
            expenseRepository,
            this::refreshHeaderActivitySummary,
            this::showError,
            this::showInfo
        );
        tripCrudController = new MainWindowTripCrudController(
            tripManager,
            expenseRepository,
            lookupCrudController,
            this::refreshTripList,
            this::refreshHeaderActivitySummary,
            this::showError
        );

        showHomePage();
        if (helpButton != null) {
            helpButton.setOnAction(e -> showGuideForCurrentPage());
        }
        refreshHeaderActivitySummary();
    }

    /**
     * Displays the home page with trip list and summary panels.
     */
    @Override
    public void showHomePage() {
        currentPageContext = PageContext.HOME;
        refreshTripList();
        tripListView.setItems(tripObservableList);
        tripListView.setPlaceholder(new Label("No trips yet. Click Add Trip to begin."));
        tripListView.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Trip trip, boolean empty) {
                super.updateItem(trip, empty);
                if (empty || trip == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                ImageView imageView = new ImageView();
                imageView.setFitHeight(52);
                imageView.setFitWidth(52);
                imageView.setPreserveRatio(true);
                imageView.getStyleClass().add("thumb");
                Image image = resolveImage(trip.getCountry() != null ? trip.getCountry().getImagePath() : null);
                if (image != null) {
                    imageView.setImage(image);
                }

                Label title = new Label(trip.getName());
                title.getStyleClass().add("cell-title");
                Label subtitle = new Label(formatDateTimeRange(trip.getStartDateTime(), trip.getEndDateTime()));
                subtitle.getStyleClass().add("cell-subtitle");
                String countryText = trip.getCountry() != null ? trip.getCountry().getName() : "No country";
                Label countryMeta = new Label(countryText);
                countryMeta.getStyleClass().add("cell-meta");
                int activityCount = trip.getActivities().size();
                String activityText = activityCount + (activityCount == 1 ? " Activity" : " Activities");
                Label activityMeta = new Label(activityText);
                activityMeta.getStyleClass().add("cell-meta");

                VBox textBox = new VBox(3, title, subtitle, countryMeta, activityMeta);
                HBox card = new HBox(10, imageView, textBox);
                card.getStyleClass().add("friendly-cell");
                card.setMaxWidth(Double.MAX_VALUE);
                setText(null);
                setGraphic(card);
            }
        });

        addTripButton.setOnAction(e -> tripCrudController.showAddTripDialog());
        if (editTripButton != null) {
            editTripButton.setOnAction(e -> handleEditTrip());
        }
        deleteTripButton.setOnAction(e -> handleDeleteTrip());
        tripListView.setOnMouseClicked(event -> {
            Trip selected = tripListView.getSelectionModel().getSelectedItem();
            if (selected != null && event.getClickCount() == 2) {
                showTripPage(selected);
            }
        });

        if (homeContent != null) {
            rootPane.setCenter(homeContent);
        }
        refreshHeaderActivitySummary();
    }

    /**
     * Displays the trip page for a selected trip.
     *
     * @param trip trip to show
     */
    @Override
    public void showTripPage(Trip trip) {
        currentPageContext = PageContext.TRIP;
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/TripPage.fxml"));
            BorderPane tripPage = loader.load();
            TripPage controller = loader.getController();
            controller.setTrip(trip);
            controller.setMainWindowControl(this);
            controller.setTripManager(tripManager);
            controller.setExpenseRepository(expenseRepository);
            rootPane.setCenter(tripPage);
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            String detail = root.getMessage();
            if (detail == null || detail.isBlank()) {
                detail = root.getClass().getSimpleName();
            }
            e.printStackTrace();
            showError("Failed to load trip page: " + detail);
        }
    }

    /**
     * Displays the activity page for a selected activity.
     *
     * @param activity activity to show
     * @param tripPage calling trip page used for back-navigation
     */
    @Override
    public void showActivityPage(Activity activity, TripPage tripPage) {
        currentPageContext = PageContext.ACTIVITY;
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/ActivityPage.fxml"));
            BorderPane activityPage = loader.load();
            ActivityPage controller = loader.getController();
            controller.setActivity(activity);
            controller.setTripPage(tripPage);
            controller.setTripManager(tripManager);
            controller.setMainWindowControl(this);
            controller.setExpenseRepository(expenseRepository);
            rootPane.setCenter(activityPage);
        } catch (Exception e) {
            showError("Failed to load activity page: " + e.getMessage());
        }
    }

    /**
     * Returns all available countries from lookup storage.
     *
     * @return available countries
     */
    public List<Country> getAvailableCountries() {
        return lookupCrudController.getAvailableCountries();
    }

    /**
     * Returns all available locations from lookup storage.
     *
     * @return available locations
     */
    @Override
    public List<Location> getAvailableLocations() {
        return lookupCrudController.getAvailableLocations();
    }

    /**
     * Opens the add-country flow.
     *
     * @return created country, or {@code null} when cancelled
     */
    public Country promptAddCountry() {
        return lookupCrudController.promptAddCountry();
    }

    /**
     * Opens the edit-country flow.
     *
     * @param country country to edit
     * @return updated country, or {@code null} when cancelled
     */
    public Country promptEditCountry(Country country) {
        return lookupCrudController.promptEditCountry(country);
    }

    /**
     * Opens the add-location flow.
     *
     * @return created location, or {@code null} when cancelled
     */
    @Override
    public Location promptAddLocation() {
        return lookupCrudController.promptAddLocation();
    }

    /**
     * Opens the edit-location flow.
     *
     * @param location location to edit
     * @return updated location, or {@code null} when cancelled
     */
    @Override
    public Location promptEditLocation(Location location) {
        return lookupCrudController.promptEditLocation(location);
    }

    /**
     * Opens the edit-trip flow.
     *
     * @param trip trip to edit
     * @return {@code true} when a save occurred
     */
    @Override
    public boolean promptEditTrip(Trip trip) {
        return tripCrudController.promptEditTrip(trip);
    }

    /**
     * Deletes a trip from UI and persistent stores.
     *
     * @param trip trip to delete
     * @return {@code true} when deletion succeeds
     */
    public boolean deleteTripFromUi(Trip trip) {
        return tripCrudController.deleteTripFromUi(trip);
    }

    /**
     * Deletes a country from UI and persistent stores.
     *
     * @param country country to delete
     * @param onDataChanged callback run after successful deletion
     * @return {@code true} when deletion succeeds
     */
    public boolean deleteCountryFromUi(Country country, Runnable onDataChanged) {
        return lookupCrudController.deleteCountryFromUi(country, onDataChanged);
    }

    /**
     * Deletes a location from UI and persistent stores.
     *
     * @param location location to delete
     * @param onDataChanged callback run after successful deletion
     * @return {@code true} when deletion succeeds
     */
    @Override
    public boolean deleteLocationFromUi(Location location, Runnable onDataChanged) {
        return lookupCrudController.deleteLocationFromUi(location, onDataChanged);
    }

    /**
     * Removes an expense from repository when no longer referenced.
     *
     * @param expenseId expense identifier
     */
    @Override
    public void cleanupExpenseIfOrphaned(int expenseId) {
        tripCrudController.cleanupExpenseIfOrphaned(expenseId);
    }

    /**
     * Adds delete actions to a location combo box.
     *
     * @param locationCombo combo box to configure
     * @param onDataChanged callback run after successful deletion
     */
    @Override
    public void configureLocationComboForDelete(ComboBox<Location> locationCombo, Runnable onDataChanged) {
        lookupCrudController.configureLocationComboForDelete(locationCombo, onDataChanged);
    }

    /**
     * Refreshes derived UI or data state.
     */
    @Override
    public void refreshHeaderActivitySummary() {
        if (activitySnapshotController == null) {
            return;
        }
        activitySnapshotController.refresh(tripManager.getTrips());
    }

    /**
     * Shows contextual guidance for the trip page.
     */
    public void showTripGuide() {
        showGuideDialog("Trip Page Guide", "Trip page controls",
            List.of(
                "Use Edit Trip to change trip name, dates, and country.",
                "Use Add Activity to build your itinerary for this trip.",
                "Use Edit Activity to update timing, type, and location.",
                "Use Add Expense, Edit Expense, and Delete Expense to manage costs.",
                "Use the activity filter to focus on one activity type.",
                "Use Back to return to the trips list."
            ));
    }

    /**
     * Shows contextual guidance for the activity page.
     */
    public void showActivityGuide() {
        showGuideDialog("Activity Page Guide", "Activity expense controls",
            List.of(
                "Use this page to manage expenses tied to one activity.",
                "Use Add Expense to record new spending.",
                "Use Edit Expense to correct amounts, category, currency, or image.",
                "Use Delete Expense to remove an entry that is no longer needed.",
                "Use Edit Activity to update activity details without leaving this page.",
                "Use Back to return to the trip page."
            ));
    }

    private void handleDeleteTrip() {
        Trip selected = tripListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        tripCrudController.deleteTripFromUi(selected);
    }

    private void handleEditTrip() {
        Trip selected = tripListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Please select a trip to edit.");
            return;
        }
        tripCrudController.promptEditTrip(selected);
    }

    private void showGuideForCurrentPage() {
        switch (currentPageContext) {
        case TRIP:
            showTripGuide();
            break;
        case ACTIVITY:
            showActivityGuide();
            break;
        case HOME:
        default:
            showHomeGuide();
            break;
        }
    }

    private void refreshTripList() {
        List<Trip> sortedTrips = tripManager.getTrips().stream()
            .sorted(TRIP_DISPLAY_COMPARATOR)
            .toList();
        tripObservableList.setAll(sortedTrips);
    }

    private void showHomeGuide() {
        showGuideDialog("Home Page Guide", "Top-level controls",
            List.of(
                "Use Add Trip to create a new trip.",
                "Select a trip and use Edit Trip to update details.",
                "Select a trip and use Delete Trip to remove it.",
                "Double-click a trip card to open its itinerary and expenses.",
                "Inside add/edit forms, use New, Edit, and Delete beside selectors to manage countries and locations."
            ));
    }

    private void showGuideDialog(String title, String header, List<String> pageNotes) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        MainWindowDialogSupport.applyDialogTheme(dialog, "guide-dialog", "secondary-button");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        VBox content = new VBox(10);
        content.getStyleClass().add("guide-content");
        VBox quickStartSection = createGuideSection("Quick Start", List.of(
            "Open this guide any time using the ? button on the current page.",
            "Use Add to create items, Edit to update items, and Delete to remove items.",
            "Use Back buttons to return to the previous page.",
            "Use selector buttons in forms to manage countries and locations without leaving the dialog."
        ));

        VBox pageSection = createGuideSection("This Page", pageNotes);
        content.getChildren().addAll(quickStartSection, pageSection);
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    private VBox createGuideSection(String title, List<String> notes) {
        VBox section = new VBox(6);
        section.getStyleClass().add("guide-section");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("guide-section-title");
        section.getChildren().add(titleLabel);

        for (String note : notes) {
            HBox row = new HBox(8);
            row.getStyleClass().add("guide-note-row");
            Label bullet = new Label("-");
            bullet.getStyleClass().add("guide-bullet");
            Label text = new Label(note);
            text.setWrapText(true);
            text.getStyleClass().add("guide-text");
            HBox.setHgrow(text, Priority.ALWAYS);
            row.getChildren().addAll(bullet, text);
            section.getChildren().add(row);
        }
        return section;
    }

    private Image resolveImage(String imagePath) {
        if (imagePath == null || imagePath.isBlank()) {
            return null;
        }
        try {
            String normalizedPath = imageAssetStore.normalizeImagePath(imagePath);
            if (normalizedPath == null || normalizedPath.isBlank()) {
                return null;
            }
            if (normalizedPath.startsWith("/images/")) {
                InputStream stream = getClass().getResourceAsStream(normalizedPath);
                if (stream != null) {
                    return new Image(stream);
                }
                return null;
            }
            File imageFile = new File(normalizedPath);
            if (!imageFile.exists()) {
                return null;
            }
            return new Image(new FileInputStream(imageFile));
        } catch (Exception e) {
            return null;
        }
    }

    private String formatDateTimeRange(LocalDateTime start, LocalDateTime end) {
        return formatDateTime(start) + " to " + formatDateTime(end);
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DATE_TIME_FORMAT) : "?";
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
