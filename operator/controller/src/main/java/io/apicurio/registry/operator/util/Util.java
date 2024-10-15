package io.apicurio.registry.operator.util;

public class Util {

    private Util() {
    }

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
