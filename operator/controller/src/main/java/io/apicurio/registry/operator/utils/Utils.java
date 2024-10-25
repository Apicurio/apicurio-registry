package io.apicurio.registry.operator.utils;

public class Utils {

    private Utils() {
    }

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

}
