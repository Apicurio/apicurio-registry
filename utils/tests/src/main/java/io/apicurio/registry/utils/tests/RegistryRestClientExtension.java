package io.apicurio.registry.utils.tests;

import java.lang.reflect.Type;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import io.apicurio.registry.client.RegistryRestClient;

/**
 * @author famartin
 */
public class RegistryRestClientExtension implements ParameterResolver {

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        Type type = parameterContext.getParameter().getParameterizedType();
        if (type instanceof Class) {
            if (type == RegistryRestClient.class) {
                return true;
            } else if (RegistryRestClient.class.isAssignableFrom((Class)type)){
                return true;
            }
        }

        return false;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return new LazyRegistryRestClient(TestUtils.getRegistryApiUrl());
    }

}
