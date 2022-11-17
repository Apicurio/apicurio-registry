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

import io.apicurio.tenantmanager.api.datamodel.NewApicurioTenantRequest;
import io.apicurio.tenantmanager.api.datamodel.ResourceType;
import io.apicurio.tenantmanager.api.datamodel.TenantResource;
import io.apicurio.tenantmanager.client.TenantManagerClient;
import io.apicurio.tenantmanager.client.TenantManagerClientImpl;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.RegistryClientFactory;
import io.apicurio.registry.rest.client.exception.LimitExceededException;
import io.apicurio.registry.rest.v2.beans.EditableMetaData;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.rest.client.auth.OidcAuth;
import io.apicurio.rest.client.auth.exception.AuthErrorHandler;
import io.apicurio.rest.client.spi.ApicurioHttpClient;
import io.apicurio.rest.client.spi.ApicurioHttpClientFactory;
import io.apicurio.tests.common.ApicurioRegistryBaseIT;
import io.apicurio.tests.common.Constants;
import io.apicurio.tests.common.RegistryFacade;
import io.apicurio.tests.common.auth.CustomJWTAuth;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @author Fabian Martinez
 */
@Tag(Constants.MULTITENANCY)
public class MultitenantLimitsIT extends ApicurioRegistryBaseIT {

    private static final ByteArrayInputStream CONTENT = new ByteArrayInputStream("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}".getBytes(StandardCharsets.UTF_8));
    private static final long CONTENT_SIZE = CONTENT.available();
    private static final ByteArrayInputStream CONTENT_LARGER = new ByteArrayInputStream("{\"type\":\"record\",\"name\":\"myrecord111111111111\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}".getBytes(StandardCharsets.UTF_8));
    private static final long CONTENT_LARGER_SIZE = CONTENT_LARGER.available();

    private static ByteArrayInputStream useContent() {
        CONTENT.reset();
        return CONTENT;
    }

    private static ByteArrayInputStream useContentLarger() {
        CONTENT_LARGER.reset();
        return CONTENT_LARGER;
    }

    private RegistryFacade registryFacade = RegistryFacade.getInstance();

    ApicurioHttpClient httpClient;

    protected ApicurioHttpClient getHttpClient(String baseEndpoint) {
        if (httpClient == null) {
            httpClient = ApicurioHttpClientFactory.create(baseEndpoint, new AuthErrorHandler());
        }
        return httpClient;
    }

    @Test
    public void testLimits() {

        var tenant1 = createTenant();
        var tenant2 = createTenant();

        testTenantLimits(tenant1);
        testTenantLimits(tenant2);

    }

    private void testTenantLimits(RegistryClient client) {


        String artifactId = TestUtils.generateArtifactId();
        client.createArtifact(null, artifactId, ArtifactType.JSON, useContent());
        client.createArtifactVersion(null, artifactId, null, useContent());

        //valid metadata
        EditableMetaData meta = new EditableMetaData();
        meta.setName(StringUtils.repeat('a', 512));
        meta.setDescription(StringUtils.repeat('a', 1024));
        String fourBytesText = StringUtils.repeat('a', 4);
        meta.setProperties(Map.of(
                StringUtils.repeat('a', 4), fourBytesText,
                StringUtils.repeat('b', 4), fourBytesText));
        meta.setLabels(Arrays.asList(fourBytesText, fourBytesText));
        client.updateArtifactVersionMetaData(null, artifactId, "1", meta);

        //invalid metadata
        EditableMetaData invalidmeta = new EditableMetaData();
        invalidmeta.setName(StringUtils.repeat('a', 513));
        invalidmeta.setDescription(StringUtils.repeat('a', 1025));
        String fiveBytesText = StringUtils.repeat('a', 5);
        invalidmeta.setProperties(Map.of(
                StringUtils.repeat('a', 5), fiveBytesText,
                StringUtils.repeat('b', 5), fiveBytesText));
        invalidmeta.setLabels(Arrays.asList(fiveBytesText, fiveBytesText));
        Assertions.assertThrows(LimitExceededException.class, () -> {
            client.updateArtifactVersionMetaData(null, artifactId, "1", invalidmeta);
        });

        //invalid content size
        Assertions.assertThrows(LimitExceededException.class, () -> {
            client.createArtifact(null, TestUtils.generateArtifactId(), ArtifactType.JSON, useContentLarger());
        });

        //schema number 3, ok
        Assertions.assertDoesNotThrow(() -> {
            client.createArtifact(null, TestUtils.generateArtifactId(), ArtifactType.JSON, useContent());
        });

        //schema number 4 , exceeds the max number of schemas
        Assertions.assertThrows(LimitExceededException.class, () -> {
            client.createArtifact(null, TestUtils.generateArtifactId(), ArtifactType.JSON, new ByteArrayInputStream("{}".getBytes()));
        });
    }

    private RegistryClient createTenant() {

        String username = UUID.randomUUID().toString();

        NewApicurioTenantRequest tenantReq = new NewApicurioTenantRequest();
        tenantReq.setOrganizationId(UUID.randomUUID().toString());
        tenantReq.setTenantId(UUID.randomUUID().toString());
        tenantReq.setCreatedBy(username);

        List<TenantResource> resources = new ArrayList<>();

        TenantResource tr = new TenantResource();
        tr.setLimit(3L);
        tr.setType(ResourceType.MAX_TOTAL_SCHEMAS_COUNT);
        resources.add(tr);

        tr = new TenantResource();
        tr.setLimit(CONTENT_SIZE);
        tr.setType(ResourceType.MAX_SCHEMA_SIZE_BYTES);
        resources.add(tr);

        tr = new TenantResource();
        tr.setLimit(2L);
        tr.setType(ResourceType.MAX_ARTIFACT_PROPERTIES_COUNT);
        resources.add(tr);

        tr = new TenantResource();
        tr.setLimit(4L);
        tr.setType(ResourceType.MAX_PROPERTY_KEY_SIZE_BYTES);
        resources.add(tr);

        tr = new TenantResource();
        tr.setLimit(4L);
        tr.setType(ResourceType.MAX_PROPERTY_VALUE_SIZE_BYTES);
        resources.add(tr);

        tr = new TenantResource();
        tr.setLimit(2L);
        tr.setType(ResourceType.MAX_ARTIFACT_LABELS_COUNT);
        resources.add(tr);

        tr = new TenantResource();
        tr.setLimit(512L);
        tr.setType(ResourceType.MAX_ARTIFACT_NAME_LENGTH_CHARS);
        resources.add(tr);

        tr = new TenantResource();
        tr.setLimit(1024L);
        tr.setType(ResourceType.MAX_ARTIFACT_DESCRIPTION_LENGTH_CHARS);
        resources.add(tr);

        tenantReq.setResources(resources);

        //TODO add limits for more resources

        var keycloak = registryFacade.getMTOnlyKeycloakMock();

        TenantManagerClient tenantManager = new TenantManagerClientImpl(registryFacade.getTenantManagerUrl(), Collections.emptyMap(),
                    new OidcAuth(getHttpClient(keycloak.tokenEndpoint), keycloak.clientId, keycloak.clientSecret));

        tenantManager.createTenant(tenantReq);

        String tenantAppUrl = TestUtils.getRegistryBaseUrl() + "/t/" + tenantReq.getTenantId();

        return RegistryClientFactory.create(tenantAppUrl + "/apis/registry/v2", Collections.emptyMap(), new CustomJWTAuth(username, tenantReq.getOrganizationId()));
    }

}
