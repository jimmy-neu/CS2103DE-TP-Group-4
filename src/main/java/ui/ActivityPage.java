package ui;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import activity.Activity;
import expense.Expense;
import expense.ExpenseRepository;
import javafx.collections.ListChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Dialog;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import expense.Expense.Currency;
import expense.Expense.Type;
import javafx.scene.control.ListCell;
import javafx.stage.Window;
import storage.ImageAssetStore;
import ui.control.MainWindowControl;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * JavaFX controller for viewing and editing a single activity.
 *
 * <p>This page collaborates with {@link MainWindowControl}, {@link TripPage}, and
 * {@link ExpenseRepository} to manage activity details, expense CRUD, and navigation.</p>
 */
public class ActivityPage {
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    @FXML
    private Label activityNameLabel;
    @FXML
    private Label activityDateLabel;
    @FXML
    private Label activityLocationLabel;
    @FXML
    private ListView<Expense> expenseListView;
    @FXML
    private ImageView activityImageView;
    @FXML
    private Button editActivityButton;
    @FXML
    private Button addExpenseButton;
    @FXML
    private Button editExpenseButton;
    @FXML
    private Button deleteExpenseButton;
    @FXML
    private Button backButton;

    private Activity activity;
    private ObservableList<Expense> expenseObservableList = FXCollections.observableArrayList();
    private TripPage tripPage;
    private trip.TripManager tripManager;
    private MainWindowControl mainWindowControl;
    private ExpenseRepository expenseRepository;
    private final ImageAssetStore imageAssetStore = new ImageAssetStore();

    /**
     * Binds this page to an activity and refreshes the displayed fields.
     *
     * @param activity activity to display
     */
    public void setActivity(Activity activity) {
        this.activity = activity;
        activityNameLabel.setText(activity.getName());
        activityDateLabel.setText(formatDateTimeRange(activity.getStartDateTime(), activity.getEndDateTime()));
        activityLocationLabel.setText(activity.getLocation() != null ? activity.getLocation().toString() : "");
        if (activityImageView != null && activity.getLocation() != null) {
            activityImageView.setImage(resolveImage(activity.getLocation().getImagePath()));
        }
        expenseObservableList.setAll(activity.getExpenses());
    }

    /**
     * Injects the trip page used for back-navigation.
     *
     * @param tripPage parent trip page
     */
    public void setTripPage(TripPage tripPage) {
        this.tripPage = tripPage;
    }

    /**
     * Injects the trip manager used for persistence after edits.
     *
     * @param tripManager trip manager
     */
    public void setTripManager(trip.TripManager tripManager) {
        this.tripManager = tripManager;
    }

    /**
     * Injects the main-window control contract used for cross-page interactions.
     *
     * @param mainWindowControl top-level control contract
     */
    public void setMainWindowControl(MainWindowControl mainWindowControl) {
        this.mainWindowControl = mainWindowControl;
    }

    /**
     * Injects the expense repository used by expense dialogs.
     *
     * @param expenseRepository expense repository
     */
    public void setExpenseRepository(ExpenseRepository expenseRepository) {
        this.expenseRepository = expenseRepository;
    }

    @FXML
    private void initialize() {
        expenseListView.setItems(expenseObservableList);
        expenseListView.setPlaceholder(new Label("No expenses added yet."));
        expenseListView.setCellFactory(list -> new ListCell<Expense>() {
            @Override
            protected void updateItem(Expense expense, boolean empty) {
                super.updateItem(expense, empty);
                if (empty || expense == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label title = new Label(expense.getName());
                    title.getStyleClass().add("cell-title");

                    Label subtitle = new Label(String.format("%.2f %s", expense.getCost(), expense.getCurrency()));
                    subtitle.getStyleClass().add("cell-subtitle");

                    Label typeChip = new Label("Type: " + expense.getType().name());
                    typeChip.getStyleClass().add("meta-chip");
                    HBox chipRow = new HBox(6, typeChip);

                    VBox cardText = new VBox(3, title, subtitle, chipRow);

                    ImageView imageView = new ImageView();
                    imageView.setFitHeight(56);
                    imageView.setFitWidth(56);
                    imageView.setPreserveRatio(true);
                    imageView.getStyleClass().add("thumb");
                    Image image = resolveImage(expense.getImagePath());
                    if (image != null) {
                        imageView.setImage(image);
                    }

                    HBox content = new HBox(10, imageView, cardText);

                    VBox card = new VBox(content);
                    card.getStyleClass().add("friendly-cell");
                    setText(null);
                    setGraphic(card);
                }
            }
        });
        backButton.setOnAction(e -> {
            if (tripPage != null) {
                tripPage.showTripPage();
            }
        });
        addExpenseButton.setOnAction(e -> handleAddExpense());
        if (editExpenseButton != null) {
            editExpenseButton.setOnAction(e -> handleEditExpense());
        }
        if (deleteExpenseButton != null) {
            deleteExpenseButton.setOnAction(e -> handleDeleteExpense());
        }
        if (editActivityButton != null) {
            editActivityButton.setOnAction(e -> handleEditActivity());
        }
    }

    private void handleAddExpense() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Add Expense");
        dialog.setHeaderText("Enter expense details");
        applyDialogTheme(dialog);
        ButtonType addButtonType = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        TextField nameField = new TextField();
        TextField costField = new TextField("100.0");
        ComboBox<Currency> currencyCombo = new ComboBox<>();
        currencyCombo.getItems().addAll(Currency.values());
        currencyCombo.getSelectionModel().selectFirst();
        ComboBox<Type> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll(Type.values());
        typeCombo.getSelectionModel().selectFirst();
        TextField imagePathField = new TextField();
        imagePathField.setEditable(false);
        Button uploadButton = new Button("Upload Image...");
        uploadButton.setOnAction(e -> chooseImagePath(dialog, imagePathField));

        grid.add(new Label("Name:"), 0, 0); grid.add(nameField, 1, 0);
        grid.add(new Label("Cost:"), 0, 1); grid.add(costField, 1, 1);
        grid.add(new Label("Currency:"), 0, 2); grid.add(currencyCombo, 1, 2);
        grid.add(new Label("Type:"), 0, 3); grid.add(typeCombo, 1, 3);
        grid.add(new Label("Image:"), 0, 4); grid.add(new HBox(8, imagePathField, uploadButton), 1, 4);

        dialog.getDialogPane().setContent(grid);
        dialog.showAndWait().ifPresent(result -> {
            if (result != addButtonType) {
                return;
            }
            try {
                String name = nameField.getText();
                float cost = Float.parseFloat(costField.getText());
                Currency currency = currencyCombo.getValue();
                Type type = typeCombo.getValue();
                Expense expense;
                if (expenseRepository != null) {
                    expense = expenseRepository.createExpense(name, cost, currency, type, imagePathField.getText());
                    expenseRepository.save();
                } else {
                    expense = new Expense(expenseObservableList.size() + 1, name, cost, currency, type);
                }

                activity.addExpense(expense);
                expenseObservableList.setAll(activity.getExpenses());
                if (tripManager != null) {
                    tripManager.saveToFile();
                }
                if (mainWindowControl != null) {
                    mainWindowControl.refreshHeaderActivitySummary();
                }
            } catch (Exception e) {
                showError("Failed to add expense: " + e.getMessage());
            }
        });
    }

    private void handleEditExpense() {
        Expense selectedExpense = expenseListView.getSelectionModel().getSelectedItem();
        if (selectedExpense == null) {
            showError("Please select an expense to edit.");
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit Expense");
        dialog.setHeaderText("Update expense details");
        applyDialogTheme(dialog);
        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        TextField nameField = new TextField(selectedExpense.getName());
        TextField costField = new TextField(String.format("%.2f", selectedExpense.getCost()));
        ComboBox<Currency> currencyCombo = new ComboBox<>();
        currencyCombo.getItems().addAll(Currency.values());
        currencyCombo.getSelectionModel().select(selectedExpense.getCurrency());
        ComboBox<Type> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll(Type.values());
        typeCombo.getSelectionModel().select(selectedExpense.getType());
        TextField imagePathField = new TextField(selectedExpense.getImagePath() == null ? "" : selectedExpense.getImagePath());
        imagePathField.setEditable(false);
        Button uploadButton = new Button("Upload Image...");
        uploadButton.setOnAction(e -> chooseImagePath(dialog, imagePathField));

        grid.add(new Label("Name:"), 0, 0); grid.add(nameField, 1, 0);
        grid.add(new Label("Cost:"), 0, 1); grid.add(costField, 1, 1);
        grid.add(new Label("Currency:"), 0, 2); grid.add(currencyCombo, 1, 2);
        grid.add(new Label("Type:"), 0, 3); grid.add(typeCombo, 1, 3);
        grid.add(new Label("Image:"), 0, 4); grid.add(new HBox(8, imagePathField, uploadButton), 1, 4);

        dialog.getDialogPane().setContent(grid);
        dialog.showAndWait().ifPresent(result -> {
            if (result != saveButtonType) {
                return;
            }
            try {
                float cost = Float.parseFloat(costField.getText());
                if (expenseRepository != null) {
                    expenseRepository.updateExpense(
                            selectedExpense.getId(),
                            nameField.getText(),
                            cost,
                            currencyCombo.getValue(),
                            typeCombo.getValue(),
                            imagePathField.getText());
                    expenseRepository.save();
                } else {
                    selectedExpense.setName(nameField.getText());
                    selectedExpense.setCost(cost);
                    selectedExpense.setCurrency(currencyCombo.getValue());
                    selectedExpense.setType(typeCombo.getValue());
                    if (!imagePathField.getText().isBlank()) {
                        selectedExpense.setImagePath(imagePathField.getText());
                    }
                }

                expenseObservableList.setAll(activity.getExpenses());
                if (tripManager != null) {
                    tripManager.saveToFile();
                }
                if (mainWindowControl != null) {
                    mainWindowControl.refreshHeaderActivitySummary();
                }
            } catch (Exception e) {
                showError("Failed to edit expense: " + e.getMessage());
            }
        });
    }

    private void handleDeleteExpense() {
        Expense selectedExpense = expenseListView.getSelectionModel().getSelectedItem();
        if (selectedExpense == null) {
            showError("Please select an expense to delete.");
            return;
        }

        try {
            activity.deleteExpenseById(selectedExpense.getId());
            if (tripManager != null) {
                tripManager.saveToFile();
            }
            if (mainWindowControl != null) {
                mainWindowControl.cleanupExpenseIfOrphaned(selectedExpense.getId());
                mainWindowControl.refreshHeaderActivitySummary();
            }
            expenseObservableList.setAll(activity.getExpenses());
        } catch (Exception e) {
            showError("Failed to delete expense: " + e.getMessage());
        }
    }

    private void handleEditActivity() {
        if (activity == null) {
            showError("No activity selected.");
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit Activity");
        dialog.setHeaderText("Update activity details");
        applyDialogTheme(dialog);
        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        TextField nameField = new TextField(activity.getName());
        DatePicker startDatePicker = new DatePicker(activity.getStartDateTime().toLocalDate());
        DatePicker endDatePicker = new DatePicker(activity.getEndDateTime().toLocalDate());
        TextField startTimeField = new TextField(activity.getStartDateTime().toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")));
        TextField endTimeField = new TextField(activity.getEndDateTime().toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")));

        ComboBox<location.Location> locationCombo = new ComboBox<>();
        if (mainWindowControl != null) {
            locationCombo.getItems().setAll(mainWindowControl.getAvailableLocations());
            mainWindowControl.configureLocationComboForDelete(locationCombo,
                () -> locationCombo.getItems().setAll(mainWindowControl.getAvailableLocations()));
        }
        locationCombo.setConverter(new javafx.util.StringConverter<>() {
            /**
             * Returns a string representation of this object.
             */
            @Override
            public String toString(location.Location location) {
                return location == null ? "" : location.toString();
            }

            /**
             * Parsing is not required for this display-only converter.
             */
            @Override
            public location.Location fromString(String string) {
                return null;
            }
        });
        if (activity.getLocation() != null && locationCombo.getItems().contains(activity.getLocation())) {
            locationCombo.getSelectionModel().select(activity.getLocation());
        }

        Button newLocationButton = createAddButton("New...");
        newLocationButton.setOnAction(e -> {
            if (mainWindowControl != null) {
                location.Location created = mainWindowControl.promptAddLocation();
                if (created != null) {
                    locationCombo.getItems().setAll(mainWindowControl.getAvailableLocations());
                    locationCombo.getSelectionModel().select(created);
                }
            }
        });

        Button editLocationButton = createEditButton("Edit...");
        editLocationButton.setOnAction(e -> {
            if (mainWindowControl != null) {
                location.Location edited = mainWindowControl.promptEditLocation(locationCombo.getValue());
                if (edited != null) {
                    locationCombo.getItems().setAll(mainWindowControl.getAvailableLocations());
                    locationCombo.getSelectionModel().select(edited);
                }
            }
        });

        Button deleteLocationButton = createDeleteButton("Delete");
        deleteLocationButton.setOnAction(e -> {
            if (mainWindowControl != null) {
                mainWindowControl.deleteLocationFromUi(locationCombo.getValue(),
                    () -> locationCombo.getItems().setAll(mainWindowControl.getAvailableLocations()));
            }
        });

        ComboBox<Activity.Type> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll(Activity.Type.values());
        Activity.Type selectedType = activity.getTypes().isEmpty() ? Activity.Type.OTHER : activity.getTypes().get(0);
        typeCombo.getSelectionModel().select(selectedType);

        grid.add(new Label("Name:"), 0, 0); grid.add(nameField, 1, 0);
        grid.add(new Label("Start Date:"), 0, 1); grid.add(startDatePicker, 1, 1);
        grid.add(new Label("Start Time (HH:mm):"), 0, 2); grid.add(startTimeField, 1, 2);
        grid.add(new Label("End Date:"), 0, 3); grid.add(endDatePicker, 1, 3);
        grid.add(new Label("End Time (HH:mm):"), 0, 4); grid.add(endTimeField, 1, 4);
        grid.add(new Label("Location:"), 0, 5);
        grid.add(createResponsiveActionRow(locationCombo, newLocationButton, editLocationButton, deleteLocationButton), 1, 5);
        grid.add(new Label("Type:"), 0, 6); grid.add(typeCombo, 1, 6);

        dialog.getDialogPane().setContent(grid);
        dialog.showAndWait().ifPresent(result -> {
            if (result != saveButtonType) {
                return;
            }
            try {
                LocalDateTime start = LocalDateTime.of(startDatePicker.getValue(), parseTimeOrDefault(startTimeField.getText(), LocalTime.of(0, 0)));
                LocalDateTime end = LocalDateTime.of(endDatePicker.getValue(), parseTimeOrDefault(endTimeField.getText(), LocalTime.of(23, 59)));
                if (start.isAfter(end)) {
                    throw new IllegalArgumentException("Start must not be after end");
                }

                activity.setName(nameField.getText());
                activity.setStartDateTime(start);
                activity.setEndDateTime(end);
                activity.setLocation(locationCombo.getValue());
                activity.setTypes(List.of(typeCombo.getValue()));

                setActivity(activity);
                if (tripManager != null) {
                    tripManager.saveToFile();
                }
                if (mainWindowControl != null) {
                    mainWindowControl.refreshHeaderActivitySummary();
                }
            } catch (Exception e) {
                showError("Failed to edit activity: " + e.getMessage());
            }
        });
    }

    private String formatDateTimeRange(LocalDateTime start, LocalDateTime end) {
        return formatDateTime(start) + " to " + formatDateTime(end);
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DATE_TIME_FORMAT) : "?";
    }

    private void applyDialogTheme(Dialog<?> dialog) {
        String stylesheet = getClass().getResource("/view/theme.css").toExternalForm();
        if (!dialog.getDialogPane().getStylesheets().contains(stylesheet)) {
            dialog.getDialogPane().getStylesheets().add(stylesheet);
        }
        if (!dialog.getDialogPane().getStyleClass().contains("form-dialog")) {
            dialog.getDialogPane().getStyleClass().add("form-dialog");
        }
        applyDialogActionStyles(dialog);
        dialog.getDialogPane().getButtonTypes().addListener((ListChangeListener<ButtonType>) change ->
                applyDialogActionStyles(dialog));
    }

    private void applyDialogActionStyles(Dialog<?> dialog) {
        for (ButtonType buttonType : dialog.getDialogPane().getButtonTypes()) {
            if (!buttonType.getButtonData().isCancelButton()) {
                continue;
            }
            Node node = dialog.getDialogPane().lookupButton(buttonType);
            if (node instanceof Button button) {
                applyButtonStyleClass(button, "delete-button");
            }
        }
    }

    private void chooseImagePath(Dialog<?> dialog, TextField targetField) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose Image");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.webp")
        );
        Window window = dialog.getDialogPane().getScene() != null ? dialog.getDialogPane().getScene().getWindow() : null;
        File file = chooser.showOpenDialog(window);
        if (file != null) {
            targetField.setText(file.getAbsolutePath());
        }
    }

    private Button createAddButton(String text) {
        Button button = new Button(text);
        applyButtonStyleClass(button, "add-button");
        return button;
    }

    private Button createEditButton(String text) {
        Button button = new Button(text);
        applyButtonStyleClass(button, "edit-button");
        return button;
    }

    private Button createDeleteButton(String text) {
        Button button = new Button(text);
        applyButtonStyleClass(button, "delete-button");
        return button;
    }

    private HBox createResponsiveActionRow(ComboBox<?> combo, Button addButton, Button editButton, Button deleteButton) {
        combo.setMaxWidth(Double.MAX_VALUE);
        combo.setPrefWidth(240);
        HBox.setHgrow(combo, Priority.ALWAYS);
        lockButtonWidth(addButton);
        lockButtonWidth(editButton);
        lockButtonWidth(deleteButton);
        return new HBox(8, combo, addButton, editButton, deleteButton);
    }

    private void lockButtonWidth(Button button) {
        button.setMinWidth(84);
    }

    private void applyButtonStyleClass(Button button, String styleClass) {
        if (!button.getStyleClass().contains(styleClass)) {
            button.getStyleClass().add(styleClass);
        }
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

    private LocalTime parseTimeOrDefault(String text, LocalTime fallback) {
        try {
            return LocalTime.parse(text);
        } catch (Exception e) {
            return fallback;
        }
    }

    private void showError(String message) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Error");
        dialog.setHeaderText(null);
        applyDialogTheme(dialog);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setContent(new Label(message));
        dialog.showAndWait();
    }

    /**
     * Returns the bound parent trip page.
     *
     * @return parent trip page, or {@code null}
     */
    public TripPage getTripPage() {
        return tripPage;
    }
}
