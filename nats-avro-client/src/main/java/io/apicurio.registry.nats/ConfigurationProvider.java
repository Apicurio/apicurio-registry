package io.apicurio.registry.nats;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ConfigurationProvider {

    private static InputStream inputStream ;

    private static Properties properties ;

    static {
        try {
            inputStream = ConfigurationProvider.class.getClassLoader().getResourceAsStream("connection.properties");
            if (inputStream != null) {
                properties = new Properties();
                properties.load(inputStream);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Properties getProperties(){
        return properties;
    }

    public static String getString(String key){
        return properties.getProperty(key);
    }

    public static boolean getBoolean(String key){
        return Boolean.parseBoolean(getString(key));
    }

    public static int getInt(String key){
        return Integer.parseInt(getString(key));
    }

    public static long getLong(String key){
        return Long.parseLong(getString(key));
    }
}
