package exceptions;

/**
 * Signals that a requested trip cannot be found.
 *
 * <p>This exception is primarily emitted by {@link trip.TripManager} lookup and deletion
 * operations and handled by higher-level application flows.</p>
 */
public class TripNotFoundException extends Exception {
    /**
     * Creates an exception describing a missing trip lookup.
     *
     * @param message human-readable failure detail
     */
    public TripNotFoundException(String message) {
        super(message);
    }
}
