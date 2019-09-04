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

package io.apicurio.registry.storage.impl.jpa;

import java.util.Optional;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.apicurio.registry.storage.RegistryStorageException;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;

@ApplicationScoped
public class JPADatabaseManager {

    private static Logger log = LoggerFactory.getLogger(JPADatabaseManager.class);

    @ConfigProperty(name = "registry.storage.type")
    Optional<String> storageType;

    void onStart(@Observes StartupEvent event) {

        log.info("JDBC Database Manager is starting...");

        if (!storageType.isPresent()) {
            throw new RegistryStorageException("Could not initialize data storage. " +
                    "Configuration property 'registry.storage.type' not found.");
        }

        log.info("JDBC storage type: " + storageType.get());
    }

    void onStop(@Observes ShutdownEvent event) {
        log.info("JDBC Database Manager is stopping...");
    }
}
