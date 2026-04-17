package utilities;
import java.awt.image.BufferedImage;
import java.util.Objects;

/**
 * Base class for all entities with shared identity and descriptive fields.
 */
public abstract class BaseEntity {

    private int id;

    private String name;

    private String description;

    private int priority;

    // BufferedImage should remain runtime-only and never be serialized to JSON.
    private transient BufferedImage image;

    /**
     * Creates a base entity with default id and name values.
     */
    protected BaseEntity() {
        this(0, "Unnamed");
    }

    /**
     * Creates a base entity with the provided identity values.
     * @param id non-negative entity id.
     * @param name non-blank display name.
     */
    protected BaseEntity(int id, String name) {
        setId(id);
        setName(name);
        setPriority(0);
    }

    /**
     * Returns the stable entity identifier.
     *
     * @return non-negative entity id
     */
    public int getId() {
        return id;
    }

    /**
     * Sets the stable entity identifier.
     *
     * @param id non-negative entity id
     */
    public void setId(int id) {
        if (id < 0) {
            throw new IllegalArgumentException("id must be non-negative");
        }
        this.id = id;
    }

    /**
     * Returns the display name.
     *
     * @return non-blank entity name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the display name.
     *
     * @param name non-blank entity name
     */
    public void setName(String name) {
        String trimmed = Objects.requireNonNull(name, "name").trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        this.name = trimmed;
    }

    /**
     * Returns the optional free-text description.
     *
     * @return description text, or {@code null}
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the optional free-text description.
     *
     * @param description description text, or {@code null}
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Returns the priority score used for sorting and display.
     *
     * @return non-negative priority score
     */
    public int getPriority() {
        return priority;
    }

    /**
     * Sets the priority score used for sorting and display.
     *
     * @param priority non-negative priority score
     */
    public void setPriority(int priority) {
        if (priority < 0) {
            throw new IllegalArgumentException("priority must be non-negative");
        }
        this.priority = priority;
    }

    /**
     * Returns the in-memory image object.
     *
     * @return runtime image value, or {@code null}
     */
    public BufferedImage getImage() {
        return image;
    }

    /**
     * Sets the in-memory image object.
     *
     * @param image runtime image value, or {@code null}
     */
    public void setImage(BufferedImage image) {
        this.image = image;
    }

}
