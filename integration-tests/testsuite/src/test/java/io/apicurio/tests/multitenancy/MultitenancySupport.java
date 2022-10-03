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

package io.apicurio.tests.multitenancy;

import java.util.Collections;
import java.util.UUID;

import io.apicurio.rest.client.auth.exception.AuthErrorHandler;
import io.apicurio.rest.client.spi.ApicurioHttpClient;
import io.apicurio.rest.client.spi.ApicurioHttpClientFactory;
import org.junit.jupiter.api.Assertions;

import io.apicurio.tenantmanager.api.datamodel.NewApicurioTenantRequest;
import io.apicurio.tenantmanager.client.TenantManagerClient;
import io.apicurio.tenantmanager.client.TenantManagerClientImpl;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.RegistryClientFactory;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.rest.client.auth.OidcAuth;
import io.apicurio.tests.common.RegistryFacade;
import io.apicurio.tests.common.auth.CustomJWTAuth;

/**
 * @author Fabian Martinez
 */
public class MultitenancySupport {

    private RegistryFacade registryFacade = RegistryFacade.getInstance();

    private TenantManagerClient tenantManager;

    public MultitenancySupport() {
        //singleton
    }

    ApicurioHttpClient httpClient;

    protected ApicurioHttpClient getHttpClient(String serverUrl) {
        if (httpClient == null) {
            httpClient = ApicurioHttpClientFactory.create(serverUrl, new AuthErrorHandler());
        }
        return httpClient;
    }

    public TenantUserClient createTenant() throws Exception {
        TenantUser user = new TenantUser(UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString());
        return createTenant(user);
    }

    public TenantUserClient createTenant(TenantUser user) throws Exception {
        String tenantAppUrl = registerTenant(user);
        var client = createUserClient(user, tenantAppUrl);
        registryFacade.getMTOnlyKeycloakMock().addStubForTenant(user.principalId, user.principalPassword, user.organizationId);
        return new TenantUserClient(user, tenantAppUrl, client, registryFacade.getMTOnlyKeycloakMock().tokenEndpoint);
    }

    private String registerTenant(TenantUser user) throws Exception {

        String tenantAppUrl = TestUtils.getRegistryBaseUrl() + "/t/" + user.tenantId;

        NewApicurioTenantRequest tenantReq = new NewApicurioTenantRequest();
        tenantReq.setOrganizationId(user.organizationId);
        tenantReq.setTenantId(user.tenantId);
        tenantReq.setCreatedBy(user.principalId);

        TenantManagerClient tenantManager = getTenantManagerClient();
        tenantManager.createTenant(tenantReq);

        TestUtils.retry(() -> Assertions.assertNotNull(tenantManager.getTenant(user.tenantId)));

        return tenantAppUrl;
    }

    public RegistryClient createUserClientCustomJWT(TenantUser user, String tenantAppUrl) {
        return RegistryClientFactory.create(tenantAppUrl, Collections.emptyMap(), new CustomJWTAuth(user.principalId, user.organizationId));
    }

    public RegistryClient createUserClient(TenantUser user, String tenantAppUrl) {
        var keycloak = registryFacade.getMTOnlyKeycloakMock();
        return RegistryClientFactory.create(tenantAppUrl, Collections.emptyMap(), new OidcAuth(getHttpClient(keycloak.tokenEndpoint), user.principalId, user.principalPassword));
    }

    public synchronized TenantManagerClient getTenantManagerClient() {
        if (tenantManager == null) {
            var keycloak = registryFacade.getMTOnlyKeycloakMock();
            tenantManager = new TenantManagerClientImpl(registryFacade.getTenantManagerUrl(), Collections.emptyMap(),
                    new OidcAuth(getHttpClient(keycloak.tokenEndpoint), keycloak.clientId, keycloak.clientSecret));
        }
        return tenantManager;
    }

}
