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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static io.apicurio.tests.ApicurioRegistryBaseIT.resourceToString;
import static io.apicurio.tests.utils.CustomTestsUtils.createArtifact;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class UpgradeTestsDataInitializer {

    protected static final String REFERENCE_CONTENT = "{\"name\":\"ibm\"}";
    protected static final String ARTIFACT_CONTENT = "{\"name\":\"redhat\"}";

    public static final String PREPARE_LOG_COMPACTION = "prepareLogCompactionGroup";
    public static final String PREPARE_PROTO_GROUP = "prepareProtobufHashUpgradeTest";
    public static final String PREPARE_REFERENCES_GROUP = "prepareReferencesUpgradeTest";

    private static final Logger logger = LoggerFactory.getLogger(UpgradeTestsDataInitializer.class);

    public static void prepareProtobufHashUpgradeTest(RegistryClient registryClient) throws Exception {
        logger.info("Preparing ProtobufHashUpgradeTest test data...");

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

        logger.info("Finished preparing ProtobufHashUpgradeTest test data.");
    }

    public static void prepareReferencesUpgradeTest(RegistryClient registryClient) throws Exception {
        logger.info("Preparing ReferencesUpgradeTest test data...");

        final CustomTestsUtils.ArtifactData artifact = CustomTestsUtils.createArtifact(registryClient, PREPARE_REFERENCES_GROUP, ArtifactType.JSON, REFERENCE_CONTENT);

        //Create a second artifact referencing the first one, the hash will be the same using version 2.4.1.Final.
        var artifactReference = new ArtifactReference();

        artifactReference.setName("testReference");
        artifactReference.setArtifactId(artifact.meta.getId());
        artifactReference.setGroupId(artifact.meta.getGroupId());
        artifactReference.setVersion(artifact.meta.getVersion());

        var artifactReferences = List.of(artifactReference);

        String artifactId = UUID.randomUUID().toString();

        final CustomTestsUtils.ArtifactData artifactWithReferences = CustomTestsUtils.createArtifactWithReferences(PREPARE_REFERENCES_GROUP, artifactId, registryClient, ArtifactType.AVRO, ARTIFACT_CONTENT, artifactReferences);

        String calculatedHash = DigestUtils.sha256Hex(ARTIFACT_CONTENT);

        //Assertions
        //The artifact hash is calculated without using references
        assertEquals(calculatedHash, artifactWithReferences.contentHash);

        //Once prepared, set the global variables, so we can compare during the test execution.
        KafkaSqlStorageUpgradeIT.artifactReferences = artifactReferences;
        KafkaSqlStorageUpgradeIT.artifactWithReferences = artifactWithReferences;
        SqlStorageUpgradeIT.artifactReferences = artifactReferences;
        SqlStorageUpgradeIT.artifactWithReferences = artifactWithReferences;

        logger.info("Finished preparing ReferencesUpgradeTest test data.");
    }

    public static void prepareTestStorageUpgrade(String testName, String tenantManagerUrl, String registryBaseUrl) throws Exception {
        logger.info("Preparing TestStorageUpgrade test data...");

        MultitenancySupport mt = new MultitenancySupport(tenantManagerUrl, registryBaseUrl);

        List<TenantData> data = loadData(mt, testName);

        verifyData(data);

        SqlStorageUpgradeIT.data = data;

        logger.info("Finished preparing TestStorageUpgrade test data.");
    }

    public static void prepareLogCompactionTests(RegistryClient registryClient) throws Exception {
        try {

            logger.info("Preparing LogCompactionTests test data...");

            RegistryWaitUtils.retry(registryClient, registryClient1 -> CustomTestsUtils.createArtifact(registryClient, PREPARE_LOG_COMPACTION, ArtifactType.AVRO, ApicurioRegistryBaseIT.resourceToString("artifactTypes/" + "avro/multi-field_v1.json")));

            var artifactdata = CustomTestsUtils.createArtifact(registryClient, PREPARE_LOG_COMPACTION, ArtifactType.JSON, ApicurioRegistryBaseIT.resourceToString("artifactTypes/" + "jsonSchema/person_v1.json"));
            CustomTestsUtils.createArtifact(registryClient, PREPARE_LOG_COMPACTION, ArtifactType.PROTOBUF, ApicurioRegistryBaseIT.resourceToString("artifactTypes/" + "protobuf/tutorial_v1.proto"));
            assertEquals(3, registryClient.listArtifactsInGroup(PREPARE_LOG_COMPACTION).getCount());

            //spend some time doing something
            //this is just to give kafka some time to be 100% the topic is log compacted
            logger.info("Giving kafka some time to do log compaction");
            for (int i = 0; i < 15; i++) {
                registryClient.getArtifactMetaData(artifactdata.meta.getGroupId(), artifactdata.meta.getId());
                try {
                    Thread.sleep(900);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            logger.info("Finished giving kafka some time");
        } catch (Exception e) {
            logger.warn("Error filling origin with artifacts information:", e);
        }

        logger.info("Finished preparing LogCompactionTests test data.");
    }

    private static List<TenantData> loadData(MultitenancySupport mt, String testName) throws Exception {

        List<TenantData> tenants = new ArrayList<>();

        for (int i = 0; i < 10; i++) {

            TenantData tenant = new TenantData();
            TenantUserClient user = mt.createTenant();
            tenant.tenant = user;
            tenants.add(tenant);

            logger.info("Tenant {} created...", tenant.tenant.user.tenantId);

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

            logger.info("Tenant {} filled with test data...", tenant.tenant.user.tenantId);
        }
        return tenants;
    }

    public static void verifyData(List<TenantData> tenants) {

        for (TenantData tenant : tenants) {

            logger.info("Verifying tenant {} data...", tenant.tenant.user.tenantId);

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

                logger.info("Tenant {} data verified...", tenant.tenant.user.tenantId);
            }
        }
    }

    public static void createMoreArtifacts(List<TenantData> tenants) throws Exception {
        for (TenantData tenant : tenants) {
            var client = tenant.tenant.client;

            tenant.artifacts.add(createArtifact(client, ArtifactType.AVRO, ApicurioRegistryBaseIT.resourceToString("artifactTypes/" + "avro/multi-field_v1.json")));
            tenant.artifacts.add(createArtifact(client, ArtifactType.KCONNECT, ApicurioRegistryBaseIT.resourceToString("artifactTypes/" + "kafkaConnect/simple_v1.json")));
        }
    }

    public static class TenantData {
        TenantUserClient tenant;
        List<CustomTestsUtils.ArtifactData> artifacts;

        public TenantData() {
            artifacts = new ArrayList<>();
        }
    }
}
