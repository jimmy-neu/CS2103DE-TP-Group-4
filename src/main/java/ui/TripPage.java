package ui;

import activity.Activity;
import expense.Expense;
import expense.ExpenseRepository;
import filter.ActivityFilter;
import javafx.collections.ListChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;
import storage.ImageAssetStore;
import trip.Trip;
import ui.control.MainWindowControl;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JavaFX controller for displaying and editing trip-level details.
 *
 * <p>This page coordinates activity and expense views, timeline rendering, filtering via
 * {@link ActivityFilter}, and navigation through {@link MainWindowControl}.</p>
 */
public class TripPage {
    private static final double MIN_DAY_TIMELINE_WIDTH = 220.0;
    private static final double BLOCK_HEIGHT = 42.0;
    private static final double LANE_GAP = 10.0;
    private static final double MIN_BLOCK_WIDTH = 16.0;
    private static final DateTimeFormatter DAY_HEADER_FORMAT = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");
    private static final Comparator<Activity> ACTIVITY_DISPLAY_COMPARATOR = Comparator
            .comparingInt(Activity::getPriority).reversed()
            .thenComparing(Activity::getStartDateTime)
            .thenComparing(Activity::getEndDateTime);

    @FXML
    private Label tripNameLabel;
    @FXML
    private Label tripDateLabel;
    @FXML
    private Label tripLocationLabel;
    @FXML
    private VBox timelineContainer;
    @FXML
    private ComboBox<String> activityTypeFilter;
    @FXML
    private ListView<Activity> activityListView;
    @FXML
    private ListView<Expense> expenseListView;
    @FXML
    private Button addActivityButton;
    @FXML
    private Button editActivityButton;
    @FXML
    private Button editTripButton;
    @FXML
    private Button addExpenseButton;
    @FXML
    private Button editExpenseButton;
    @FXML
    private Button deleteExpenseButton;
    @FXML
    private Button backButton;
    @FXML
    private ImageView tripImageView;
    @FXML
    private Label totalCostLabel;

    private Trip trip;
    private final ObservableList<Activity> activityObservableList = FXCollections.observableArrayList();
    private final ObservableList<Expense> expenseObservableList = FXCollections.observableArrayList();
    private final Map<Integer, String> expenseSourceActivityById = new HashMap<>();
    private final Set<Integer> overlappingActivityIds = new HashSet<>();
    private final ImageAssetStore imageAssetStore = new ImageAssetStore();
    private ExpenseRepository expenseRepository;
    private MainWindowControl mainWindowControl;
    private trip.TripManager tripManager;

    /**
     * Binds this page to a trip and refreshes all visible sections.
     *
     * @param trip trip to display
     */
    public void setTrip(Trip trip) {
        this.trip = trip;
        tripNameLabel.setText(trip.getName());
        tripDateLabel.setText(formatDateTimeRange(trip.getStartDateTime(), trip.getEndDateTime()));
        tripLocationLabel.setText(trip.getCountry() != null ? trip.getCountry().toString() : "");
        if (tripImageView != null) {
            tripImageView.setImage(resolveImage(trip.getCountry() != null ? trip.getCountry().getImagePath() : null));
        }
        applyActivityFilter();
        refreshExpenseList();
        refreshTimeline();
        refreshTotalCost();
    }

    /**
     * Injects the cross-page control contract used for navigation and shared actions.
     *
     * @param mainWindowControl top-level control contract
     */
    public void setMainWindowControl(MainWindowControl mainWindowControl) {
        this.mainWindowControl = mainWindowControl;
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
     * Injects the expense repository used by expense dialogs.
     *
     * @param expenseRepository expense repository
     */
    public void setExpenseRepository(ExpenseRepository expenseRepository) {
        this.expenseRepository = expenseRepository;
    }

    @FXML
    private void initialize() {
        activityListView.setItems(activityObservableList);
        expenseListView.setItems(expenseObservableList);
        activityListView.setPlaceholder(new Label("No activities yet."));
        expenseListView.setPlaceholder(new Label("No expenses yet."));
        timelineContainer.widthProperty().addListener((obs, oldWidth, newWidth) -> {
            if (trip != null) {
                refreshTimeline();
            }
        });

        activityListView.setCellFactory(list -> new ListCell<Activity>() {
            @Override
            protected void updateItem(Activity activity, boolean empty) {
                super.updateItem(activity, empty);
                if (empty || activity == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    boolean overlaps = overlappingActivityIds.contains(activity.getId());

                    Label title = new Label(activity.getName());
                    title.getStyleClass().add("cell-title");

                        Label subtitle = new Label(formatDateTimeRange(activity.getStartDateTime(), activity.getEndDateTime()));
                    subtitle.getStyleClass().add("cell-subtitle");

                    Label locationMeta = new Label(activity.getLocation() != null
                            ? activity.getLocation().toString()
                            : "No location");
                    locationMeta.getStyleClass().add("cell-meta");

                    Label typeChip = new Label(activity.getTypes().isEmpty()
                            ? "Type: OTHER"
                            : "Type: " + activity.getTypes().get(0).name());
                    typeChip.getStyleClass().add("meta-chip");

                    HBox chipRow = new HBox(6);
                    chipRow.getChildren().add(typeChip);
                    if (overlaps) {
                        Label overlapChip = new Label("Overlap");
                        overlapChip.getStyleClass().add("overlap-chip");
                        chipRow.getChildren().add(overlapChip);
                    }
                    VBox cardText = new VBox(4, title, subtitle, locationMeta, chipRow);

                    ImageView imageView = new ImageView();
                    imageView.setFitHeight(56);
                    imageView.setFitWidth(56);
                    imageView.setPreserveRatio(true);
                    imageView.getStyleClass().add("thumb");
                    if (activity.getLocation() != null) {
                        Image image = resolveImage(activity.getLocation().getImagePath());
                        if (image != null) {
                            imageView.setImage(image);
                        }
                    }

                    HBox content = new HBox(10, imageView, cardText);

                    VBox card = new VBox(content);
                    card.getStyleClass().add("friendly-cell");
                    if (overlaps) {
                        card.getStyleClass().add("friendly-cell-overlap");
                        title.getStyleClass().add("cell-title-overlap");
                    }
                    setText(null);
                    setGraphic(card);
                }
            }
        });

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

                    String sourceActivityName = expenseSourceActivityById.get(expense.getId());
                    Label sourceMeta = null;
                    if (sourceActivityName != null && !sourceActivityName.isBlank()) {
                        sourceMeta = new Label(sourceActivityName);
                        sourceMeta.getStyleClass().add("cell-meta");
                    }

                    Label typeChip = new Label("Type: " + expense.getType().name());
                    typeChip.getStyleClass().add("meta-chip");
                    HBox chipRow = new HBox(6, typeChip);

                    VBox cardText = new VBox(3);
                    cardText.getChildren().addAll(title, subtitle);
                    if (sourceMeta != null) {
                        cardText.getChildren().add(sourceMeta);
                    }
                    cardText.getChildren().add(chipRow);

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
            if (mainWindowControl != null) {
                mainWindowControl.showHomePage();
            }
        });

        activityTypeFilter.getItems().add("ALL");
        for (Activity.Type type : Activity.Type.values()) {
            activityTypeFilter.getItems().add(type.name());
        }
        activityTypeFilter.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item);
            }
        });
        activityTypeFilter.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item);
            }
        });
        activityTypeFilter.setVisibleRowCount(6);
        activityTypeFilter.setPrefWidth(190);

        activityTypeFilter.getSelectionModel().selectFirst();
        activityTypeFilter.setOnAction(e -> applyActivityFilter());

        addActivityButton.setOnAction(e -> handleAddActivity());
        if (editActivityButton != null) {
            editActivityButton.setOnAction(e -> handleEditActivity());
        }
        if (editTripButton != null) {
            editTripButton.setOnAction(e -> handleEditTrip());
        }
        addExpenseButton.setOnAction(e -> handleAddExpense());
        if (editExpenseButton != null) {
            editExpenseButton.setOnAction(e -> handleEditExpense());
        }
        if (deleteExpenseButton != null) {
            deleteExpenseButton.setOnAction(e -> handleDeleteExpense());
        }

        activityListView.setOnMouseClicked(event -> {
            Activity selected = activityListView.getSelectionModel().getSelectedItem();
            if (selected != null && event.getClickCount() == 2 && mainWindowControl != null) {
                mainWindowControl.showActivityPage(selected, this);
            }
        });
    }

    private void refreshTimeline() {
        timelineContainer.getChildren().clear();
        if (trip == null) {
            return;
        }

        refreshActivityOverlapIndex();
        List<Activity> activities = new ArrayList<>(trip.getActivities());
        activities.sort(Comparator.comparing(Activity::getStartDateTime));
        Set<Activity> overlappingActivities = findOverlappingActivities(activities);

        LocalDate day = trip.getStartDateTime().toLocalDate();
        LocalDate tripEndDay = trip.getEndDateTime().toLocalDate();
        while (!day.isAfter(tripEndDay)) {
            List<DaySegment> segments = buildSegmentsForDay(day, activities, overlappingActivities);
            timelineContainer.getChildren().add(buildDayTimelineSection(day, segments));
            day = day.plusDays(1);
        }
    }

    private VBox buildDayTimelineSection(LocalDate day, List<DaySegment> segments) {
        double timelineWidth = getTimelineWidth();
        VBox dayBox = new VBox(8);
        dayBox.getStyleClass().add("timeline-day-card");

        Label dayHeader = new Label(day.format(DAY_HEADER_FORMAT));
        dayHeader.getStyleClass().add("timeline-day-title");

        long overlapCount = segments.stream().filter(segment -> segment.overlaps).count();

        HBox headerRow = new HBox(8);
        headerRow.setAlignment(Pos.CENTER_LEFT);
        headerRow.getStyleClass().add("timeline-day-header");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label itemCountBadge = createTimelineBadge(segments.size() + " item(s)", "timeline-day-badge");
        Label overlapBadge = overlapCount > 0
            ? createTimelineBadge(overlapCount + " overlap(s)", "timeline-day-badge-overlap")
            : createTimelineBadge("No overlaps", "timeline-day-badge-clear");

        headerRow.getChildren().addAll(dayHeader, spacer, itemCountBadge, overlapBadge);

        Pane ruler = createHourRuler(timelineWidth);
        Pane timelinePane = createTimelinePane(segments, timelineWidth);

        dayBox.getChildren().addAll(headerRow, ruler, timelinePane);
        return dayBox;
    }

    private Label createTimelineBadge(String text, String styleClass) {
        Label badge = new Label(text);
        badge.getStyleClass().add(styleClass);
        return badge;
    }

    private Pane createHourRuler(double timelineWidth) {
        Pane ruler = new Pane();
        ruler.getStyleClass().add("timeline-ruler");
        ruler.setPrefWidth(timelineWidth);
        ruler.setMinHeight(18);
        ruler.setPrefHeight(18);

        for (int hour = 0; hour <= 24; hour += 4) {
            Label marker = new Label(String.format("%02d:00", hour));
            marker.getStyleClass().add("timeline-ruler-marker");
            marker.setLayoutX(Math.max(0, minutesToPixels(hour * 60, timelineWidth) - 14));
            marker.setLayoutY(0);
            ruler.getChildren().add(marker);
        }
        return ruler;
    }

    private Pane createTimelinePane(List<DaySegment> segments, double timelineWidth) {
        Pane timelinePane = new Pane();
        timelinePane.getStyleClass().add("timeline-pane");
        timelinePane.setPrefWidth(timelineWidth);

        int laneCount = assignLanes(segments);
        double timelineHeight = Math.max(44.0, 8.0 + laneCount * (BLOCK_HEIGHT + LANE_GAP));
        timelinePane.setMinHeight(timelineHeight);
        timelinePane.setPrefHeight(timelineHeight);

        addHourGridLines(timelinePane, timelineWidth);

        if (segments.isEmpty()) {
            Label emptyState = new Label("No activities planned");
            emptyState.getStyleClass().add("timeline-empty-state");
            emptyState.setLayoutX(10);
            emptyState.setLayoutY(12);
            timelinePane.getChildren().add(emptyState);
            return timelinePane;
        }

        for (DaySegment segment : segments) {
            VBox activityBlock = new VBox(1.5);
            activityBlock.setAlignment(Pos.CENTER_LEFT);
            activityBlock.setPrefHeight(BLOCK_HEIGHT);
            activityBlock.setMinHeight(BLOCK_HEIGHT);

            double x = minutesToPixels(segment.startMinute, timelineWidth);
            double width = Math.max(MIN_BLOCK_WIDTH, minutesToPixels(segment.endMinute - segment.startMinute, timelineWidth));
            double maxAllowedWidth = Math.max(4.0, timelineWidth - x - 1);
            width = Math.min(width, maxAllowedWidth);
            double y = 6 + segment.lane * (BLOCK_HEIGHT + LANE_GAP);

            String typeText = segment.activity.getTypes().isEmpty()
                    ? "OTHER"
                    : segment.activity.getTypes().get(0).name();
            Label blockName = new Label(segment.activity.getName());
            blockName.getStyleClass().add("timeline-block-name");
            blockName.setWrapText(true);

                Label blockMeta = new Label(createTimeRangeText(segment.startMinute, segment.endMinute)
                    + " | " + typeText
                    + (segment.multiDay ? " | Multi-day" : ""));
            blockMeta.getStyleClass().add("timeline-block-meta");
            blockMeta.setWrapText(true);

            activityBlock.getChildren().addAll(blockName, blockMeta);

            activityBlock.setLayoutX(x);
            activityBlock.setLayoutY(y);
            activityBlock.setPrefWidth(width);
            activityBlock.setMaxWidth(width);
            activityBlock.getStyleClass().add("timeline-activity-block");

            if (segment.overlaps) {
                activityBlock.getStyleClass().add("timeline-activity-block-overlap");
            }

            String warning = segment.overlaps ? " (overlap detected)" : "";
            int durationMinutes = (int) Duration.between(segment.activity.getStartDateTime(), segment.activity.getEndDateTime()).toMinutes();
                Tooltip.install(activityBlock, new Tooltip(segment.activity.getName() + ": "
                    + formatDateTimeRange(segment.activity.getStartDateTime(), segment.activity.getEndDateTime())
                    + ", duration " + formatDuration(durationMinutes) + warning));
            timelinePane.getChildren().add(activityBlock);
        }
        return timelinePane;
    }

    private void addHourGridLines(Pane timelinePane, double timelineWidth) {
        for (int hour = 0; hour <= 24; hour++) {
            Region line = new Region();
            line.setLayoutX(minutesToPixels(hour * 60, timelineWidth));
            line.setLayoutY(0);
            line.setPrefWidth(hour % 4 == 0 ? 1.2 : 0.6);
            line.prefHeightProperty().bind(timelinePane.heightProperty());
            if (hour % 4 == 0) {
                line.getStyleClass().add("timeline-grid-line-major");
            } else {
                line.getStyleClass().add("timeline-grid-line-minor");
            }
            timelinePane.getChildren().add(line);
        }
    }

    private String createTimeRangeText(int startMinute, int endMinute) {
        return formatMinuteOfDay(startMinute) + "-" + formatMinuteOfDay(endMinute);
    }

    private String formatMinuteOfDay(int minuteOfDay) {
        if (minuteOfDay >= 24 * 60) {
            return "24:00";
        }
        int normalized = Math.max(0, minuteOfDay);
        return LocalTime.MIN.plusMinutes(normalized).format(TIME_FORMAT);
    }

    private String formatDuration(int minuteCount) {
        int hours = minuteCount / 60;
        int minutes = minuteCount % 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }

    private double minutesToPixels(int minutes, double timelineWidth) {
        return (minutes / 1440.0) * timelineWidth;
    }

    private double getTimelineWidth() {
        double available = timelineContainer.getWidth() - 32;
        if (available <= 0) {
            return MIN_DAY_TIMELINE_WIDTH;
        }
        return Math.max(MIN_DAY_TIMELINE_WIDTH, available);
    }

    private int assignLanes(List<DaySegment> segments) {
        List<Integer> laneEnds = new ArrayList<>();
        for (DaySegment segment : segments) {
            int assignedLane = -1;
            for (int lane = 0; lane < laneEnds.size(); lane++) {
                if (segment.startMinute >= laneEnds.get(lane)) {
                    assignedLane = lane;
                    laneEnds.set(lane, segment.endMinute);
                    break;
                }
            }
            if (assignedLane == -1) {
                laneEnds.add(segment.endMinute);
                assignedLane = laneEnds.size() - 1;
            }
            segment.lane = assignedLane;
        }
        return laneEnds.size();
    }

    private List<DaySegment> buildSegmentsForDay(LocalDate day, List<Activity> activities, Set<Activity> overlappingActivities) {
        LocalDateTime dayStart = day.atStartOfDay();
        LocalDateTime dayEnd = day.plusDays(1).atStartOfDay();

        List<DaySegment> segments = new ArrayList<>();
        for (Activity activity : activities) {
            if (!activity.getEndDateTime().isAfter(dayStart) || !activity.getStartDateTime().isBefore(dayEnd)) {
                continue;
            }

            boolean multiDay = !activity.getStartDateTime().toLocalDate().equals(activity.getEndDateTime().toLocalDate());
            int startMinute;
            int endMinute;
            if (multiDay) {
                // Show full-day span on every overlapping day to avoid partial clipping.
                startMinute = 0;
                endMinute = 24 * 60;
            } else {
                startMinute = (int) Duration.between(dayStart, activity.getStartDateTime()).toMinutes();
                endMinute = (int) Duration.between(dayStart, activity.getEndDateTime()).toMinutes();
            }

            if (endMinute > startMinute) {
                segments.add(new DaySegment(activity, startMinute, endMinute,
                        overlappingActivities.contains(activity), multiDay));
            }
        }
        segments.sort(Comparator
                .comparingInt((DaySegment segment) -> segment.startMinute)
                .thenComparingInt(segment -> segment.endMinute));
        return segments;
    }

    private Set<Activity> findOverlappingActivities(List<Activity> activities) {
        Set<Activity> overlaps = new HashSet<>();
        for (int i = 0; i < activities.size(); i++) {
            Activity current = activities.get(i);
            for (int j = i + 1; j < activities.size(); j++) {
                Activity other = activities.get(j);
                if (current.overlapsWith(other)) {
                    overlaps.add(current);
                    overlaps.add(other);
                }
            }
        }
        return overlaps;
    }

    private void refreshActivityOverlapIndex() {
        overlappingActivityIds.clear();
        if (trip == null) {
            return;
        }
        for (Activity activity : findOverlappingActivities(trip.getActivities())) {
            overlappingActivityIds.add(activity.getId());
        }
    }

    private void applyActivityFilter() {
        if (trip == null) {
            return;
        }
        refreshActivityOverlapIndex();
        String selected = activityTypeFilter.getSelectionModel().getSelectedItem();
        Activity.Type filterType = null;
        if (selected != null && !"ALL".equals(selected)) {
            filterType = Activity.Type.valueOf(selected);
        }
        List<Activity> filtered = new ArrayList<>(ActivityFilter.byType(trip.getActivities(), filterType));
        filtered.sort(ACTIVITY_DISPLAY_COMPARATOR);
        activityObservableList.setAll(filtered);
        activityListView.refresh();
    }

    private void refreshTotalCost() {
        StringBuilder sb = new StringBuilder("Total Cost: ");
        boolean first = true;
        for (Expense.Currency currency : Expense.Currency.values()) {
            float total = trip.getTotalCost(currency);
            if (total > 0) {
                if (!first) {
                    sb.append("    ");
                }
                sb.append(String.format("%.2f %s", total, currency.name()));
                first = false;
            }
        }
        if (first) {
            sb.append("No expenses yet");
        }
        totalCostLabel.setText(sb.toString());
    }

    private void refreshExpenseList() {
        if (trip == null) {
            expenseSourceActivityById.clear();
            expenseObservableList.clear();
            return;
        }
        expenseSourceActivityById.clear();
        List<Expense> merged = new ArrayList<>();
        merged.addAll(trip.getExpenses());
        for (Activity activity : trip.getActivities()) {
            for (Expense expense : activity.getExpenses()) {
                merged.add(expense);
                expenseSourceActivityById.putIfAbsent(expense.getId(), activity.getName());
            }
        }
        expenseObservableList.setAll(merged);
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
        ComboBox<Expense.Currency> currencyCombo = new ComboBox<>();
        currencyCombo.getItems().addAll(Expense.Currency.values());
        currencyCombo.getSelectionModel().selectFirst();
        ComboBox<Expense.Type> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll(Expense.Type.values());
        typeCombo.getSelectionModel().selectFirst();
        TextField imagePathField = new TextField();
        imagePathField.setEditable(false);
        Button uploadButton = new Button("Upload Image...");
        uploadButton.setOnAction(e -> chooseImagePath(dialog, imagePathField));

        grid.add(new Label("Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Cost:"), 0, 1);
        grid.add(costField, 1, 1);
        grid.add(new Label("Currency:"), 0, 2);
        grid.add(currencyCombo, 1, 2);
        grid.add(new Label("Type:"), 0, 3);
        grid.add(typeCombo, 1, 3);
        grid.add(new Label("Image:"), 0, 4);
        grid.add(new HBox(8, imagePathField, uploadButton), 1, 4);

        dialog.getDialogPane().setContent(grid);
        dialog.showAndWait().ifPresent(result -> {
            if (result != addButtonType) {
                return;
            }
            try {
                String name = nameField.getText();
                float cost = Float.parseFloat(costField.getText());
                Expense.Currency currency = currencyCombo.getValue();
                Expense.Type type = typeCombo.getValue();
                Expense expense;
                if (expenseRepository != null) {
                    expense = expenseRepository.createExpense(name, cost, currency, type, imagePathField.getText());
                    expenseRepository.save();
                } else {
                    expense = new Expense(expenseObservableList.size() + 1, name, cost, currency, type);
                }

                trip.addExpense(expense);
                refreshExpenseList();
                refreshTotalCost();
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
        ComboBox<Expense.Currency> currencyCombo = new ComboBox<>();
        currencyCombo.getItems().addAll(Expense.Currency.values());
        currencyCombo.getSelectionModel().select(selectedExpense.getCurrency());
        ComboBox<Expense.Type> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll(Expense.Type.values());
        typeCombo.getSelectionModel().select(selectedExpense.getType());
        TextField imagePathField = new TextField(selectedExpense.getImagePath() == null ? "" : selectedExpense.getImagePath());
        imagePathField.setEditable(false);
        Button uploadButton = new Button("Upload Image...");
        uploadButton.setOnAction(e -> chooseImagePath(dialog, imagePathField));

        grid.add(new Label("Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Cost:"), 0, 1);
        grid.add(costField, 1, 1);
        grid.add(new Label("Currency:"), 0, 2);
        grid.add(currencyCombo, 1, 2);
        grid.add(new Label("Type:"), 0, 3);
        grid.add(typeCombo, 1, 3);
        grid.add(new Label("Image:"), 0, 4);
        grid.add(new HBox(8, imagePathField, uploadButton), 1, 4);

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

                refreshExpenseList();
                refreshTotalCost();
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
            boolean removed = removeExpenseFromTrip(selectedExpense.getId());
            if (!removed) {
                throw new IllegalArgumentException("Selected expense is no longer attached to this trip.");
            }

            if (tripManager != null) {
                tripManager.saveToFile();
            }
            if (mainWindowControl != null) {
                mainWindowControl.cleanupExpenseIfOrphaned(selectedExpense.getId());
            }

            refreshExpenseList();
            refreshTotalCost();
            if (mainWindowControl != null) {
                mainWindowControl.refreshHeaderActivitySummary();
            }
        } catch (Exception e) {
            showError("Failed to delete expense: " + e.getMessage());
        }
    }

    private boolean removeExpenseFromTrip(int expenseId) {
        for (Expense expense : trip.getExpenses()) {
            if (expense.getId() == expenseId) {
                try {
                    trip.deleteExpenseById(expenseId);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
        }

        for (Activity activity : trip.getActivities()) {
            for (Expense expense : activity.getExpenses()) {
                if (expense.getId() == expenseId) {
                    try {
                        activity.deleteExpenseById(expenseId);
                        return true;
                    } catch (Exception e) {
                        return false;
                    }
                }
            }
        }

        return false;
    }

    private void handleEditTrip() {
        if (trip == null || mainWindowControl == null) {
            return;
        }
        boolean updated = mainWindowControl.promptEditTrip(trip);
        if (updated) {
            setTrip(trip);
        }
    }

    private void handleAddActivity() {
        Dialog<Activity> dialog = new Dialog<>();
        dialog.setTitle("Add Activity");
        dialog.setHeaderText("Enter activity details");
        applyDialogTheme(dialog);
        ButtonType addButtonType = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        TextField nameField = new TextField();
        LocalDate tripStart = trip.getStartDateTime().toLocalDate();
        LocalDate tripEnd = trip.getEndDateTime().toLocalDate();
        DatePicker startDatePicker = new DatePicker(tripStart);
        DatePicker endDatePicker = new DatePicker(tripEnd);
        TextField startTimeField = new TextField("00:00");
        TextField endTimeField = new TextField("23:59");
        ComboBox<location.Location> locationCombo = new ComboBox<>();
        Runnable refreshLocations = () -> {
            if (mainWindowControl == null) {
                return;
            }
            location.Location selectedLocation = locationCombo.getValue();
            List<location.Location> refreshed = new ArrayList<>();
            for (location.Location location : mainWindowControl.getAvailableLocations()) {
                if (trip.getCountry() == null
                        || location.getCountry() == null
                        || location.getCountry().getId() == trip.getCountry().getId()) {
                    refreshed.add(location);
                }
            }
            locationCombo.getItems().setAll(refreshed);
            if (locationCombo.getItems().isEmpty()) {
                locationCombo.getSelectionModel().clearSelection();
                return;
            }
            if (selectedLocation == null || !locationCombo.getItems().contains(selectedLocation)) {
                locationCombo.getSelectionModel().selectFirst();
            }
        };
        if (mainWindowControl != null) {
            refreshLocations.run();
            mainWindowControl.configureLocationComboForDelete(locationCombo, refreshLocations);
        }
        locationCombo.setConverter(new StringConverter<>() {
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
        if (!locationCombo.getItems().isEmpty()) {
            locationCombo.getSelectionModel().selectFirst();
        }
        Button newLocationButton = createAddButton("New...");
        newLocationButton.setOnAction(e -> {
            if (mainWindowControl != null) {
                location.Location location = mainWindowControl.promptAddLocation();
                if (location != null) {
                    refreshLocations.run();
                    locationCombo.getSelectionModel().select(location);
                }
            }
        });
        Button editLocationButton = createEditButton("Edit...");
        editLocationButton.setOnAction(e -> {
            if (mainWindowControl != null) {
                location.Location edited = mainWindowControl.promptEditLocation(locationCombo.getValue());
                if (edited != null) {
                    refreshLocations.run();
                    locationCombo.getSelectionModel().select(edited);
                }
            }
        });
        Button deleteLocationButton = createDeleteButton("Delete");
        deleteLocationButton.setOnAction(e -> {
            if (mainWindowControl != null) {
                mainWindowControl.deleteLocationFromUi(locationCombo.getValue(), refreshLocations);
            }
        });
        HBox locationRow = createResponsiveActionRow(locationCombo, newLocationButton, editLocationButton,
                deleteLocationButton);

        ComboBox<Activity.Type> activityTypeCombo = new ComboBox<>();
        activityTypeCombo.getItems().addAll(Activity.Type.values());
        activityTypeCombo.getSelectionModel().selectFirst();

        grid.add(new Label("Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Start Date:"), 0, 1);
        grid.add(startDatePicker, 1, 1);
        grid.add(new Label("Start Time (HH:mm):"), 0, 2);
        grid.add(startTimeField, 1, 2);
        grid.add(new Label("End Date:"), 0, 3);
        grid.add(endDatePicker, 1, 3);
        grid.add(new Label("End Time (HH:mm):"), 0, 4);
        grid.add(endTimeField, 1, 4);
        grid.add(new Label("Location:"), 0, 5);
        grid.add(locationRow, 1, 5);

        grid.add(new Label("Type:"), 0, 6);
        grid.add(activityTypeCombo, 1, 6);

        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == addButtonType) {
                try {
                    String name = nameField.getText();
                    LocalDate startDate = startDatePicker.getValue();
                    LocalDate endDate = endDatePicker.getValue();
                    LocalTime startTime;
                    LocalTime endTime;
                    try {
                        startTime = LocalTime.parse(startTimeField.getText());
                    } catch (Exception e) {
                        startTime = LocalTime.of(0, 0);
                    }
                    try {
                        endTime = LocalTime.parse(endTimeField.getText());
                    } catch (Exception e) {
                        endTime = LocalTime.of(23, 59);
                    }
                    LocalDateTime start = (startDate != null) ? LocalDateTime.of(startDate, startTime) : null;
                    LocalDateTime end = (endDate != null) ? LocalDateTime.of(endDate, endTime) : null;
                    location.Location loc = locationCombo.getValue();
                    Activity newActivity = new Activity(activityObservableList.size() + 1, name, start, end, loc);
                    newActivity.addType(activityTypeCombo.getValue());
                    return newActivity;
                } catch (Exception ex) {
                    // Intentionally returns null for invalid input.
                }
            }
            return null;
        });

        dialog.showAndWait().ifPresent(activity -> {
            try {
                trip.addActivity(activity);
                applyActivityFilter();
                refreshTimeline();
                refreshExpenseList();
                refreshTotalCost();
                if (tripManager != null) {
                    tripManager.saveToFile();
                }
                if (mainWindowControl != null) {
                    mainWindowControl.refreshHeaderActivitySummary();
                }
            } catch (Exception e) {
                showError("Failed to add activity: " + e.getMessage());
            }
        });
    }

    private void handleEditActivity() {
        Activity selectedActivity = activityListView.getSelectionModel().getSelectedItem();
        if (selectedActivity == null) {
            showError("Please select an activity to edit.");
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

        TextField nameField = new TextField(selectedActivity.getName());
        DatePicker startDatePicker = new DatePicker(selectedActivity.getStartDateTime().toLocalDate());
        DatePicker endDatePicker = new DatePicker(selectedActivity.getEndDateTime().toLocalDate());
        TextField startTimeField = new TextField(selectedActivity.getStartDateTime().toLocalTime().format(TIME_FORMAT));
        TextField endTimeField = new TextField(selectedActivity.getEndDateTime().toLocalTime().format(TIME_FORMAT));

        ComboBox<location.Location> locationCombo = new ComboBox<>();
        Runnable refreshLocations = () -> {
            if (mainWindowControl == null) {
                return;
            }
            location.Location selectedLocation = locationCombo.getValue();
            List<location.Location> refreshed = new ArrayList<>();
            for (location.Location location : mainWindowControl.getAvailableLocations()) {
                if (trip.getCountry() == null
                        || location.getCountry() == null
                        || location.getCountry().getId() == trip.getCountry().getId()) {
                    refreshed.add(location);
                }
            }
            locationCombo.getItems().setAll(refreshed);
            if (locationCombo.getItems().isEmpty()) {
                locationCombo.getSelectionModel().clearSelection();
                return;
            }
            if (selectedLocation == null || !locationCombo.getItems().contains(selectedLocation)) {
                locationCombo.getSelectionModel().selectFirst();
            }
        };
        if (mainWindowControl != null) {
            refreshLocations.run();
            mainWindowControl.configureLocationComboForDelete(locationCombo, refreshLocations);
        }
        locationCombo.setConverter(new StringConverter<>() {
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
        if (selectedActivity.getLocation() != null && locationCombo.getItems().contains(selectedActivity.getLocation())) {
            locationCombo.getSelectionModel().select(selectedActivity.getLocation());
        }

        Button newLocationButton = createAddButton("New...");
        newLocationButton.setOnAction(e -> {
            if (mainWindowControl != null) {
                location.Location location = mainWindowControl.promptAddLocation();
                if (location != null) {
                    refreshLocations.run();
                    locationCombo.getSelectionModel().select(location);
                }
            }
        });
        Button editLocationButton = createEditButton("Edit...");
        editLocationButton.setOnAction(e -> {
            if (mainWindowControl != null) {
                location.Location edited = mainWindowControl.promptEditLocation(locationCombo.getValue());
                if (edited != null) {
                    refreshLocations.run();
                    locationCombo.getSelectionModel().select(edited);
                }
            }
        });
        Button deleteLocationButton = createDeleteButton("Delete");
        deleteLocationButton.setOnAction(e -> {
            if (mainWindowControl != null) {
                mainWindowControl.deleteLocationFromUi(locationCombo.getValue(), refreshLocations);
            }
        });

        ComboBox<Activity.Type> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll(Activity.Type.values());
        Activity.Type currentType = selectedActivity.getTypes().isEmpty() ? Activity.Type.OTHER : selectedActivity.getTypes().get(0);
        typeCombo.getSelectionModel().select(currentType);

        grid.add(new Label("Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Start Date:"), 0, 1);
        grid.add(startDatePicker, 1, 1);
        grid.add(new Label("Start Time (HH:mm):"), 0, 2);
        grid.add(startTimeField, 1, 2);
        grid.add(new Label("End Date:"), 0, 3);
        grid.add(endDatePicker, 1, 3);
        grid.add(new Label("End Time (HH:mm):"), 0, 4);
        grid.add(endTimeField, 1, 4);
        grid.add(new Label("Location:"), 0, 5);
        grid.add(createResponsiveActionRow(locationCombo, newLocationButton, editLocationButton,
            deleteLocationButton), 1, 5);
        grid.add(new Label("Type:"), 0, 6);
        grid.add(typeCombo, 1, 6);

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

                selectedActivity.setName(nameField.getText());
                selectedActivity.setStartDateTime(start);
                selectedActivity.setEndDateTime(end);
                selectedActivity.setLocation(locationCombo.getValue());
                selectedActivity.setTypes(List.of(typeCombo.getValue()));

                applyActivityFilter();
                refreshTimeline();
                refreshExpenseList();
                refreshTotalCost();
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

    /**
     * Navigates back to this trip page from a child page.
     */
    public void showTripPage() {
        if (mainWindowControl != null && trip != null) {
            mainWindowControl.showTripPage(trip);
        }
    }

    /**
     * Returns the currently bound trip.
     *
     * @return currently displayed trip, or {@code null}
     */
    public Trip getTrip() {
        return trip;
    }

    private String formatDateTimeRange(LocalDateTime start, LocalDateTime end) {
        return formatDateTime(start) + " to " + formatDateTime(end);
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DATE_TIME_FORMAT) : "?";
    }

    private LocalTime parseTimeOrDefault(String text, LocalTime fallback) {
        try {
            return LocalTime.parse(text);
        } catch (Exception e) {
            return fallback;
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
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
        Window owner = dialog.getDialogPane().getScene() != null ? dialog.getDialogPane().getScene().getWindow() : null;
        if (owner != null) {
            owner.sizeToScene();
        }
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

    /**
     * Represents the class DaySegment.
     */
    private static class DaySegment {
        private final Activity activity;
        private final int startMinute;
        private final int endMinute;
        private final boolean overlaps;
        private final boolean multiDay;
        private int lane;

        /**
         * Creates a timeline segment projection for one activity span.
         * @param activity source activity represented by this segment.
         * @param startMinute segment start offset in minutes from day start.
         * @param endMinute segment end offset in minutes from day start.
         * @param overlaps whether the segment overlaps another segment.
         * @param multiDay whether the segment crosses day boundaries.
         */
        private DaySegment(Activity activity, int startMinute, int endMinute, boolean overlaps, boolean multiDay) {
            this.activity = activity;
            this.startMinute = startMinute;
            this.endMinute = endMinute;
            this.overlaps = overlaps;
            this.multiDay = multiDay;
        }
    }
}
