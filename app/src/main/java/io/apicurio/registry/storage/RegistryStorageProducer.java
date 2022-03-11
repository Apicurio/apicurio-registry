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

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;

import org.slf4j.Logger;

import io.apicurio.common.apps.config.DynamicConfigStorage;
import io.apicurio.registry.storage.decorator.RegistryStorageDecorator;
import io.apicurio.registry.storage.impl.sql.InMemoryRegistryStorage;
import io.apicurio.registry.types.Current;

/**
 * @author Ales Justin
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

    private RegistryStorage cachedImpl;

    @Produces
    @ApplicationScoped
    @Current
    public RegistryStorage realImpl() {
        if (cachedImpl != null) {
            return cachedImpl;
        }

        if (provider.isResolvable()) {
            cachedImpl = provider.get().storage();
        } else {
            cachedImpl = defaultStorage.get();
        }

        if (cachedImpl != null) {
            log.info(String.format("Using RegistryStore: %s", cachedImpl.getClass().getName()));

            Comparator<RegistryStorageDecorator> decoratorsComparator = Comparator.comparing(RegistryStorageDecorator::order);

            List<RegistryStorageDecorator> declist = decorators.stream()
                    .filter(RegistryStorageDecorator::isEnabled)
                    .sorted(decoratorsComparator)
                    .collect(Collectors.toList());

            if (!declist.isEmpty()) {
                log.debug("RegistryStorage decorators");
                declist.forEach(d -> log.debug(d.getClass().getName()));
            }

            for (int i = declist.size() - 1 ; i >= 0; i--) {
                RegistryStorageDecorator decorator = declist.get(i);
                decorator.setDelegate(cachedImpl);
                cachedImpl = decorator;
            }

            return cachedImpl;
        }

        throw new IllegalStateException("No RegistryStorage available on the classpath!");
    }

    @Produces
    @ApplicationScoped
    public DynamicConfigStorage configStorage() {
        return realImpl();
    }

}
