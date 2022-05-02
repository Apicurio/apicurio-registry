package io.apicurio.registry.systemtest.framework;

public final class Environment {
    public static final String convertersUrl = System.getenv().get(Constants.CONVERTERS_URL_ENV_VAR);

    public static final String convertersSha512sum = System.getenv().get(Constants.CONVERTERS_SHA512SUM_ENV_VAR);
}
