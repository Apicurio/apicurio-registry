package io.apicurio.registry.utils;


public class BooleanUtil {

    public static boolean toBoolean(Object parameter) {
        if (parameter == null) {
            return false;
        }
        if (parameter instanceof Boolean) {
            return (Boolean) parameter;
        }
        if (parameter instanceof String) {
            return Boolean.parseBoolean((String) parameter);
        }
        return false;
    }

}
