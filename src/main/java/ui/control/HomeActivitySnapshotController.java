package ui.control;

import activity.Activity;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import trip.Trip;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * UI helper that builds the home-page snapshot of ongoing and upcoming activities.
 *
 * <p>This controller reads {@link Trip} data prepared by the main window and renders summary
 * cards into the provided JavaFX containers.</p>
 */
public class HomeActivitySnapshotController {
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");
    private static final Comparator<ActivitySummaryEntry> SNAPSHOT_ACTIVITY_COMPARATOR = Comparator
        .comparingInt((ActivitySummaryEntry entry) -> entry.activity.getPriority()).reversed()
        .thenComparing(entry -> entry.activity.getStartDateTime())
        .thenComparing(entry -> entry.activity.getEndDateTime());

    private final VBox ongoingActivityContainer;
    private final VBox upcomingActivityContainer;

    /**
     * Creates a snapshot UI coordinator bound to the two target containers.
     *
     * @param ongoingActivityContainer container for ongoing activity cards
     * @param upcomingActivityContainer container for upcoming activity cards
     */
    public HomeActivitySnapshotController(VBox ongoingActivityContainer, VBox upcomingActivityContainer) {
        this.ongoingActivityContainer = ongoingActivityContainer;
        this.upcomingActivityContainer = upcomingActivityContainer;
    }

    /**
     * Recomputes and redraws ongoing/upcoming activity snapshot cards.
     *
     * @param trips all trips to summarize
     */
    public void refresh(List<Trip> trips) {
        if (ongoingActivityContainer == null || upcomingActivityContainer == null) {
            return;
        }

        ongoingActivityContainer.getChildren().clear();
        upcomingActivityContainer.getChildren().clear();

        LocalDateTime now = LocalDateTime.now();
        List<ActivitySummaryEntry> activityEntries = new ArrayList<>();
        for (Trip trip : trips) {
            for (Activity activity : trip.getActivities()) {
                activityEntries.add(new ActivitySummaryEntry(activity, trip.getName()));
            }
        }

        List<ActivitySummaryEntry> ongoing = activityEntries.stream()
            .filter(entry -> !entry.activity.getStartDateTime().isAfter(now)
                && entry.activity.getEndDateTime().isAfter(now))
            .sorted(SNAPSHOT_ACTIVITY_COMPARATOR)
            .toList();

        List<ActivitySummaryEntry> upcoming = activityEntries.stream()
            .filter(entry -> entry.activity.getStartDateTime().isAfter(now))
            .sorted(SNAPSHOT_ACTIVITY_COMPARATOR)
            .toList();

        if (ongoing.isEmpty()) {
            ongoingActivityContainer.getChildren().add(createSnapshotEmptyCard("No ongoing activities"));
        } else {
            for (ActivitySummaryEntry entry : ongoing) {
                ongoingActivityContainer.getChildren().add(createSnapshotCard(entry, true));
            }
        }

        if (upcoming.isEmpty()) {
            upcomingActivityContainer.getChildren().add(createSnapshotEmptyCard("No upcoming activities"));
        } else {
            for (ActivitySummaryEntry entry : upcoming) {
                upcomingActivityContainer.getChildren().add(createSnapshotCard(entry, false));
            }
        }
    }

    private VBox createSnapshotCard(ActivitySummaryEntry entry, boolean isOngoing) {
        VBox card = new VBox(4);
        card.getStyleClass().add("snapshot-item");

        Label activityName = new Label(entry.activity.getName());
        activityName.getStyleClass().add("snapshot-name");
        activityName.setWrapText(true);

        String timeText = isOngoing
            ? "Until " + entry.activity.getEndDateTime().format(DATE_TIME_FORMAT)
            : entry.activity.getStartDateTime().format(DATE_TIME_FORMAT);
        Label timeLabel = new Label(timeText);
        timeLabel.getStyleClass().add("snapshot-meta");

        HBox tagRow = new HBox(6);
        Label tripTag = createSnapshotTag("Trip: " + entry.tripName);
        String typeText = entry.activity.getTypes().isEmpty() ? "Type: OTHER"
            : "Type: " + entry.activity.getTypes().get(0).name();
        Label typeTag = createSnapshotTag(typeText);
        tagRow.getChildren().addAll(tripTag, typeTag);

        card.getChildren().addAll(activityName, timeLabel, tagRow);
        return card;
    }

    private Label createSnapshotTag(String text) {
        Label tag = new Label(text);
        tag.getStyleClass().add("snapshot-tag");
        return tag;
    }

    private VBox createSnapshotEmptyCard(String text) {
        VBox card = new VBox();
        card.getStyleClass().addAll("snapshot-item", "snapshot-empty");
        Label label = new Label(text);
        label.getStyleClass().add("snapshot-meta");
        card.getChildren().add(label);
        return card;
    }

    /**
     * Lightweight view model for one activity snapshot row.
     */
    private static class ActivitySummaryEntry {
        private final Activity activity;
        private final String tripName;

        /**
         * Creates a lightweight snapshot entry for home-page rendering.
         *
         * @param activity activity shown in the snapshot card
         * @param tripName parent trip name shown as metadata
         */
        private ActivitySummaryEntry(Activity activity, String tripName) {
            this.activity = activity;
            this.tripName = tripName;
        }
    }
}
