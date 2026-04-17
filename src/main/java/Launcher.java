import javafx.application.Application;

/**
 * Application entry point that starts the JavaFX runtime for the trip planner.
 *
 * <p>This launcher delegates startup to {@link Main} and does not hold domain or UI state.</p>
 */
public class Launcher {
    /**
     * Starts the JavaFX application runtime.
     *
     * @param args command-line arguments passed to the launcher
     */
    public static void main(String[] args) {
        Application.launch(Main.class, args);
    }
}
