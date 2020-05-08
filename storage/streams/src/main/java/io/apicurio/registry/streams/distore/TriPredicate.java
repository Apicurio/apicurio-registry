package io.apicurio.registry.streams.distore;

/**
 * Three predicate.
 *
 * @author Ales Justin
 */
public interface TriPredicate<T, U, V> {
    /**
     * Performs this operation on the given arguments.
     *
     * @param t the first input argument
     * @param u the second input argument
     * @param v the third input argument
     * @return true of false
     */
    boolean test(T t, U u, V v);
}
