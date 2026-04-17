package expense;
import java.util.Locale;
import java.util.Objects;

import utilities.BaseEntity;
import utilities.Copyable;

/**
 * Domain model for a single monetary expense entry.
 *
 * <p>Expenses are attached to {@link trip.Trip} and {@link activity.Activity} objects and are
 * persisted through the expense repository and storage components.</p>
 */
public class Expense extends BaseEntity implements Copyable<Expense> {

    /**
     * Enumerates supported expense categories.
     */
    public enum Type {
        FOOD,
        ACCOMMODATION,
        TRANSPORTATION,
        ENTERTAINMENT,
        OTHER
    }

    /**
     * Enumerates supported currencies for expense values.
     */
    public enum Currency {
        SGD,
        USD,
        EUR,
        GBP,
        JPY,
        CNY
    }

    private float cost;

    private Currency currency;

    private Type type;

    private String imagePath;

    /**
     * Creates an expense without an image path.
     *
     * @param id expense identifier
     * @param name expense display name
     * @param cost expense amount (non-negative)
     * @param currency expense currency
     * @param type expense category
     */
    public Expense(int id, String name, float cost, Currency currency, Type type) {
        this(id, name, cost, currency, type, null);
    }

    /**
     * Creates an expense with all persisted fields.
     *
     * @param id expense identifier
     * @param name expense display name
     * @param cost expense amount (non-negative)
     * @param currency expense currency
     * @param type expense category
     * @param imagePath image path, or {@code null}
     */
    public Expense(int id, String name, float cost, Currency currency, Type type, String imagePath) {
        super(id, name);
        setCost(cost);
        setCurrency(currency);
        setType(type);
        setImagePath(imagePath);
    }

    /**
     * Returns the expense amount.
     *
     * @return expense amount
     */
    public float getCost() {
        return cost;
    }

    /**
     * Updates the expense amount.
     *
     * @param cost expense amount (must be non-negative)
     */
    public void setCost(float cost) {
        if (cost < 0) {
            throw new IllegalArgumentException("cost must be non-negative");
        }
        this.cost = cost;
    }

    /**
     * Returns the expense currency.
     *
     * @return expense currency
     */
    public Currency getCurrency() {
        return currency;
    }

    /**
     * Updates the expense currency.
     *
     * @param currency expense currency
     */
    public void setCurrency(Currency currency) {
        this.currency = Objects.requireNonNull(currency, "currency");
    }

    /**
     * Returns the expense category.
     *
     * @return expense category
     */
    public Type getType() {
        return type;
    }

    /**
     * Updates the expense category.
     *
     * @param type expense category
     */
    public void setType(Type type) {
        this.type = Objects.requireNonNull(type, "type");
    }

    /**
     * Returns the normalized image path.
     *
     * @return image path, or {@code null}
     */
    public String getImagePath() {
        return imagePath;
    }

    /**
     * Updates the normalized image path.
     *
     * @param imagePath image path, or {@code null}
     */
    public void setImagePath(String imagePath) {
        if (imagePath == null) {
            this.imagePath = null;
            return;
        }
        String trimmed = imagePath.trim();
        this.imagePath = trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Creates a value copy of this expense.
     *
     * @return copied expense instance
     */
    @Override
    public Expense copy() {
        Expense copy = new Expense(getId(), getName(), cost, currency, type, imagePath);
        copy.setDescription(getDescription());
        copy.setPriority(getPriority());
        copy.setImage(getImage());
        return copy;
    }

    /**
     * Returns a string representation of this object.
     */
    @Override
    public String toString() {
        return "Expense #" + getId() + ": " + getName()
                + " | " + String.format(Locale.US, "%.2f", getCost()) + " " + getCurrency()
                + " | Type: " + getType();
    }
}
