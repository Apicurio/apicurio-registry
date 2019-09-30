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

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import java.util.Optional;

@ApplicationScoped
public class JPADatabaseManager {

    private static Logger log = LoggerFactory.getLogger(JPADatabaseManager.class);

    @ConfigProperty(name = "quarkus.datasource.url")
    Optional<String> dsUrl;

    @ConfigProperty(name = "quarkus.datasource.username")
    Optional<String> dsUser;

    @ConfigProperty(name = "quarkus.datasource.password")
    Optional<String> dsPassword;

    void onStart(@Observes StartupEvent event) {

        log.info("JPA storage is starting...");

        dsUrl.ifPresent(x -> log.debug("Datasource URL: " + x));
        dsUrl.orElseThrow(() -> new IllegalStateException("Datasource URL missing!"));

        if (!dsUser.isPresent()) {
            log.warn("Datasource username is missing.");
        }
        if (!dsPassword.isPresent()) {
            log.warn("Datasource password is missing.");
        }
    }

    void onStop(@Observes ShutdownEvent event) {

        log.info("JPA storage is stopping...");
    }
}
