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

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.RegistryClientFactory;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.rest.client.auth.exception.AuthErrorHandler;
import io.apicurio.rest.client.spi.ApicurioHttpClient;
import io.apicurio.rest.client.spi.ApicurioHttpClientFactory;
import io.apicurio.tenantmanager.api.datamodel.NewApicurioTenantRequest;
import io.apicurio.tenantmanager.client.TenantManagerClient;
import io.apicurio.tenantmanager.client.TenantManagerClientImpl;
import org.junit.jupiter.api.Assertions;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static io.apicurio.rest.client.config.ApicurioClientConfig.APICURIO_REQUEST_HEADERS_PREFIX;

/**
 * @author Carles Arnal
 */
public class MultitenancySupport {

    private TenantManagerClient tenantManager;
    private final String tenantManagerUrl;
    private final String registryBaseUrl;

    public MultitenancySupport(String tenantManagerUrl, String registryBaseUrl) {
        this.tenantManagerUrl = tenantManagerUrl;
        this.registryBaseUrl = registryBaseUrl;
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
        return new TenantUserClient(user, tenantAppUrl, client, null);
    }

    private String registerTenant(TenantUser user) throws Exception {

        String tenantAppUrl = registryBaseUrl + "/t/" + user.tenantId;

        NewApicurioTenantRequest tenantReq = new NewApicurioTenantRequest();
        tenantReq.setOrganizationId(user.organizationId);
        tenantReq.setTenantId(user.tenantId);
        tenantReq.setCreatedBy(user.principalId);

        TenantManagerClient tenantManager = getTenantManagerClient();
        tenantManager.createTenant(tenantReq);

        TestUtils.retry(() -> Assertions.assertNotNull(tenantManager.getTenant(user.tenantId)));

        return tenantAppUrl;
    }

    public RegistryClient createUserClient(TenantUser user, String tenantAppUrl) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(APICURIO_REQUEST_HEADERS_PREFIX + "X-Tenant-Id", user.organizationId);
        return RegistryClientFactory.create(tenantAppUrl, headers);
    }

    public synchronized TenantManagerClient getTenantManagerClient() {
        if (tenantManager == null) {
            tenantManager = new TenantManagerClientImpl(tenantManagerUrl, Collections.emptyMap(), null);
        }
        return tenantManager;
    }

}
