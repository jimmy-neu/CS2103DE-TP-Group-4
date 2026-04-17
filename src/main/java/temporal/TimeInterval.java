package temporal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.Objects;

/**
 * Represents an entity with a bounded time interval.
 *
 * <p>Implementations provide start/end timestamps while default methods expose shared overlap
 * and duration calculations.</p>
 */
public interface TimeInterval {

    /**
     * Returns the interval start timestamp.
     *
     * @return start timestamp
     */
    LocalDateTime getStartDateTime();

    /**
     * Updates the interval start timestamp.
     *
     * @param startDateTime start timestamp
     */
    void setStartDateTime(LocalDateTime startDateTime);

    /**
     * Returns the interval end timestamp.
     *
     * @return end timestamp
     */
    LocalDateTime getEndDateTime();

    /**
     * Updates the interval end timestamp.
     *
     * @param endDateTime end timestamp
     */
    void setEndDateTime(LocalDateTime endDateTime);

    /**
     * Checks whether this interval overlaps another interval.
     *
     * @param other interval to compare against
     * @return {@code true} when the intervals overlap in time
     */
    default Boolean overlapsWith(TimeInterval other) {
        Objects.requireNonNull(other, "other");
        LocalDateTime start = Objects.requireNonNull(getStartDateTime(), "startDateTime");
        LocalDateTime end = Objects.requireNonNull(getEndDateTime(), "endDateTime");
        LocalDateTime otherStart = Objects.requireNonNull(other.getStartDateTime(), "other.startDateTime");
        LocalDateTime otherEnd = Objects.requireNonNull(other.getEndDateTime(), "other.endDateTime");
        return start.isBefore(otherEnd) && end.isAfter(otherStart);
    }

    /**
     * Returns the elapsed duration between start and end.
     *
     * @return interval duration
     */
    default Duration getDuration() {
        return Duration.between(
                Objects.requireNonNull(getStartDateTime(), "startDateTime"),
                Objects.requireNonNull(getEndDateTime(), "endDateTime"));
    }

    /**
     * Returns the elapsed calendar period between start and end dates.
     *
     * @return calendar period between start and end dates
     */
    default Period getPeriod() {
        LocalDateTime start = Objects.requireNonNull(getStartDateTime(), "startDateTime");
        LocalDateTime end = Objects.requireNonNull(getEndDateTime(), "endDateTime");
        return Period.between(start.toLocalDate(), end.toLocalDate());
    }

}
