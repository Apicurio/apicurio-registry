package io.apicurio.registry.util;

import java.io.InputStream;
import java.util.Properties;

public class PropertiesLoader {

    private static Properties properties = new Properties();

    static {
        loadProperties("application.properties");
    }

    /**
     * Loads properties file from the classpath.
     */
    private static void loadProperties(final String fileName) {
        try (InputStream inputStream = PropertiesLoader.class.getClassLoader().getResourceAsStream(fileName)) {
            properties.load(inputStream);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Gets a named property.
     *
     * @param key
     */
    public String get(String key) {
        return properties.getProperty(key);
    }
}
