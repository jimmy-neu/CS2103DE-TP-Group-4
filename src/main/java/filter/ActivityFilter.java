package filter;

import activity.Activity;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Utility class providing stateless filtering operations for {@link Activity} collections.
 *
 * <p>This helper is used by presentation logic such as trip-page filtering so controllers can
 * reuse tested query logic without owning filtering state.</p>
 */
public final class ActivityFilter {

    /**
     * Prevents instantiation because all methods are static utilities.
     */
    private ActivityFilter() {
    }

    /**
     * Returns only the activities whose {@code types} list contains the given type.
     *
     * @param activities the full list of activities (must not be null)
     * @param type       the type to match; if null, all activities are returned
     * @return an unmodifiable filtered list
     */
    public static List<Activity> byType(List<Activity> activities, Activity.Type type) {
        Objects.requireNonNull(activities, "activities");
        if (type == null) {
            return Collections.unmodifiableList(activities);
        }
        return activities.stream()
                .filter(a -> a.getTypes().contains(type))
                .collect(Collectors.toUnmodifiableList());
    }
}