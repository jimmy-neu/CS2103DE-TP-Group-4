import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

/**
 * JavaFX bootstrap class that loads the main application scene.
 *
 * <p>This class initializes {@code MainWindow.fxml} and shared styling, then hands control to
 * the JavaFX controllers declared in the loaded view.</p>
 */
public class Main extends Application {
    /**
     * Initializes and shows the primary application window.
     *
     * @param stage primary JavaFX stage provided by the runtime
     */
    @Override
    public void start(Stage stage) {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(Main.class.getResource("/view/MainWindow.fxml"));
            BorderPane root = fxmlLoader.load();
            Scene scene = new Scene(root);
            scene.getStylesheets().add(Main.class.getResource("/view/theme.css").toExternalForm());
            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
