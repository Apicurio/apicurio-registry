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

package io.apicurio.registry.metrics.health.liveness;

import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.types.Current;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Default;
import javax.inject.Inject;

/**
 * @author Ales Justin
 */
@ApplicationScoped
@Liveness
@Default
public class StorageLivenessCheck implements HealthCheck {

    @Inject
    @Current
    RegistryStorage storage;

    @Override
    public synchronized HealthCheckResponse call() {
        return HealthCheckResponse.builder()
                                  .name("StorageLivenessCheck")
                                  .status(storage.isAlive())
                                  .build();
    }
}
