package exceptions;

/**
 * Signals that an activity cannot be resolved from the current data set.
 *
 * <p>This exception is raised by trip and activity management operations and propagated to UI
 * flows that perform activity lookup or deletion.</p>
 */
public class ActivityNotFoundException extends Exception {
    /**
     * Creates an exception describing a missing activity lookup.
     *
     * @param message human-readable failure detail
     */
    public ActivityNotFoundException(String message) {
        super(message);
    }
}
