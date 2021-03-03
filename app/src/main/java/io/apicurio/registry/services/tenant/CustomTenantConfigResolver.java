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

package io.apicurio.registry.services.tenant;

import io.apicurio.registry.mt.TenantContext;
import io.apicurio.registry.mt.TenantIdResolver;
import io.apicurio.registry.mt.metadata.TenantMetadataDto;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.types.Current;
import io.quarkus.oidc.OidcTenantConfig;
import io.quarkus.oidc.TenantConfigResolver;
import io.quarkus.oidc.common.runtime.OidcCommonConfig.Tls.Verification;
import io.vertx.ext.web.RoutingContext;

import java.util.Optional;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class provides the logic to configure the authentication layer with the tenant configuration
 * for an incoming http request.
 *
 *
 * @author Carles Arnal
 * @author Fabian Martinez
 *
 */
@ApplicationScoped
public class CustomTenantConfigResolver implements TenantConfigResolver {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    @Inject
    @Current
    RegistryStorage registryStorage;

    @Inject
    TenantContext tenantContext;

    @Inject
    @ConfigProperty(name = "quarkus.oidc.tls.verification")
    Optional<String> tlsVerification;

    @Inject
    TenantIdResolver tenantIdResolver;

    @Override
    public OidcTenantConfig resolve(RoutingContext context) {

        if (!tenantIdResolver.resolveTenantId(context)) {
            log.debug("Tenant config is not loaded, fallback to default tenant");
            // resolve to default tenant configuration
            return null;
        }

        final String tenantId = tenantContext.tenantId();

        log.debug("Resolving authz config for tenant {}", tenantId);

        final TenantMetadataDto registryTenant = registryStorage.getTenantMetadata(tenantId);
        final OidcTenantConfig config = new OidcTenantConfig();

        config.setTenantId(registryTenant.getTenantId());
        config.setAuthServerUrl(registryTenant.getAuthServerUrl());
        config.setClientId(registryTenant.getClientId());

        if (tlsVerification.isPresent() && tlsVerification.get().equalsIgnoreCase("none")) {
            config.tls.verification = Optional.of(Verification.NONE);
        }

        return config;
    }
}
