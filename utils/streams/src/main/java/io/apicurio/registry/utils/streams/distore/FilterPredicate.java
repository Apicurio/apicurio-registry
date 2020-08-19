package io.apicurio.registry.utils.streams.distore;

/**
 * Filter predicate.
 *
 * @author Ales Justin
 */
public interface FilterPredicate<K, V> {
    /**
     * Predicate like for filtering keyvalue store.
     *
     * @param filter filter string
     * @param over   the over enum name
     * @param key    the key
     * @param value  the value
     * @return true of false
     */
    boolean test(String filter, String over, K key, V value);
}
