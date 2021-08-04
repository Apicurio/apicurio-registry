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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.apicurio.multitenant.api.datamodel.NewRegistryTenantRequest;
import io.apicurio.multitenant.api.datamodel.ResourceType;
import io.apicurio.multitenant.api.datamodel.TenantResource;
import io.apicurio.multitenant.client.TenantManagerClient;
import io.apicurio.multitenant.client.TenantManagerClientImpl;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.RegistryClientFactory;
import io.apicurio.registry.rest.client.exception.LimitExceededException;
import io.apicurio.registry.rest.v2.beans.EditableMetaData;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.rest.client.auth.OidcAuth;
import io.apicurio.tests.common.ApicurioRegistryBaseIT;
import io.apicurio.tests.common.Constants;
import io.apicurio.tests.common.RegistryFacade;
import io.apicurio.tests.common.auth.CustomJWTAuth;

/**
 * @author Fabian Martinez
 */
@Tag(Constants.MULTITENANCY)
public class MultitenantLimitsIT extends ApicurioRegistryBaseIT {

    private RegistryFacade registryFacade = RegistryFacade.getInstance();

    @Test
    public void testLimits() {

        var tenant1 = createTenant();
        var tenant2 = createTenant();

        testTenantLimits(tenant1);
        testTenantLimits(tenant2);

    }

    private void testTenantLimits(RegistryClient client) {
        ByteArrayInputStream content = new ByteArrayInputStream("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}".getBytes(StandardCharsets.UTF_8));

        String artifactId = TestUtils.generateArtifactId();

        client.createArtifact(null, artifactId, ArtifactType.JSON, content);
        content.reset();
        client.createArtifactVersion(null, artifactId, null, content);

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

        //schema number 3 , exceeds the max number of schemas
        Assertions.assertThrows(LimitExceededException.class, () -> {
            client.createArtifact(null, artifactId, ArtifactType.JSON, new ByteArrayInputStream("{}".getBytes()));
        });
    }

    private RegistryClient createTenant() {

        String username = UUID.randomUUID().toString();

        NewRegistryTenantRequest tenantReq = new NewRegistryTenantRequest();
        tenantReq.setOrganizationId(UUID.randomUUID().toString());
        tenantReq.setTenantId(UUID.randomUUID().toString());
        tenantReq.setCreatedBy(username);

//        props.put("registry.limits.config.max-total-schemas", "2");
//        props.put("registry.limits.config.max-artifact-properties", "2");
//        props.put("registry.limits.config.max-property-key-size", "4"); //use text test
//        props.put("registry.limits.config.max-property-value-size", "4");
//        props.put("registry.limits.config.max-artifact-lables", "2");
//        props.put("registry.limits.config.max-label-size", "4");
//        props.put("registry.limits.config.max-name-length", "512");
//        props.put("registry.limits.config.max-description-length", "1024");

        List<TenantResource> resources = new ArrayList<>();

        TenantResource tr = new TenantResource();
        tr.setLimit(2L);
        tr.setType(ResourceType.MAX_TOTAL_SCHEMAS_COUNT);
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
                    new OidcAuth(keycloak.tokenEndpoint, keycloak.clientId, keycloak.clientSecret));

        tenantManager.createTenant(tenantReq);

        String tenantAppUrl = TestUtils.getRegistryBaseUrl() + "/t/" + tenantReq.getTenantId();

        return RegistryClientFactory.create(tenantAppUrl + "/apis/registry/v2", Collections.emptyMap(), new CustomJWTAuth(username, tenantReq.getOrganizationId()));
    }

}
