package io.apicurio.registry.utils.streams.distore;

import java.util.Map;

/**
 * Filter predicate.
 *
 * @author Ales Justin
 */
public interface FilterPredicate<K, V> {
    /**
     * Predicate like for filtering keyvalue store.
     *
     * @param filtersMap map containing all the filters to be applied
     * @param key    the key
     * @param value  the value
     * @return true of false
     */
    boolean test(Map<String, String> filtersMap, K key, V value);
}
