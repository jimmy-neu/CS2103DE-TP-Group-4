package expense;
import exceptions.ExpenseNotFoundException;

/**
 * Defines expense CRUD operations and total-cost aggregation behavior.
 */
public interface ExpenseManagable {

    /**
     * Adds an expense to the owning aggregate.
     *
     * @param expense expense to add (must not be null)
     */
    void addExpense(Expense expense);

    /**
     * Removes an expense by identifier.
     *
     * @param id expense id
     * @throws ExpenseNotFoundException if no matching expense exists
     */
    void deleteExpenseById(int id) throws ExpenseNotFoundException;

    /**
     * Removes an expense by name.
     *
     * @param name expense name
     * @throws ExpenseNotFoundException if no matching expense exists
     */
    void deleteExpenseByName(String name) throws ExpenseNotFoundException;

    /**
     * Returns an expense by identifier.
     *
     * @param id expense id
     * @return matching expense
     * @throws ExpenseNotFoundException if no matching expense exists
     */
    Expense getExpenseById(int id) throws ExpenseNotFoundException;

    /**
     * Returns an expense by name.
     *
     * @param name expense name
     * @return matching expense
     * @throws ExpenseNotFoundException if no matching expense exists
     */
    Expense getExpenseByName(String name) throws ExpenseNotFoundException;

    /**
     * Calculates the total cost for a single currency.
     *
     * @param currency currency to aggregate
     * @return total cost in the requested currency
     */
    float getTotalCost(Expense.Currency currency);
}
