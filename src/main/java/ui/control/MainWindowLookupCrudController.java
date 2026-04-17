package ui.control;

import country.Country;
import country.CountryRepository;
import expense.ExpenseRepository;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Window;
import javafx.util.Callback;
import location.Location;
import location.LocationRepository;
import trip.TripManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Controller for country and location lookup CRUD workflows from the main window.
 *
 * <p>This class coordinates {@link CountryRepository}, {@link LocationRepository}, and
 * {@link TripManager} constraints while delegating feedback through injected UI callbacks.</p>
 */
public class MainWindowLookupCrudController {
    private final BorderPane rootPane;
    private final TripManager tripManager;
    private final CountryRepository countryRepository;
    private final LocationRepository locationRepository;
    private final ExpenseRepository expenseRepository;
    private final Runnable refreshHeaderAction;
    private final Consumer<String> errorSink;
    private final BiConsumer<String, String> infoSink;

    /**
         * Creates a lookup CRUD coordinator for countries and locations.
         *
         * @param rootPane main window root pane used to resolve dialog owner
         * @param tripManager trip manager used for reference checks
         * @param countryRepository country repository
         * @param locationRepository location repository
         * @param expenseRepository expense repository
         * @param refreshHeaderAction callback to refresh header summary UI
         * @param errorSink callback for surfacing error messages
         * @param infoSink callback for surfacing informational messages
     */
    public MainWindowLookupCrudController(
            BorderPane rootPane,
            TripManager tripManager,
            CountryRepository countryRepository,
            LocationRepository locationRepository,
            ExpenseRepository expenseRepository,
            Runnable refreshHeaderAction,
            Consumer<String> errorSink,
            BiConsumer<String, String> infoSink) {
        this.rootPane = rootPane;
        this.tripManager = tripManager;
        this.countryRepository = countryRepository;
        this.locationRepository = locationRepository;
        this.expenseRepository = expenseRepository;
        this.refreshHeaderAction = refreshHeaderAction;
        this.errorSink = errorSink;
        this.infoSink = infoSink;
    }

    /**
     * Returns all available countries.
     *
     * @return countries from repository
     */
    public List<Country> getAvailableCountries() {
        return countryRepository.getCountries();
    }

    /**
     * Returns all available locations.
     *
     * @return locations from repository
     */
    public List<Location> getAvailableLocations() {
        return locationRepository.getLocations();
    }

    /**
     * Opens the add-country dialog.
     *
     * @return created country, or {@code null} when cancelled
     */
    public Country promptAddCountry() {
        Country country = openAddCountryDialog(getOwnerWindow());
        if (country != null) {
            saveLookupStores();
        }
        return country;
    }

    /**
     * Opens the edit-country dialog.
     *
     * @param country country to edit
     * @return updated country, or {@code null} when cancelled
     */
    public Country promptEditCountry(Country country) {
        if (country == null) {
            showError("Please select a country to edit.");
            return null;
        }
        Country updated = openEditCountryDialog(getOwnerWindow(), country);
        if (updated != null) {
            saveLookupStores();
        }
        return updated;
    }

    /**
     * Opens the add-location dialog.
     *
     * @return created location, or {@code null} when cancelled
     */
    public Location promptAddLocation() {
        Location location = openAddLocationDialog(getOwnerWindow());
        if (location != null) {
            saveLookupStores();
        }
        return location;
    }

    /**
     * Opens the edit-location dialog.
     *
     * @param location location to edit
     * @return updated location, or {@code null} when cancelled
     */
    public Location promptEditLocation(Location location) {
        if (location == null) {
            showError("Please select a location to edit.");
            return null;
        }
        Location updated = openEditLocationDialog(getOwnerWindow(), location);
        if (updated != null) {
            saveLookupStores();
        }
        return updated;
    }

    /**
     * Deletes a country when no dependent references block the operation.
     *
     * @param country country to delete
     * @param onDataChanged callback run after successful deletion
     * @return {@code true} when deletion succeeds
     */
    public boolean deleteCountryFromUi(Country country, Runnable onDataChanged) {
        if (country == null) {
            showError("Please select a country to delete.");
            return false;
        }
        return attemptDeleteCountry(country, onDataChanged);
    }

    /**
     * Deletes a location when no dependent references block the operation.
     *
     * @param location location to delete
     * @param onDataChanged callback run after successful deletion
     * @return {@code true} when deletion succeeds
     */
    public boolean deleteLocationFromUi(Location location, Runnable onDataChanged) {
        if (location == null) {
            showError("Please select a location to delete.");
            return false;
        }
        return attemptDeleteLocation(location, onDataChanged);
    }

    /**
     * Adds contextual delete actions to a location combo box.
     *
     * @param locationCombo combo box to configure
     * @param onDataChanged callback run after successful deletion
     */
    public void configureLocationComboForDelete(ComboBox<Location> locationCombo, Runnable onDataChanged) {
        Callback<ListView<Location>, ListCell<Location>> factory = ignored -> createLocationCellWithDelete(onDataChanged);
        locationCombo.setCellFactory(factory);
        locationCombo.setButtonCell(createLocationCellWithDelete(onDataChanged));
    }

    /**
     * Adds contextual delete actions to a country combo box.
     *
     * @param countryCombo combo box to configure
     * @param onDataChanged callback run after successful deletion
     */
    public void configureCountryComboForDelete(ComboBox<Country> countryCombo, Runnable onDataChanged) {
        Callback<ListView<Country>, ListCell<Country>> factory = ignored -> createCountryCellWithDelete(onDataChanged);
        countryCombo.setCellFactory(factory);
        countryCombo.setButtonCell(createCountryCellWithDelete(onDataChanged));
    }

    private Country openAddCountryDialog(Window owner) {
        Dialog<Country> dialog = new Dialog<>();
        dialog.setTitle("Add Country");
        dialog.setHeaderText("Enter country details (name required)");
        MainWindowDialogSupport.applyDialogTheme(dialog, "form-dialog", "delete-button");
        if (owner != null) {
            dialog.initOwner(owner);
        }

        ButtonType addButtonType = new ButtonType("Add Country", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        TextField nameField = new TextField();
        TextField continentField = new TextField();
        TextField imagePathField = new TextField();
        imagePathField.setEditable(false);
        Button uploadButton = new Button("Upload Image...");
        uploadButton.setOnAction(e -> MainWindowDialogSupport.chooseImagePath(dialog, imagePathField));

        grid.add(new Label("Name*"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Continent"), 0, 1);
        grid.add(continentField, 1, 1);
        grid.add(new Label("Image"), 0, 2);
        grid.add(new HBox(8, imagePathField, uploadButton), 1, 2);

        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton != addButtonType) {
                return null;
            }
            try {
                return countryRepository.addCountry(nameField.getText(), continentField.getText(), imagePathField.getText());
            } catch (Exception ex) {
                showError("Invalid country: " + ex.getMessage());
                return null;
            }
        });

        return dialog.showAndWait().orElse(null);
    }

    private Country openEditCountryDialog(Window owner, Country country) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit Country");
        dialog.setHeaderText("Update country details");
        MainWindowDialogSupport.applyDialogTheme(dialog, "form-dialog", "delete-button");
        if (owner != null) {
            dialog.initOwner(owner);
        }

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        TextField nameField = new TextField(country.getName());
        TextField continentField = new TextField(country.getContinent() == null ? "" : country.getContinent());
        TextField imagePathField = new TextField(country.getImagePath() == null ? "" : country.getImagePath());
        imagePathField.setEditable(false);
        Button uploadButton = new Button("Upload Image...");
        uploadButton.setOnAction(e -> MainWindowDialogSupport.chooseImagePath(dialog, imagePathField));

        grid.add(new Label("Name*"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Continent"), 0, 1);
        grid.add(continentField, 1, 1);
        grid.add(new Label("Image"), 0, 2);
        grid.add(new HBox(8, imagePathField, uploadButton), 1, 2);

        dialog.getDialogPane().setContent(grid);
        final boolean[] updated = new boolean[]{false};
        dialog.showAndWait().ifPresent(result -> {
            if (result != saveButtonType) {
                return;
            }
            try {
                countryRepository.updateCountry(country.getId(), nameField.getText(), continentField.getText(), imagePathField.getText());
                updated[0] = true;
            } catch (Exception e) {
                showError("Invalid country: " + e.getMessage());
            }
        });

        return updated[0] ? countryRepository.findById(country.getId()) : null;
    }

    private Location openAddLocationDialog(Window owner) {
        Dialog<Location> dialog = new Dialog<>();
        dialog.setTitle("Add Location");
        dialog.setHeaderText("Enter location details (name required)");
        MainWindowDialogSupport.applyDialogTheme(dialog, "form-dialog", "delete-button");
        if (owner != null) {
            dialog.initOwner(owner);
        }

        ButtonType addButtonType = new ButtonType("Add Location", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        TextField nameField = new TextField();
        TextField cityField = new TextField();
        TextField addressField = new TextField();
        TextField latitudeField = new TextField();
        TextField longitudeField = new TextField();
        TextField imagePathField = new TextField();
        imagePathField.setEditable(false);

        ComboBox<Country> countryCombo = new ComboBox<>();
        Runnable refreshCountries = () -> refreshCountryCombo(countryCombo);
        refreshCountries.run();
        countryCombo.setConverter(MainWindowDialogSupport.createCountryConverter());
        configureCountryComboForDelete(countryCombo, refreshCountries);
        if (!countryCombo.getItems().isEmpty()) {
            countryCombo.getSelectionModel().selectFirst();
        }

        Button newCountryButton = MainWindowDialogSupport.createAddButton("New...");
        newCountryButton.setOnAction(e -> {
            Country country = openAddCountryDialog(dialog.getDialogPane().getScene().getWindow());
            if (country != null) {
                refreshCountries.run();
                countryCombo.getSelectionModel().select(country);
            }
        });

        Button editCountryButton = MainWindowDialogSupport.createEditButton("Edit...");
        editCountryButton.setOnAction(e -> {
            Country edited = promptEditCountry(countryCombo.getValue());
            if (edited != null) {
                refreshCountries.run();
                countryCombo.getSelectionModel().select(edited);
            }
        });

        Button deleteCountryButton = MainWindowDialogSupport.createDeleteButton("Delete");
        deleteCountryButton.setOnAction(e -> deleteCountryFromUi(countryCombo.getValue(), refreshCountries));

        Button uploadButton = new Button("Upload Image...");
        uploadButton.setOnAction(e -> MainWindowDialogSupport.chooseImagePath(dialog, imagePathField));

        grid.add(new Label("Name*"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Country"), 0, 1);
        grid.add(MainWindowDialogSupport.createResponsiveActionRow(
            countryCombo, newCountryButton, editCountryButton, deleteCountryButton
        ), 1, 1);
        grid.add(new Label("City"), 0, 2);
        grid.add(cityField, 1, 2);
        grid.add(new Label("Address"), 0, 3);
        grid.add(addressField, 1, 3);
        grid.add(new Label("Latitude"), 0, 4);
        grid.add(latitudeField, 1, 4);
        grid.add(new Label("Longitude"), 0, 5);
        grid.add(longitudeField, 1, 5);
        grid.add(new Label("Image"), 0, 6);
        grid.add(new HBox(8, imagePathField, uploadButton), 1, 6);

        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton != addButtonType) {
                return null;
            }
            try {
                Country country = countryCombo.getValue();
                if (country == null) {
                    throw new IllegalArgumentException("Country is required");
                }
                return locationRepository.addLocation(
                    nameField.getText(),
                    addressField.getText(),
                    cityField.getText(),
                    country.getId(),
                    MainWindowDialogSupport.parseOptionalDouble(latitudeField.getText()),
                    MainWindowDialogSupport.parseOptionalDouble(longitudeField.getText()),
                    imagePathField.getText()
                );
            } catch (Exception ex) {
                showError("Invalid location: " + ex.getMessage());
                return null;
            }
        });

        return dialog.showAndWait().orElse(null);
    }

    private Location openEditLocationDialog(Window owner, Location location) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit Location");
        dialog.setHeaderText("Update location details");
        MainWindowDialogSupport.applyDialogTheme(dialog, "form-dialog", "delete-button");
        if (owner != null) {
            dialog.initOwner(owner);
        }

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        TextField nameField = new TextField(location.getName());
        TextField cityField = new TextField(location.getCity() == null ? "" : location.getCity());
        TextField addressField = new TextField(location.getAddress() == null ? "" : location.getAddress());
        TextField latitudeField = new TextField(location.getLatitude() == null ? "" : location.getLatitude().toString());
        TextField longitudeField = new TextField(location.getLongitude() == null ? "" : location.getLongitude().toString());
        TextField imagePathField = new TextField(location.getImagePath() == null ? "" : location.getImagePath());
        imagePathField.setEditable(false);

        ComboBox<Country> countryCombo = new ComboBox<>();
        Runnable refreshCountries = () -> refreshCountryCombo(countryCombo);
        refreshCountries.run();
        countryCombo.setConverter(MainWindowDialogSupport.createCountryConverter());
        configureCountryComboForDelete(countryCombo, refreshCountries);
        if (location.getCountry() != null && countryCombo.getItems().contains(location.getCountry())) {
            countryCombo.getSelectionModel().select(location.getCountry());
        }

        Button newCountryButton = MainWindowDialogSupport.createAddButton("New...");
        newCountryButton.setOnAction(e -> {
            Country created = promptAddCountry();
            if (created != null) {
                refreshCountries.run();
                countryCombo.getSelectionModel().select(created);
            }
        });

        Button editCountryButton = MainWindowDialogSupport.createEditButton("Edit...");
        editCountryButton.setOnAction(e -> {
            Country edited = promptEditCountry(countryCombo.getValue());
            if (edited != null) {
                refreshCountries.run();
                countryCombo.getSelectionModel().select(edited);
            }
        });

        Button deleteCountryButton = MainWindowDialogSupport.createDeleteButton("Delete");
        deleteCountryButton.setOnAction(e -> deleteCountryFromUi(countryCombo.getValue(), refreshCountries));

        Button uploadButton = new Button("Upload Image...");
        uploadButton.setOnAction(e -> MainWindowDialogSupport.chooseImagePath(dialog, imagePathField));

        grid.add(new Label("Name*"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Country"), 0, 1);
        grid.add(MainWindowDialogSupport.createResponsiveActionRow(
            countryCombo, newCountryButton, editCountryButton, deleteCountryButton
        ), 1, 1);
        grid.add(new Label("City"), 0, 2);
        grid.add(cityField, 1, 2);
        grid.add(new Label("Address"), 0, 3);
        grid.add(addressField, 1, 3);
        grid.add(new Label("Latitude"), 0, 4);
        grid.add(latitudeField, 1, 4);
        grid.add(new Label("Longitude"), 0, 5);
        grid.add(longitudeField, 1, 5);
        grid.add(new Label("Image"), 0, 6);
        grid.add(new HBox(8, imagePathField, uploadButton), 1, 6);

        dialog.getDialogPane().setContent(grid);
        final boolean[] updated = new boolean[]{false};
        dialog.showAndWait().ifPresent(result -> {
            if (result != saveButtonType) {
                return;
            }
            try {
                Country selectedCountry = countryCombo.getValue();
                if (selectedCountry == null) {
                    throw new IllegalArgumentException("Country is required");
                }
                locationRepository.updateLocation(
                    location.getId(),
                    nameField.getText(),
                    addressField.getText(),
                    cityField.getText(),
                    selectedCountry.getId(),
                    MainWindowDialogSupport.parseOptionalDouble(latitudeField.getText()),
                    MainWindowDialogSupport.parseOptionalDouble(longitudeField.getText()),
                    imagePathField.getText()
                );
                updated[0] = true;
            } catch (Exception e) {
                showError("Invalid location: " + e.getMessage());
            }
        });

        return updated[0] ? locationRepository.findById(location.getId()) : null;
    }

    private boolean attemptDeleteCountry(Country country, Runnable onDataChanged) {
        int countryId = country.getId();
        List<String> tripReferences = MainWindowReferenceInspector.findCountryReferences(tripManager, countryId);
        if (!tripReferences.isEmpty()) {
            showError("Cannot delete country '" + country.getName() + "'.\nReferenced by trips:\n- "
                + String.join("\n- ", tripReferences));
            return false;
        }

        List<Location> locationsInCountry = new ArrayList<>();
        for (Location location : locationRepository.getLocations()) {
            if (location.getCountry() != null && location.getCountry().getId() == countryId) {
                locationsInCountry.add(location);
            }
        }

        List<String> blockingActivityReferences = new ArrayList<>();
        for (Location location : locationsInCountry) {
            List<String> locationReferences = MainWindowReferenceInspector.findLocationReferences(tripManager, location.getId());
            if (!locationReferences.isEmpty()) {
                blockingActivityReferences.add("Location: " + location.getName());
                for (String reference : locationReferences) {
                    blockingActivityReferences.add("  " + reference);
                }
            }
        }

        if (!blockingActivityReferences.isEmpty()) {
            showError("Cannot delete country '" + country.getName()
                + "'.\nSome linked locations are still referenced by activities:\n- "
                + String.join("\n- ", blockingActivityReferences));
            return false;
        }

        try {
            int deletedLocationCount = 0;
            for (Location location : locationsInCountry) {
                locationRepository.deleteLocationById(location.getId());
                deletedLocationCount++;
            }
            countryRepository.deleteCountryById(country.getId());
            saveLookupStores();
            if (onDataChanged != null) {
                onDataChanged.run();
            }
            String deleteMessage = "Deleted " + country.getName();
            if (deletedLocationCount > 0) {
                deleteMessage += " and " + deletedLocationCount + " linked location(s).";
            }
            showInfo("Country Deleted", deleteMessage);
            refreshHeaderAction.run();
            return true;
        } catch (Exception e) {
            showError("Failed to delete country: " + e.getMessage());
            return false;
        }
    }

    private boolean attemptDeleteLocation(Location location, Runnable onDataChanged) {
        List<String> references = MainWindowReferenceInspector.findLocationReferences(tripManager, location.getId());
        if (!references.isEmpty()) {
            showError("Cannot delete location '" + location.getName() + "'.\nReferenced by:\n- "
                + String.join("\n- ", references));
            return false;
        }

        try {
            locationRepository.deleteLocationById(location.getId());
            saveLookupStores();
            if (onDataChanged != null) {
                onDataChanged.run();
            }
            showInfo("Location Deleted", "Deleted " + location.getName());
            refreshHeaderAction.run();
            return true;
        } catch (Exception e) {
            showError("Failed to delete location: " + e.getMessage());
            return false;
        }
    }

    private void refreshCountryCombo(ComboBox<Country> countryCombo) {
        Country selectedCountry = countryCombo.getValue();
        countryCombo.getItems().setAll(countryRepository.getCountries());
        if (countryCombo.getItems().isEmpty()) {
            countryCombo.getSelectionModel().clearSelection();
            return;
        }
        if (selectedCountry == null || !countryCombo.getItems().contains(selectedCountry)) {
            countryCombo.getSelectionModel().selectFirst();
        }
    }

    private void saveLookupStores() {
        try {
            countryRepository.save();
            locationRepository.save();
            expenseRepository.save();
        } catch (IOException e) {
            showError("Failed to save lookup data: " + e.getMessage());
        }
    }

    private ListCell<Country> createCountryCellWithDelete(Runnable onDataChanged) {
        ListCell<Country> cell = new ListCell<>() {
            @Override
            protected void updateItem(Country item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.toString());
            }
        };
        MenuItem deleteItem = new MenuItem("Delete Country");
        deleteItem.setOnAction(event -> {
            Country country = cell.getItem();
            if (country != null) {
                attemptDeleteCountry(country, onDataChanged);
            }
        });
        ContextMenu contextMenu = new ContextMenu(deleteItem);
        cell.emptyProperty().addListener((obs, wasEmpty, isEmpty) -> cell.setContextMenu(isEmpty ? null : contextMenu));
        return cell;
    }

    private ListCell<Location> createLocationCellWithDelete(Runnable onDataChanged) {
        ListCell<Location> cell = new ListCell<>() {
            @Override
            protected void updateItem(Location item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.toString());
            }
        };
        MenuItem deleteItem = new MenuItem("Delete Location");
        deleteItem.setOnAction(event -> {
            Location location = cell.getItem();
            if (location != null) {
                attemptDeleteLocation(location, onDataChanged);
            }
        });
        ContextMenu contextMenu = new ContextMenu(deleteItem);
        cell.emptyProperty().addListener((obs, wasEmpty, isEmpty) -> cell.setContextMenu(isEmpty ? null : contextMenu));
        return cell;
    }

    private Window getOwnerWindow() {
        return rootPane.getScene() != null ? rootPane.getScene().getWindow() : null;
    }

    private void showError(String message) {
        errorSink.accept(message);
    }

    private void showInfo(String title, String message) {
        infoSink.accept(title, message);
    }
}
