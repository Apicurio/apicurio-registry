package io.apicurio.registry.serde.nats.client.config;


import io.apicurio.registry.serde.Configurable;

import java.io.Closeable;
import java.util.Map;

public class Utils {


    private static ClassLoader getClassLoader() {
        var cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = Utils.class.getClassLoader();
        }
        return cl;
    }


    private static <T> Class<? extends T> loadClass(String klass, Class<T> superType) {
        try {
            return Class.forName(klass, true, getClassLoader()).asSubclass(superType);
        } catch (ClassNotFoundException ex) {
            throw new RuntimeException(ex);
        }
    }


    public static <T> T newInstance(Class<T> klass) {
        if (klass == null)
            throw new RuntimeException("class cannot be null");
        try {
            return klass.getDeclaredConstructor().newInstance();
        } catch (Exception ex) {
            throw new RuntimeException("Could not instantiate class " + klass.getCanonicalName(), ex);
        }
    }


    public static <T> T newConfiguredInstance(Object klass, Class<T> superType, Map<String, Object> config) {
        if (klass == null) {
            return null;
        }
        Object instance;
        if (klass instanceof String) {
            instance = newInstance(loadClass((String) klass, superType));
        } else if (klass instanceof Class<?>) {
            instance = newInstance((Class<?>) klass);
        } else {
            throw new RuntimeException("Unexpected element of type " + klass.getClass().getCanonicalName() + ", expected String or Class");
        }
        try {
            if (!superType.isInstance(instance)) {
                throw new RuntimeException(klass.getClass().getCanonicalName() + " is not an instance of " + superType.getCanonicalName());
            }
            if (instance instanceof Configurable) {
                ((Configurable) instance).configure(config);
            }
        } catch (Exception ex) {
            if (instance instanceof Closeable) {
                try {
                    ((Closeable) instance).close();
                } catch (Throwable t) {
                    // TODO log
                }
            }
            throw ex;
        }
        return superType.cast(instance);
    }
}
