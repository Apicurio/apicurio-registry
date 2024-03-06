package io.apicurio.tests.dbupgrade.sql;

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.tests.MultitenancyNoAuthTestProfile;
import io.apicurio.registry.utils.tests.PostgreSqlEmbeddedTestResource;
import io.apicurio.registry.utils.tests.SimpleDisplayName;
import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.apicurio.tests.dbupgrade.UpgradeTestsDataInitializer;
import io.apicurio.tests.multitenancy.MultitenancySupport;
import io.apicurio.tests.multitenancy.TenantUser;
import io.apicurio.tests.multitenancy.TenantUserClient;
import io.apicurio.tests.utils.Constants;
import io.apicurio.tests.utils.CustomTestsUtils;
import io.apicurio.tests.utils.TestSeparator;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.stream.Collectors;

import static io.apicurio.tests.dbupgrade.UpgradeTestsDataInitializer.PREPARE_PROTO_GROUP;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayNameGeneration(SimpleDisplayName.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag(Constants.DB_UPGRADE)
@Tag(Constants.SQL)
@TestProfile(MultitenancyNoAuthTestProfile.class)
@QuarkusIntegrationTest
@QuarkusTestResource(value = PostgreSqlEmbeddedTestResource.class, restrictToAnnotatedClass = true)
@QuarkusTestResource(value = SqlProtobufCanonicalHashUpgraderIT.SqlStorageUpgradeTestInitializer.class, restrictToAnnotatedClass = true)
public class SqlProtobufCanonicalHashUpgraderIT extends ApicurioRegistryBaseIT implements TestSeparator, Constants {

    public static CustomTestsUtils.ArtifactData protoData;
    public static RegistryClient upgradeTenantClient;
    private static final Logger logger = LoggerFactory.getLogger(SqlStorageUpgradeIT.class);

    @Override
    public void cleanArtifacts() throws Exception {
        //Don't clean artifacts for this test
    }

    @Test
    public void testStorageUpgradeProtobufUpgraderSql() throws Exception {
        testStorageUpgradeProtobufUpgrader("protobufCanonicalHashSql");
    }

    public void testStorageUpgradeProtobufUpgrader(String testName) throws Exception {
        //The check must be retried so the storage has been bootstrapped
        retry(() -> assertEquals(3, upgradeTenantClient.listArtifactsInGroup(PREPARE_PROTO_GROUP).getCount()));

        var searchResults = upgradeTenantClient.listArtifactsInGroup(PREPARE_PROTO_GROUP);

        var protobufs = searchResults.getArtifacts().stream()
                .filter(ar -> ar.getType().equals(ArtifactType.PROTOBUF))
                .collect(Collectors.toList());

        System.out.println("Protobuf artifacts are " + protobufs.size());
        assertEquals(1, protobufs.size());
        var protoMetadata = upgradeTenantClient.getArtifactMetaData(protobufs.get(0).getGroupId(), protobufs.get(0).getId());
        var content = upgradeTenantClient.getContentByGlobalId(protoMetadata.getGlobalId());

        //search with canonicalize
        var versionMetadata = upgradeTenantClient.getArtifactVersionMetaDataByContent(protobufs.get(0).getGroupId(), protobufs.get(0).getId(), true, null, content);
        assertEquals(protoData.meta.getContentId(), versionMetadata.getContentId());

        String test1content = ApicurioRegistryBaseIT.resourceToString("artifactTypes/" + "protobuf/tutorial_v1.proto");

        //search with canonicalize
        versionMetadata = upgradeTenantClient.getArtifactVersionMetaDataByContent(protobufs.get(0).getGroupId(), protobufs.get(0).getId(), true, null, IoUtil.toStream(test1content));
        assertEquals(protoData.meta.getContentId(), versionMetadata.getContentId());

        //search without canonicalize
        versionMetadata = upgradeTenantClient.getArtifactVersionMetaDataByContent(protobufs.get(0).getGroupId(), protobufs.get(0).getId(), false, null, IoUtil.toStream(test1content));
        assertEquals(protoData.meta.getContentId(), versionMetadata.getContentId());

        //create one more protobuf artifact and verify
        String test2content = ApicurioRegistryBaseIT.resourceToString("artifactTypes/" + "protobuf/tutorial_v2.proto");
        protoData = CustomTestsUtils.createArtifact(upgradeTenantClient, PREPARE_PROTO_GROUP, ArtifactType.PROTOBUF, test2content);
        versionMetadata = upgradeTenantClient.getArtifactVersionMetaDataByContent(PREPARE_PROTO_GROUP, protoData.meta.getId(), true, null, IoUtil.toStream(test2content));
        assertEquals(protoData.meta.getContentId(), versionMetadata.getContentId());

        //assert total num of artifacts
        assertEquals(4, upgradeTenantClient.listArtifactsInGroup(PREPARE_PROTO_GROUP).getCount());
    }

    public static class SqlStorageUpgradeTestInitializer extends SqlUpgradeTestInitializer {

        @Override
        public void prepareData(String tenantManagerUrl, String registryBaseUrl) throws Exception {
            MultitenancySupport mt = new MultitenancySupport(tenantManagerUrl, registryBaseUrl);
            TenantUser tenantUser = new TenantUser(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "storageUpgrade", UUID.randomUUID().toString());
            final TenantUserClient tenantUpgradeClient = mt.createTenant(tenantUser);

            //Prepare the data for the content and canonical hash upgraders using an isolated tenant so we don't have data conflicts.
            UpgradeTestsDataInitializer.prepareProtobufHashUpgradeTest(tenantUpgradeClient.client);

            upgradeTenantClient = tenantUpgradeClient.client;
        }

        public String getRegistryImage() {
            return "quay.io/apicurio/apicurio-registry-sql:2.3.0.Final";
        }
    }
}
