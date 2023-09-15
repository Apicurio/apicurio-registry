/*
 * Copyright 2020 Red Hat
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

package io.apicurio.registry.storage;

import io.apicurio.common.apps.config.DynamicConfigStorage;
import io.apicurio.registry.storage.decorator.RegistryStorageDecorator;
import io.apicurio.registry.storage.impl.sql.InMemoryRegistryStorage;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.types.Raw;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.slf4j.Logger;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Ales Justin
 * @author Jakub Senko <em>m@jsenko.net</em>
 */
@ApplicationScoped
public class RegistryStorageProducer {

    @Inject
    Logger log;

    @Inject
    Instance<InMemoryRegistryStorage> defaultStorage;

    @Inject
    Instance<RegistryStorageProvider> provider;

    @Inject
    Instance<RegistryStorageDecorator> decorators;

    private RegistryStorage cachedCurrent;

    private RegistryStorage cachedRaw;


    @Produces
    @ApplicationScoped
    @Current
    public RegistryStorage current() {
        if (cachedCurrent == null) {
            cachedCurrent = raw();

            Comparator<RegistryStorageDecorator> decoratorComparator = Comparator
                    .comparing(RegistryStorageDecorator::order);

            List<RegistryStorageDecorator> activeDecorators = decorators.stream()
                    .filter(RegistryStorageDecorator::isEnabled)
                    .sorted(decoratorComparator)
                    .collect(Collectors.toList());

            if (!activeDecorators.isEmpty()) {
                log.debug("Following RegistryStorage decorators have been enabled (in order): {}",
                        activeDecorators.stream().map(d -> d.getClass().getName()).collect(Collectors.toList()));

                for (int i = activeDecorators.size() - 1; i >= 0; i--) {
                    RegistryStorageDecorator decorator = activeDecorators.get(i);
                    decorator.setDelegate(cachedCurrent);
                    cachedCurrent = decorator;
                }
            } else {
                log.debug("No RegistryStorage decorator has been enabled");
            }
        }

        return cachedCurrent;
    }


    @Produces
    @ApplicationScoped
    @Raw
    public RegistryStorage raw() {
        if (cachedRaw == null) {
            if (provider.isResolvable()) {
                cachedRaw = provider.get().storage();
            } else if (defaultStorage.isResolvable()) {
                cachedRaw = defaultStorage.get();
            } else {
                throw new IllegalStateException("No (single) RegistryStorage available!");
            }
            log.info("Using the following RegistryStorage implementation: {}", cachedRaw.getClass().getName());
        }
        return cachedRaw;
    }


    @Produces
    @ApplicationScoped
    public DynamicConfigStorage configStorage() {
        return current();
    }
}
