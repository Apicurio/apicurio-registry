/*
 * Copyright 2020 Red Hat Inc
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

package io.apicurio.registry.storage.impl.sql;

import io.apicurio.common.apps.logging.Logged;
import io.apicurio.registry.metrics.StorageMetricsApply;
import io.apicurio.registry.metrics.health.liveness.PersistenceExceptionLivenessApply;
import io.apicurio.registry.metrics.health.readiness.PersistenceTimeoutReadinessApply;
import io.apicurio.registry.storage.RegistryStorage;
import jakarta.annotation.PostConstruct;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * A MySQL Server implementation of the {@link RegistryStorage} interface.
 *
 * @author bruno.ariev@gmail.com
 */
@ApplicationScoped
@PersistenceExceptionLivenessApply
@PersistenceTimeoutReadinessApply
@StorageMetricsApply
@Logged
public class MySqlRegistryStorage extends AbstractSqlRegistryStorage {

    public MySqlRegistryStorage() {
        super(true);
    }

    @PostConstruct
    void onConstruct() {
        log.info("Using MySQL registry storage.");
    }

}
