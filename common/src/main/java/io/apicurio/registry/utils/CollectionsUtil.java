package io.apicurio.registry.utils;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static java.lang.String.valueOf;

public final class CollectionsUtil {

    public static Map<String, String> toMap(Properties properties) {
        var map = new HashMap<String, String>();
        properties.forEach((k, v) -> map.put(valueOf(k), valueOf(v)));
        return map;
    }

    public static Properties toProperties(Map<String, String> map) {
        var properties = new Properties();
        map.forEach(properties::setProperty);
        return properties;
    }

    public static Map<String, String> copy(Map<String, String> map) {
        var copy = new HashMap<String, String>();
        copy.putAll(map);
        return copy;
    }

    public static boolean isEmpty(Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    private CollectionsUtil() {
    }
}
