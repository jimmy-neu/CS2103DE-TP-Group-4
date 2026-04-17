package exceptions;

/**
 * Signals that an expense cannot be resolved from the current data set.
 *
 * <p>This exception is raised by expense-aware aggregates such as trips and activities, then
 * handled by higher-level services or UI actions.</p>
 */
public class ExpenseNotFoundException extends Exception {
    /**
     * Creates an exception describing a missing expense lookup.
     *
     * @param message human-readable failure detail
     */
    public ExpenseNotFoundException(String message) {
        super(message);
    }
}
