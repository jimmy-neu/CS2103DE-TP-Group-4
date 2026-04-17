package activity;
import exceptions.ExpenseNotFoundException;
import expense.Expense;
import expense.ExpenseManagable;
import expense.Expense.Currency;
import location.Location;
import temporal.TimeInterval;
import utilities.BaseEntity;
import utilities.Copyable;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Domain model for a scheduled activity that belongs to a trip itinerary.
 *
 * <p>An activity composes {@link Expense} items, may reference a {@link Location}, and is
 * managed as part of a {@link trip.Trip} through time-interval and expense contracts.</p>
 */
public class Activity extends BaseEntity implements TimeInterval, ExpenseManagable, Copyable<Activity> {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    /**
     * Enumerates supported activity categories.
     */
    public enum Type {
        SIGHTSEEING,
        ADVENTURE,
        RELAXATION,
        CULTURAL,
        OTHER
    }

    private final List<Type> types = new ArrayList<>();

    private final List<Expense> expenses = new ArrayList<>();

    private Location location;

    private LocalDateTime startDateTime;

    private LocalDateTime endDateTime;

    /**
     * Creates an activity without an explicit location.
     *
     * @param id activity identifier
     * @param name activity display name
     * @param startDateTime activity start time
     * @param endDateTime activity end time
     */
    public Activity(int id, String name, LocalDateTime startDateTime, LocalDateTime endDateTime) {
        this(id, name, startDateTime, endDateTime, null);
    }

    /**
     * Creates an activity with all primary fields.
     *
     * @param id activity identifier
     * @param name activity display name
     * @param startDateTime activity start time
     * @param endDateTime activity end time
     * @param location activity location, or {@code null}
     */
    public Activity(int id, String name, LocalDateTime startDateTime, LocalDateTime endDateTime, Location location) {
        super(id, name);
        setStartDateTime(startDateTime);
        setEndDateTime(endDateTime);
        this.location = location;
    }

    /**
     * Returns activity categories.
     *
     * @return immutable activity-type list
     */
    public List<Type> getTypes() {
        return Collections.unmodifiableList(types);
    }

    /**
     * Replaces all activity categories.
     *
     * @param types replacement category list, or {@code null} to clear
     */
    public void setTypes(List<Type> types) {
        this.types.clear();
        if (types != null) {
            this.types.addAll(types);
        }
    }

    /**
     * Adds a category to this activity.
     *
     * @param type category to add
     */
    public void addType(Type type) {
        this.types.add(Objects.requireNonNull(type, "type"));
    }

    /**
     * Returns expenses directly attached to this activity.
     *
     * @return immutable expense list
     */
    public List<Expense> getExpenses() {
        return Collections.unmodifiableList(expenses);
    }

    /**
     * Replaces expenses directly attached to this activity.
     *
     * @param expenses replacement expenses, or {@code null} to clear
     */
    public void setExpenses(List<Expense> expenses) {
        this.expenses.clear();
        if (expenses != null) {
            for (Expense expense : expenses) {
                this.expenses.add(Objects.requireNonNull(expense, "expense"));
            }
        }
    }

    /**
     * Returns the assigned location.
     *
     * @return location, or {@code null}
     */
    public Location getLocation() {
        return location;
    }

    /**
     * Updates the assigned location.
     *
     * @param location location, or {@code null}
     */
    public void setLocation(Location location) {
        this.location = location;
    }

    /**
     * Returns the activity start timestamp.
     *
     * @return start timestamp
     */
    @Override
    public LocalDateTime getStartDateTime() {
        return startDateTime;
    }

    /**
     * Updates the activity start timestamp.
     *
     * @param startDateTime start timestamp
     */
    @Override
    public void setStartDateTime(LocalDateTime startDateTime) {
        Objects.requireNonNull(startDateTime, "startDateTime");
        if (endDateTime != null && startDateTime.isAfter(endDateTime)) {
            throw new IllegalArgumentException("startDateTime must not be after endDateTime");
        }
        this.startDateTime = startDateTime;
    }

    /**
     * Returns the activity end timestamp.
     *
     * @return end timestamp
     */
    @Override
    public LocalDateTime getEndDateTime() {
        return endDateTime;
    }

    /**
     * Updates the activity end timestamp.
     *
     * @param endDateTime end timestamp
     */
    @Override
    public void setEndDateTime(LocalDateTime endDateTime) {
        Objects.requireNonNull(endDateTime, "endDateTime");
        if (startDateTime != null && endDateTime.isBefore(startDateTime)) {
            throw new IllegalArgumentException("endDateTime must not be before startDateTime");
        }
        this.endDateTime = endDateTime;
    }

    /**
     * Adds an expense to this activity.
     *
     * @param expense expense to add
     */
    @Override
    public void addExpense(Expense expense) {
        expenses.add(Objects.requireNonNull(expense, "expense"));
    }

    /**
     * Deletes an expense by identifier.
     *
     * @param id expense id
     * @throws ExpenseNotFoundException if no matching expense exists
     */
    @Override
    public void deleteExpenseById(int id) throws ExpenseNotFoundException {
        Expense expense = findExpenseById(id);
        expenses.remove(expense);
    }

    /**
     * Deletes an expense by name.
     *
     * @param name expense name
     * @throws ExpenseNotFoundException if no matching expense exists
     */
    @Override
    public void deleteExpenseByName(String name) throws ExpenseNotFoundException {
        Expense expense = findExpenseByName(name);
        expenses.remove(expense);
    }

    /**
     * Returns an expense by identifier.
     *
     * @param id expense id
     * @return matching expense
     * @throws ExpenseNotFoundException if no matching expense exists
     */
    @Override
    public Expense getExpenseById(int id) throws ExpenseNotFoundException {
        return findExpenseById(id);
    }

    /**
     * Returns an expense by name.
     *
     * @param name expense name
     * @return matching expense
     * @throws ExpenseNotFoundException if no matching expense exists
     */
    @Override
    public Expense getExpenseByName(String name) throws ExpenseNotFoundException {
        return findExpenseByName(name);
    }

    /**
     * Returns total expense amount for one currency.
     *
     * @param currency currency to aggregate
     * @return total expense amount in the given currency
     */
    @Override
    public float getTotalCost(Expense.Currency currency) {
        Objects.requireNonNull(currency, "currency");
        float total = 0f;
        for (Expense expense : expenses) {
            if (expense.getCurrency() == currency) {
                total += expense.getCost();
            }
        }
        return total;
    }

    /**
     * Creates a deep copy of this activity and its direct expenses.
     *
     * @return copied activity
     */
    @Override
    public Activity copy() {
        Activity copy = new Activity(getId(), getName(), startDateTime, endDateTime, location);
        copy.setDescription(getDescription());
        copy.setPriority(getPriority());
        copy.setImage(getImage());
        copy.setTypes(types);
        for (Expense expense : expenses) {
            copy.addExpense(expense.copy());
        }
        return copy;
    }

    private Expense findExpenseById(int id) throws ExpenseNotFoundException {
        for (Expense expense : expenses) {
            if (expense.getId() == id) {
                return expense;
            }
        }
        throw new ExpenseNotFoundException("Expense not found: id=" + id);
    }

    private Expense findExpenseByName(String name) throws ExpenseNotFoundException {
        for (Expense expense : expenses) {
            if (Objects.equals(expense.getName(), name)) {
                return expense;
            }
        }
        throw new ExpenseNotFoundException("Expense not found: name=" + name);
    }

    /**
     * Returns a string representation of this object.
     */
    @Override
    public String toString() {
        StringJoiner typeJoiner = new StringJoiner(", ");
        for (Type type : types) {
            typeJoiner.add(type.name());
        }
        String typesText = typeJoiner.length() == 0 ? "None" : typeJoiner.toString();
        return "Activity #" + getId() + ": " + getName()
                + " | " + formatDateTime(getStartDateTime()) + " -> " + formatDateTime(getEndDateTime())
                + " | Location: " + (getLocation() != null ? getLocation() : "No location")
                + " | Types: " + typesText;
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DATE_TIME_FORMAT) : "?";
    }
}
