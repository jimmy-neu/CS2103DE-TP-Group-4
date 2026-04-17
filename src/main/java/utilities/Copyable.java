package utilities;
/**
 * Defines a contract for creating logical copies of objects.
 *
 * @param <T> concrete copy type
 */
public interface Copyable<T> {

    /**
     * Creates a copy of this object.
     *
     * @return a copied instance
     */
    T copy();

}
