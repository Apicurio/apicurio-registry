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

package io.apicurio.registry.mt;

import java.util.Collections;
import java.util.Optional;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.DeploymentException;
import javax.inject.Inject;

import io.apicurio.multitenant.client.TenantManagerClient;
import io.apicurio.multitenant.client.TenantManagerClientImpl;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.utils.OptionalBean;
import io.apicurio.rest.client.JdkHttpClientProvider;
import io.apicurio.rest.client.auth.OidcAuth;
import io.quarkus.runtime.configuration.ProfileManager;

/**
 * @author Fabian Martinez
 * @author Jakub Senko <jsenko@redhat.com>
 */
@ApplicationScoped
public class TenantManagerClientProducer {

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    MultitenancyProperties properties;

    @Produces
    @ApplicationScoped
    OptionalBean<TenantManagerClient> produce() {

        if (properties.isMultitenancyEnabled()) {

            //we check if profile is prod, because some tests will fail to start quarkus app.
            //Those tests checks if multitenancy is supported and abort the test if needed
            if ("prod".equals(ProfileManager.getActiveProfile()) && !storage.supportsMultiTenancy()) {
                throw new DeploymentException("Unsupported configuration, \"registry.enable.multitenancy\" is enabled " +
                        "but the storage implementation being used (" + storage.storageName() + ") does not support multitenancy");
            }

            if (properties.getTenantManagerUrl().isEmpty()) {
                throw new DeploymentException("Unsupported configuration, \"registry.enable.multitenancy\" is enabled " +
                        "but the no \"registry.tenant.manager.url\" is provided");
            }

            if (properties.isTenantManagerAuthEnabled()) {

                if (properties.getTenantManagerAuthUrl().isEmpty() ||
                        properties.getTenantManagerClientId().isEmpty() ||
                        properties.getTenantManagerClientSecret().isEmpty()) {
                    throw new DeploymentException("Unsupported configuration, \"registry.enable.multitenancy\" is enabled " +
                            "\"registry.enable.auth\" is enabled " +
                            "but the no auth properties aren't properly configured");
                }

                return OptionalBean.of(new TenantManagerClientImpl(
                        properties.getTenantManagerUrl().get(), Collections.emptyMap(),
                        new OidcAuth(new JdkHttpClientProvider(), properties.getTenantManagerAuthUrl().get(),
                                properties.getTenantManagerClientId().get(),
                                properties.getTenantManagerClientSecret().get(),
                                Optional.empty()
                        )
                ));

            } else {
                return OptionalBean.of(new TenantManagerClientImpl(
                        properties.getTenantManagerUrl().get())
                );
            }
        }

        return OptionalBean.empty();
    }
}
