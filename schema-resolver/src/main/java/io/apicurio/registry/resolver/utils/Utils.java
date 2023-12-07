package io.apicurio.registry.resolver.utils;

import java.util.function.Consumer;

public class Utils {

    @SuppressWarnings("unchecked")
    public static <T> Class<T> loadClass(String javaType) {
        try {
            return (Class<T>) Thread.currentThread().getContextClassLoader().loadClass(javaType);
        } catch (ClassNotFoundException ignored) {
        }
        try {
            return (Class<T>) Class.forName(javaType);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    //TODO make the instantiation mechanism configurable
    @SuppressWarnings("unchecked")
    public static <V> void instantiate(Class<V> type, Object value, Consumer<V> setter) {
        if (value != null) {
            if (type.isInstance(value)) {
                setter.accept(type.cast(value));
            } else if (value instanceof Class && type.isAssignableFrom((Class<?>) value)) {
                //noinspection unchecked
                setter.accept(instantiate((Class<V>) value));
            } else if (value instanceof String) {
                Class<V> clazz = loadClass((String) value);
                setter.accept(instantiate(clazz));
            } else {
                throw new IllegalArgumentException(String.format("Cannot handle configuration [%s]: %s", type.getName(), value));
            }
        }
    }

    //TODO make the instantiation mechanism configurable
    public static <V> V instantiate(Class<V> clazz) {
        try {
            return clazz.getConstructor().newInstance();
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

}
