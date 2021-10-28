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

package io.apicurio.tests.multitenancy.dbupgrade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.RemoteDockerImage;
import org.testcontainers.utility.DockerImageName;

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v2.beans.Rule;
import io.apicurio.registry.rest.v2.beans.VersionMetaData;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.tests.SimpleDisplayName;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.ApicurioV2BaseIT;
import io.apicurio.tests.common.Constants;
import io.apicurio.tests.common.RegistryFacade;
import io.apicurio.tests.common.interfaces.TestSeparator;
import io.apicurio.tests.common.utils.RegistryUtils;
import io.apicurio.tests.multitenancy.MultitenancySupport;
import io.apicurio.tests.multitenancy.TenantUserClient;

/**
 * Note this test does not extend any base class
 *
 * @author Fabian Martinez
 */
@DisplayNameGeneration(SimpleDisplayName.class)
@TestInstance(Lifecycle.PER_CLASS)
@Tag(Constants.DB_UPGRADE)
public class SqlStorageUpgradeIT implements TestSeparator, Constants {

    @Test
    public void testStorageUpgrade() throws Exception {

        Path logsPath = RegistryUtils.getLogsPath(getClass(), "testStorageUpgrade");
        RegistryFacade facade = RegistryFacade.getInstance();

        try {

            Map<String, String> appEnv = facade.initRegistryAppEnv();

            //runs all required infra except for the registry
            facade.runMultitenancyInfra(appEnv);

            appEnv.put("QUARKUS_HTTP_PORT", "8081");

            String oldRegistryName = "registry-sql-dbv2";
            var container = new GenericContainer<>(new RemoteDockerImage(DockerImageName.parse("quay.io/apicurio/apicurio-registry-sql:2.1.0.Final")));
            container.setNetworkMode("host");
            facade.runContainer(appEnv, oldRegistryName, container);
            facade.waitForRegistryReady();

            MultitenancySupport mt = new MultitenancySupport();

            List<TenantData> data = loadData(mt);

            verifyData(data);

            facade.stopContainer(logsPath, oldRegistryName);

            facade.runRegistry(appEnv, "sql-dblatest", "8081");
            facade.waitForRegistryReady();

            verifyData(data);

            createMoreArtifacts(data);

            verifyData(data);

        } finally {
            facade.stopAndCollectLogs(logsPath);
        }

    }

    private List<TenantData> loadData(MultitenancySupport mt) throws Exception {

        List<TenantData> tenants = new ArrayList<>();

        for (int i = 0; i < 50; i++) {

            TenantData tenant = new TenantData();
            TenantUserClient user = mt.createTenant();
            tenant.tenant = user;
            tenants.add(tenant);

            RegistryClient client = user.client;

            Rule comp = new Rule();
            comp.setType(RuleType.COMPATIBILITY);
            comp.setConfig("BACKWARD");
            client.createGlobalRule(comp);
            Rule val = new Rule();
            val.setType(RuleType.VALIDITY);
            val.setConfig("SYNTAX_ONLY");
            client.createGlobalRule(val);

            tenant.artifacts.add(createArtifact(client, ArtifactType.AVRO, ApicurioV2BaseIT.resourceToString("artifactTypes/" + "avro/multi-field_v1.json")));
            tenant.artifacts.add(createArtifact(client, ArtifactType.JSON, ApicurioV2BaseIT.resourceToString("artifactTypes/" + "jsonSchema/person_v1.json")));
            tenant.artifacts.add(createArtifact(client, ArtifactType.ASYNCAPI, ApicurioV2BaseIT.resourceToString("artifactTypes/" + "asyncapi/2.0-streetlights_v1.json")));

        }

        return tenants;

    }

    private void createMoreArtifacts(List<TenantData> tenants) throws Exception {
        for (TenantData tenant : tenants) {
            var client = tenant.tenant.client;

            tenant.artifacts.add(createArtifact(client, ArtifactType.AVRO, ApicurioV2BaseIT.resourceToString("artifactTypes/" + "avro/multi-field_v1.json")));
            tenant.artifacts.add(createArtifact(client, ArtifactType.KCONNECT, ApicurioV2BaseIT.resourceToString("artifactTypes/" + "kafkaConnect/simple_v1.json")));
        }
    }

    private void verifyData(List<TenantData> tenants) {
        for (TenantData tenant : tenants) {

            var client = tenant.tenant.client;

            var grules = client.listGlobalRules();
            assertEquals(2, grules.size());

            for (ArtifactData data : tenant.artifacts) {
                ArtifactMetaData meta = data.meta;

                String content = IoUtil.toString(client.getArtifactVersion(meta.getGroupId(), meta.getId(), meta.getVersion()));
                String contentHash = DigestUtils.sha256Hex(IoUtil.toBytes(content));

                assertEquals(data.contentHash, contentHash);

                String contentgid = IoUtil.toString(client.getContentByGlobalId(meta.getGlobalId()));
                String contentgidHash = DigestUtils.sha256Hex(IoUtil.toBytes(contentgid));

                assertEquals(data.contentHash, contentgidHash);

                String contentcid = IoUtil.toString(client.getContentById(meta.getContentId()));
                String contentcidHash = DigestUtils.sha256Hex(IoUtil.toBytes(contentcid));

                assertEquals(data.contentHash, contentcidHash);

                VersionMetaData vmeta = client.getArtifactVersionMetaData(meta.getGroupId(), meta.getId(), meta.getVersion());
                assertEquals(meta.getContentId(), vmeta.getContentId());

            }

        }
    }

    private ArtifactData createArtifact(RegistryClient client, ArtifactType type, String content) throws Exception {
        String artifactId = TestUtils.generateArtifactId();
        ArtifactMetaData meta = client.createArtifact(null, artifactId, ArtifactType.JSON, IoUtil.toStream(content));
        TestUtils.retry(() -> client.getContentByGlobalId(meta.getGlobalId()));
        assertNotNull(client.getLatestArtifact(meta.getGroupId(), meta.getId()));
        String contentHash = DigestUtils.sha256Hex(IoUtil.toBytes(content));
        return new ArtifactData(meta, contentHash);
    }

    private class TenantData {
        TenantUserClient tenant;
        List<ArtifactData> artifacts;

        public TenantData() {
            artifacts = new ArrayList<>();
        }
    }

    private class ArtifactData {
        ArtifactMetaData meta;
        String contentHash;
        public ArtifactData(ArtifactMetaData meta, String contentHash) {
            this.meta = meta;
            this.contentHash = contentHash;
        }
    }
}
