package io.apicurio.tests.utils;

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.utils.tests.TestUtils;

public class RegistryWaitUtils {

    @FunctionalInterface
    public interface ConsumerExc<T> {
        void run(T t) throws Exception;
    }

    @FunctionalInterface
    public interface FunctionExc<T, R> {
        R run(T t) throws Exception;
    }

    public static void retry(RegistryClient registryClient, ConsumerExc<RegistryClient> registryOp)
            throws Exception {
        TestUtils.retry(() -> registryOp.run(registryClient));
    }

}
