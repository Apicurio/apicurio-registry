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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.UUID;

import javax.inject.Inject;

import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.RegistryClientFactory;
import io.apicurio.registry.rest.client.exception.ArtifactNotFoundException;
import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v2.beans.Rule;
import io.apicurio.registry.rest.v2.beans.SearchedArtifact;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * @author Fabian Martinez
 */
@QuarkusTest
@TestProfile(MultitenancyNoAuthTestProfile.class)
public class MultitenancyNoAuthTest extends AbstractResourceTestBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(MultitenancyNoAuthTest.class);

    @Inject
    @Current
    RegistryStorage storage;

    @Test
    public void testMultitenantRegistry() throws Exception {

        if (!storage.supportsMultiTenancy()) {
            throw new TestAbortedException("Multitenancy not supported - aborting test");
        }

        RegistryClient clientTenant1 = RegistryClientFactory.create("http://localhost:8081/t/" + UUID.randomUUID().toString() + "/apis/registry/v2" );
        RegistryClient clientTenant2 = RegistryClientFactory.create("http://localhost:8081/t/" + UUID.randomUUID().toString() + "/apis/registry/v2" );

        try {
            tenantOperations(clientTenant1);
            try {
                tenantOperations(clientTenant2);
            } finally {
                cleanTenantArtifacts(clientTenant2);
            }
        } finally {
            cleanTenantArtifacts(clientTenant1);
        }

    }

    private void tenantOperations(RegistryClient client) throws Exception {
        assertTrue(client.listArtifactsInGroup(null).getCount().intValue() == 0);

        String artifactId = TestUtils.generateArtifactId();
        ArtifactMetaData meta = client.createArtifact(null, artifactId, ArtifactType.JSON, new ByteArrayInputStream("{}".getBytes()));
        TestUtils.retry(() -> client.getContentByGlobalId(meta.getGlobalId()));

        assertNotNull(client.getLatestArtifact(meta.getGroupId(), meta.getId()));

        assertTrue(client.listArtifactsInGroup(null).getCount().intValue() == 1);

        Rule ruleConfig = new Rule();
        ruleConfig.setType(RuleType.VALIDITY);
        ruleConfig.setConfig("NONE");
        client.createArtifactRule(meta.getGroupId(), meta.getId(), ruleConfig);

        client.createGlobalRule(ruleConfig);
    }

    private void cleanTenantArtifacts(RegistryClient client) throws Exception {
        List<SearchedArtifact> artifacts = client.listArtifactsInGroup(null).getArtifacts();
        for (SearchedArtifact artifact : artifacts) {
            try {
                client.deleteArtifact(artifact.getGroupId(), artifact.getId());
            } catch (ArtifactNotFoundException e) {
                //because of async storage artifact may be already deleted but listed anyway
                LOGGER.info(e.getMessage());
            } catch (Exception e) {
                LOGGER.error("", e);
            }
        }
        TestUtils.retry(() -> assertTrue(client.listArtifactsInGroup(null).getCount().intValue() == 0));
    }


}