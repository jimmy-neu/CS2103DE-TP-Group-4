package expense;

import storage.ExpenseStorage;
import storage.ImageAssetStore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Repository service for lifecycle management of {@link Expense} records.
 *
 * <p>This class coordinates ID tracking with {@link ExpenseStorage} persistence and
 * {@link ImageAssetStore} image handling, and is consumed by trip and activity UI flows.</p>
 */
public class ExpenseRepository {

    private static final Set<Integer> USED_EXPENSE_IDS = new HashSet<>();

    private final List<Expense> expenses = new ArrayList<>();
    private final Map<Integer, Expense> expensesById = new HashMap<>();
    private final ExpenseStorage storage;
    private final ImageAssetStore imageAssetStore;
    private int nextId = 1;

    /**
     * Creates a repository backed by default storage components.
     */
    public ExpenseRepository() {
        this(new ExpenseStorage(), new ImageAssetStore());
    }

    /**
     * Creates a repository with explicit storage dependencies.
     *
     * @param storage expense storage gateway
     * @param imageAssetStore image import/normalization helper
     */
    public ExpenseRepository(ExpenseStorage storage, ImageAssetStore imageAssetStore) {
        this.storage = storage;
        this.imageAssetStore = imageAssetStore;
    }

    /**
     * Loads expenses from storage and rebuilds in-memory identity indexes.
     *
     * @throws IOException if storage read or normalization persistence fails
     */
    public void load() throws IOException {
        expenses.clear();
        expensesById.clear();
        USED_EXPENSE_IDS.clear();
        expenses.addAll(storage.load());
        nextId = 1;

        boolean hasUpdates = false;
        for (Expense expense : expenses) {
            registerExpenseId(expense.getId());
            nextId = Math.max(nextId, expense.getId() + 1);
            String normalizedPath = imageAssetStore.normalizeImagePath(expense.getImagePath());
            if (normalizedPath != null && !normalizedPath.equals(expense.getImagePath())) {
                expense.setImagePath(normalizedPath);
                hasUpdates = true;
            }
            expensesById.put(expense.getId(), expense);
        }

        if (hasUpdates) {
            save();
        }
    }

    /**
     * Persists all current expenses to storage.
     *
     * @throws IOException if writing fails
     */
    public void save() throws IOException {
        storage.save(expenses);
    }

    /**
     * Returns an immutable snapshot of all expenses.
     *
     * @return all known expenses
     */
    public List<Expense> getExpenses() {
        return Collections.unmodifiableList(expenses);
    }

    /**
     * Finds an expense by identifier.
     *
     * @param id expense id
     * @return matching expense, or {@code null}
     */
    public Expense findById(int id) {
        return expensesById.get(id);
    }

    /**
     * Returns the next available expense id.
     *
     * @return next unused identifier
     */
    public int nextAvailableId() {
        while (USED_EXPENSE_IDS.contains(nextId)) {
            nextId++;
        }
        return nextId;
    }

    /**
     * Creates and registers a new expense.
     *
     * @param name required expense name
     * @param cost expense amount
     * @param currency expense currency
     * @param type expense category
     * @param imageSourcePath optional source image path to import
     * @return created expense
     */
    public Expense createExpense(String name, float cost, Expense.Currency currency, Expense.Type type,
                                 String imageSourcePath) {
        int id = nextAvailableId();
        String normalizedName = normalizeRequired(name, "expense name");
        String imagePath = imageAssetStore.importImage(imageSourcePath, "expense", normalizedName);
        Expense expense = new Expense(id, normalizedName, cost, currency, type, imagePath);
        registerExpense(expense);
        return expense;
    }

    /**
     * Updates an existing expense.
     *
     * @param expenseId target expense id
     * @param name required expense name
     * @param cost expense amount
     * @param currency expense currency
     * @param type expense category
     * @param imageSourcePath optional source image path to import
     * @return updated expense
     */
    public Expense updateExpense(int expenseId, String name, float cost, Expense.Currency currency, Expense.Type type,
                                 String imageSourcePath) {
        Expense expense = expensesById.get(expenseId);
        if (expense == null) {
            throw new IllegalArgumentException("Expense not found: id=" + expenseId);
        }

        String normalizedName = normalizeRequired(name, "expense name");
        expense.setName(normalizedName);
        expense.setCost(cost);
        expense.setCurrency(currency);
        expense.setType(type);

        if (imageSourcePath != null && !imageSourcePath.isBlank()) {
            String storedImagePath = imageAssetStore.importImage(imageSourcePath, "expense", normalizedName);
            if (storedImagePath == null) {
                storedImagePath = imageAssetStore.normalizeImagePath(imageSourcePath);
            }
            expense.setImagePath(storedImagePath);
        }
        return expense;
    }

    /**
     * Deletes an expense by identifier.
     *
     * @param expenseId expense id
     */
    public void deleteExpenseById(int expenseId) {
        Expense expense = expensesById.remove(expenseId);
        if (expense == null) {
            throw new IllegalArgumentException("Expense not found: id=" + expenseId);
        }
        expenses.remove(expense);
        USED_EXPENSE_IDS.remove(expenseId);
    }

    /**
     * Deletes an expense by exact name.
     *
     * @param name expense name
     */
    public void deleteExpenseByName(String name) {
        String normalizedName = normalizeRequired(name, "expense name");
        Expense target = null;
        for (Expense expense : expenses) {
            if (Objects.equals(expense.getName(), normalizedName)) {
                target = expense;
                break;
            }
        }
        if (target == null) {
            throw new IllegalArgumentException("Expense not found: name=" + normalizedName);
        }
        deleteExpenseById(target.getId());
    }

    /**
     * Registers an existing expense instance in this repository.
     *
     * @param expense expense to register
     */
    public void registerExpense(Expense expense) {
        registerExpenseId(expense.getId());
        expenses.add(expense);
        expensesById.put(expense.getId(), expense);
        nextId = Math.max(nextId, expense.getId() + 1);
    }

    private void registerExpenseId(int id) {
        if (!USED_EXPENSE_IDS.add(id)) {
            throw new IllegalStateException("Duplicate expense id detected: " + id);
        }
    }

    private String normalizeRequired(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}
