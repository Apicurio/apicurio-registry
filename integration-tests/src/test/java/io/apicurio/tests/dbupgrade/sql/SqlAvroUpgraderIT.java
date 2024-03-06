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

import java.util.UUID;
import java.util.stream.Collectors;

import static io.apicurio.tests.dbupgrade.UpgradeTestsDataInitializer.PREPARE_AVRO_GROUP;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayNameGeneration(SimpleDisplayName.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag(Constants.DB_UPGRADE)
@Tag(Constants.SQL)
@TestProfile(MultitenancyNoAuthTestProfile.class)
@QuarkusIntegrationTest
@QuarkusTestResource(value = PostgreSqlEmbeddedTestResource.class, restrictToAnnotatedClass = true)
@QuarkusTestResource(value = SqlAvroUpgraderIT.SqlStorageAvroUpgradeTestInitializer.class, restrictToAnnotatedClass = true)
public class SqlAvroUpgraderIT extends ApicurioRegistryBaseIT implements TestSeparator, Constants {

    public static CustomTestsUtils.ArtifactData avroData;
    public static RegistryClient upgradeTenantClient;

    @Override
    public void cleanArtifacts() throws Exception {
        //Don't clean artifacts for this test
    }

    @Test
    public void testStorageUpgradeAvroUpgraderSql() throws Exception {
        testStorageUpgradeAvroUpgrader("testStorageUpgradeAvroUpgraderSql");
    }

    public void testStorageUpgradeAvroUpgrader(String testName) throws Exception {
        //The check must be retried so the storage has been bootstrapped
        retry(() -> assertEquals(3, upgradeTenantClient.listArtifactsInGroup(PREPARE_AVRO_GROUP).getCount()));

        var searchResults = upgradeTenantClient.listArtifactsInGroup(PREPARE_AVRO_GROUP);

        var avros = searchResults.getArtifacts().stream()
                .filter(ar -> ar.getType().equals(ArtifactType.AVRO))
                .collect(Collectors.toList());

        System.out.println("Avro artifacts are " + avros.size());
        assertEquals(1, avros.size());
        var avroMetadata = upgradeTenantClient.getArtifactMetaData(avros.get(0).getGroupId(), avros.get(0).getId());
        var content = upgradeTenantClient.getContentByGlobalId(avroMetadata.getGlobalId());

        //search with canonicalize
        var versionMetadata = upgradeTenantClient.getArtifactVersionMetaDataByContent(avros.get(0).getGroupId(), avros.get(0).getId(), true, null, content);
        assertEquals(avroData.meta.getContentId(), versionMetadata.getContentId());

        String test1content = ApicurioRegistryBaseIT.resourceToString("artifactTypes/" + "avro/multi-field_v1.json");

        //search with canonicalize
        versionMetadata = upgradeTenantClient.getArtifactVersionMetaDataByContent(avros.get(0).getGroupId(), avros.get(0).getId(), true, null, IoUtil.toStream(test1content));
        assertEquals(avroData.meta.getContentId(), versionMetadata.getContentId());

        //search without canonicalize
        versionMetadata = upgradeTenantClient.getArtifactVersionMetaDataByContent(avros.get(0).getGroupId(), avros.get(0).getId(), false, null, IoUtil.toStream(test1content));
        assertEquals(avroData.meta.getContentId(), versionMetadata.getContentId());

        //create one more avro artifact and verify
        String test2content = ApicurioRegistryBaseIT.resourceToString("artifactTypes/" + "avro/multi-field_v2.json");
        avroData = CustomTestsUtils.createArtifact(upgradeTenantClient, PREPARE_AVRO_GROUP, ArtifactType.AVRO, test2content);
        versionMetadata = upgradeTenantClient.getArtifactVersionMetaDataByContent(PREPARE_AVRO_GROUP, avroData.meta.getId(), true, null, IoUtil.toStream(test2content));
        assertEquals(avroData.meta.getContentId(), versionMetadata.getContentId());

        //assert total num of artifacts
        assertEquals(4, upgradeTenantClient.listArtifactsInGroup(PREPARE_AVRO_GROUP).getCount());
    }

    public static class SqlStorageAvroUpgradeTestInitializer extends SqlUpgradeTestInitializer {

        @Override
        public void prepareData(String tenantManagerUrl, String registryBaseUrl) throws Exception {
            UpgradeTestsDataInitializer.prepareTestStorageUpgrade(SqlStorageUpgradeIT.class.getSimpleName(), tenantManagerUrl, "http://localhost:8081");

            MultitenancySupport mt = new MultitenancySupport(tenantManagerUrl, registryBaseUrl);
            TenantUser tenantUser = new TenantUser(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "storageUpgrade", UUID.randomUUID().toString());
            final TenantUserClient tenantUpgradeClient = mt.createTenant(tenantUser);

            //Prepare the data for the content and canonical hash upgraders using an isolated tenant so we don't have data conflicts.
            UpgradeTestsDataInitializer.prepareAvroCanonicalHashUpgradeData(tenantUpgradeClient.client);

            upgradeTenantClient = tenantUpgradeClient.client;
        }

        @Override
        public String getRegistryImage() {
            return "quay.io/apicurio/apicurio-registry-sql:2.1.0.Final";
        }
    }
}
