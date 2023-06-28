/*
 * Copyright 2023 Red Hat
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

package io.apicurio.tests.dbupgrade;

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v2.beans.ArtifactReference;
import io.apicurio.registry.rest.v2.beans.Rule;
import io.apicurio.registry.rest.v2.beans.VersionMetaData;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.apicurio.tests.multitenancy.MultitenancySupport;
import io.apicurio.tests.multitenancy.TenantUserClient;
import io.apicurio.tests.utils.CustomTestsUtils;
import io.apicurio.tests.utils.RegistryWaitUtils;
import org.apache.commons.codec.digest.DigestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static io.apicurio.tests.ApicurioRegistryBaseIT.resourceToString;
import static io.apicurio.tests.dbupgrade.KafkaSqlStorageUpgradeIT.PREPARE_PROTO_GROUP;
import static io.apicurio.tests.utils.CustomTestsUtils.createArtifact;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class UpgradeTestsDataInitializer {

    protected static final String REFERENCE_CONTENT = "{\"name\":\"ibm\"}";
    protected static final String ARTIFACT_CONTENT = "{\"name\":\"redhat\"}";

    protected static void prepareProtobufHashUpgradeTest(RegistryClient registryClient) throws Exception {
        RegistryWaitUtils.retry(registryClient, registryClient1 -> CustomTestsUtils.createArtifact(registryClient, PREPARE_PROTO_GROUP, ArtifactType.AVRO, resourceToString("artifactTypes/" + "avro/multi-field_v1.json")));
        CustomTestsUtils.createArtifact(registryClient, PREPARE_PROTO_GROUP, ArtifactType.JSON, resourceToString("artifactTypes/" + "jsonSchema/person_v1.json"));

        String test1content = resourceToString("artifactTypes/" + "protobuf/tutorial_v1.proto");
        var protoData = CustomTestsUtils.createArtifact(registryClient, PREPARE_PROTO_GROUP, ArtifactType.PROTOBUF, test1content);

        //verify search with canonicalize returns the expected artifact metadata
        var versionMetadata = registryClient.getArtifactVersionMetaDataByContent(PREPARE_PROTO_GROUP, protoData.meta.getId(), true, null, IoUtil.toStream(test1content));
        assertEquals(protoData.meta.getContentId(), versionMetadata.getContentId());

        assertEquals(3, registryClient.listArtifactsInGroup(PREPARE_PROTO_GROUP).getCount());

        //Once prepared, set the global variable, so we can compare during the test execution
        KafkaSqlStorageUpgradeIT.protoData = protoData;
        SqlStorageUpgradeIT.protoData = protoData;
    }

    protected static void prepareReferencesUpgradeTest(RegistryClient registryClient) throws Exception {
        final CustomTestsUtils.ArtifactData artifact = CustomTestsUtils.createArtifact(registryClient, ArtifactType.JSON, REFERENCE_CONTENT);

        //Create a second artifact referencing the first one, the hash will be the same using version 2.4.1.Final.
        var artifactReference = new ArtifactReference();

        artifactReference.setName("testReference");
        artifactReference.setArtifactId(artifact.meta.getId());
        artifactReference.setGroupId(artifact.meta.getGroupId());
        artifactReference.setVersion(artifact.meta.getVersion());

        var artifactReferences = List.of(artifactReference);

        String artifactId = UUID.randomUUID().toString();

        final CustomTestsUtils.ArtifactData artifactWithReferences = CustomTestsUtils.createArtifactWithReferences(artifactId, registryClient, ArtifactType.AVRO, ARTIFACT_CONTENT, artifactReferences);

        String calculatedHash = DigestUtils.sha256Hex(ARTIFACT_CONTENT);

        //Assertions
        //The artifact hash is calculated without using references
        assertEquals(calculatedHash, artifactWithReferences.contentHash);

        //Once prepared, set the global variables, so we can compare during the test execution.
        KafkaSqlStorageUpgradeIT.artifactReferences = artifactReferences;
        KafkaSqlStorageUpgradeIT.artifactWithReferences = artifactWithReferences;
        SqlStorageUpgradeIT.artifactReferences = artifactReferences;
        SqlStorageUpgradeIT.artifactWithReferences = artifactWithReferences;
    }

    protected static void prepareTestStorageUpgrade(String testName, String tenantManagerUrl, String registryBaseUrl) throws Exception {
        MultitenancySupport mt = new MultitenancySupport(tenantManagerUrl, registryBaseUrl);

        List<TenantData> data = loadData(mt, testName);

        verifyData(data);

        SqlStorageUpgradeIT.data = data;
    }

    private static List<TenantData> loadData(MultitenancySupport mt, String testName) throws Exception {

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

            tenant.artifacts.add(CustomTestsUtils.createArtifact(client, ArtifactType.AVRO, resourceToString("artifactTypes/" + "avro/multi-field_v1.json")));
            tenant.artifacts.add(CustomTestsUtils.createArtifact(client, ArtifactType.JSON, resourceToString("artifactTypes/" + "jsonSchema/person_v1.json")));
            tenant.artifacts.add(CustomTestsUtils.createArtifact(client, ArtifactType.ASYNCAPI, resourceToString("artifactTypes/" + "asyncapi/2.0-streetlights_v1.json")));
        }
        return tenants;
    }

    protected static void verifyData(List<TenantData> tenants) {
        for (TenantData tenant : tenants) {

            var client = tenant.tenant.client;

            var grules = client.listGlobalRules();
            assertEquals(2, grules.size());

            for (CustomTestsUtils.ArtifactData data : tenant.artifacts) {
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

    protected static void createMoreArtifacts(List<TenantData> tenants) throws Exception {
        for (TenantData tenant : tenants) {
            var client = tenant.tenant.client;

            tenant.artifacts.add(createArtifact(client, ArtifactType.AVRO, ApicurioRegistryBaseIT.resourceToString("artifactTypes/" + "avro/multi-field_v1.json")));
            tenant.artifacts.add(createArtifact(client, ArtifactType.KCONNECT, ApicurioRegistryBaseIT.resourceToString("artifactTypes/" + "kafkaConnect/simple_v1.json")));
        }
    }

    protected static class TenantData {
        TenantUserClient tenant;
        List<CustomTestsUtils.ArtifactData> artifacts;

        public TenantData() {
            artifacts = new ArrayList<>();
        }
    }
}
