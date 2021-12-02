/*
 * Copyright 2021 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.tests.utils;

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.LoadBalanceRegistryClient;
import io.apicurio.tests.LoadBalanceRegistryClient.RegistryClientHolder;

/**
 * @author Fabian Martinez
 */
public class RegistryWaitUtils {

    @FunctionalInterface
    public interface ConsumerExc<T> {
        void run(T t) throws Exception;
    }

    @FunctionalInterface
    public interface FunctionExc<T, R> {
        R run(T t) throws Exception;
    }

    public static void retry(RegistryClient registryClient, ConsumerExc<RegistryClient> registryOp) throws Exception {
        if (registryClient instanceof LoadBalanceRegistryClient) {
            LoadBalanceRegistryClient loadBalanceRegistryClient = (LoadBalanceRegistryClient) registryClient;

            var nodes = loadBalanceRegistryClient.getRegistryNodes();

            TestUtils.retry(() -> {
                for (RegistryClientHolder target : nodes) {
                    registryOp.run(target.client);
                }
            });
        } else {
            TestUtils.retry(() -> registryOp.run(registryClient));
        }
    }

}
