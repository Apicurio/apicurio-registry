package io.apicurio.registry.utils;

public final class DtoUtil {

    public static String registryAuthPropertyToApp(String propertyName) {
        return propertyName.replace("apicurio.auth.", "app.authn.");
    }

    public static String appAuthPropertyToRegistry(String propertyName) {
        return propertyName.replace("app.authn.", "apicurio.auth.");
    }

}
