/*
 * Copyright 2019 Red Hat
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

import io.apicurio.registry.storage.impl.InMemoryRegistryStorage;
import io.apicurio.registry.types.Current;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;

/**
 * @author Ales Justin
 */
@ApplicationScoped
public class RegistryStorageProducer {
    private static Logger log = LoggerFactory.getLogger(RegistryStorageProducer.class);

    @Inject
    Instance<RegistryStorage> storages;

    @Produces
    @ApplicationScoped
    @Current
    public RegistryStorage realImpl() {
        List<RegistryStorage> list = storages.stream().collect(Collectors.toList());
        RegistryStorage impl = null;
        if (list.size() == 1) {
            impl = list.get(0);
        } else {
            for (RegistryStorage rs : list) {
                if (rs instanceof InMemoryRegistryStorage == false) {
                    impl = rs;
                    break;
                }
            }
        }
        if (impl != null) {
            log.info(String.format("Using RegistryStore: %s", impl.getClass().getName()));
            return impl;
        }
        throw new IllegalStateException("Should not be here ... ?!");
    }
}
