package ui.control;

import country.Country;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.StringConverter;

import java.io.File;
import java.time.LocalTime;

/**
 * Shared UI utility methods for dialog styling, parsing, and form helpers.
 *
 * <p>This stateless helper is used by main-window CRUD controllers to keep repeated JavaFX
 * dialog behavior centralized and consistent.</p>
 */
public final class MainWindowDialogSupport {
    /**
     * Utility class; prevents instantiation.
     */
    private MainWindowDialogSupport() {
    }

    /**
     * Applies shared stylesheet and style classes to a dialog.
     *
     * @param dialog dialog to style
     * @param styleClass optional style class for the dialog pane
     * @param cancelButtonStyleClass optional style class for cancel buttons
     */
    public static void applyDialogTheme(Dialog<?> dialog, String styleClass, String cancelButtonStyleClass) {
        String stylesheet = MainWindowDialogSupport.class.getResource("/view/theme.css").toExternalForm();
        if (!dialog.getDialogPane().getStylesheets().contains(stylesheet)) {
            dialog.getDialogPane().getStylesheets().add(stylesheet);
        }
        if (styleClass != null && !styleClass.isBlank() && !dialog.getDialogPane().getStyleClass().contains(styleClass)) {
            dialog.getDialogPane().getStyleClass().add(styleClass);
        }

        applyDialogActionStyles(dialog, cancelButtonStyleClass);
        dialog.getDialogPane().getButtonTypes().addListener((ListChangeListener<ButtonType>) change ->
            applyDialogActionStyles(dialog, cancelButtonStyleClass));
    }

    /**
     * Creates a styled add-action button.
     *
     * @param text button label
     * @return configured button
     */
    public static Button createAddButton(String text) {
        return createStyledButton(text, "add-button");
    }

    /**
     * Creates a styled edit-action button.
     *
     * @param text button label
     * @return configured button
     */
    public static Button createEditButton(String text) {
        return createStyledButton(text, "edit-button");
    }

    /**
     * Creates a styled delete-action button.
     *
     * @param text button label
     * @return configured button
     */
    public static Button createDeleteButton(String text) {
        return createStyledButton(text, "delete-button");
    }

    /**
     * Creates a responsive row containing a combo box with action buttons.
     *
     * @param combo primary combo box
     * @param addButton add-action button
     * @param editButton edit-action button
     * @param deleteButton delete-action button
     * @return configured row container
     */
    public static HBox createResponsiveActionRow(ComboBox<?> combo, Button addButton, Button editButton, Button deleteButton) {
        combo.setMaxWidth(Double.MAX_VALUE);
        combo.setPrefWidth(260);
        HBox.setHgrow(combo, Priority.ALWAYS);
        addButton.setMinWidth(84);
        editButton.setMinWidth(84);
        deleteButton.setMinWidth(84);
        return new HBox(8, combo, addButton, editButton, deleteButton);
    }

    /**
     * Opens an image chooser and writes the selected path to a text field.
     *
     * @param dialog owning dialog
     * @param targetField text field receiving selected path
     */
    public static void chooseImagePath(Dialog<?> dialog, TextField targetField) {
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

    /**
     * Creates a converter for rendering countries in combo boxes.
     *
     * @return country string converter
     */
    public static StringConverter<Country> createCountryConverter() {
        return new StringConverter<>() {
            /**
             * Returns a string representation of this object.
             */
            @Override
            public String toString(Country country) {
                return country == null ? "" : country.toString();
            }

            /**
             * Country parsing is not required for this UI converter.
             */
            @Override
            public Country fromString(String string) {
                return null;
            }
        };
    }

    /**
     * Parses a time string and falls back when parsing fails.
     *
     * @param text raw time text
     * @param fallback fallback value on parse failure
     * @return parsed time, or fallback on failure
     */
    public static LocalTime parseTimeOrDefault(String text, LocalTime fallback) {
        try {
            return LocalTime.parse(text);
        } catch (Exception e) {
            return fallback;
        }
    }

    /**
     * Parses an optional decimal value.
     *
     * @param text raw numeric text
     * @return parsed decimal value, or {@code null} for blank input
     */
    public static Double parseOptionalDouble(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        return Double.parseDouble(text.trim());
    }

    private static Button createStyledButton(String text, String styleClass) {
        Button button = new Button(text);
        if (!button.getStyleClass().contains(styleClass)) {
            button.getStyleClass().add(styleClass);
        }
        return button;
    }

    private static void applyDialogActionStyles(Dialog<?> dialog, String cancelButtonStyleClass) {
        for (ButtonType buttonType : dialog.getDialogPane().getButtonTypes()) {
            if (!buttonType.getButtonData().isCancelButton()) {
                continue;
            }
            Node node = dialog.getDialogPane().lookupButton(buttonType);
            if (node instanceof Button button && cancelButtonStyleClass != null && !cancelButtonStyleClass.isBlank()) {
                if (!button.getStyleClass().contains(cancelButtonStyleClass)) {
                    button.getStyleClass().add(cancelButtonStyleClass);
                }
            }
        }
    }
}
