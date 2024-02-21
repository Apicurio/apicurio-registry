package io.apicurio.tests.dbupgrade.sql;

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.v2.beans.ArtifactReference;
import io.apicurio.registry.types.ArtifactType;
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

import java.util.List;
import java.util.UUID;

import static io.apicurio.tests.dbupgrade.UpgradeTestsDataInitializer.ARTIFACT_CONTENT;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayNameGeneration(SimpleDisplayName.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag(Constants.DB_UPGRADE)
@Tag(Constants.SQL)
@TestProfile(MultitenancyNoAuthTestProfile.class)
@QuarkusIntegrationTest
@QuarkusTestResource(value = PostgreSqlEmbeddedTestResource.class, restrictToAnnotatedClass = true)
@QuarkusTestResource(value = SqlReferencesUpgraderIT.SqlStorageUpgradeTestInitializer.class, restrictToAnnotatedClass = true)
public class SqlReferencesUpgraderIT extends ApicurioRegistryBaseIT implements TestSeparator, Constants {

    public static CustomTestsUtils.ArtifactData artifactWithReferences;
    public static List<ArtifactReference> artifactReferences;

    public static RegistryClient upgradeTenantClient;

    @Override
    public void cleanArtifacts() throws Exception {
        //Don't clean artifacts for this test
    }

    @Test
    public void testStorageUpgradeReferencesContentHash() throws Exception {
        testStorageUpgradeReferencesContentHashUpgrader("referencesContentHash");
    }

    public void testStorageUpgradeReferencesContentHashUpgrader(String testName) throws Exception {
        //Once the storage is filled with the proper information, if we try to create the same artifact with the same references, no new version will be created and the same ids are used.
        CustomTestsUtils.ArtifactData upgradedArtifact = CustomTestsUtils.createArtifactWithReferences(artifactWithReferences.meta.getGroupId(), artifactWithReferences.meta.getId(), upgradeTenantClient, ArtifactType.AVRO, ARTIFACT_CONTENT, artifactReferences);
        assertEquals(artifactWithReferences.meta.getGlobalId(), upgradedArtifact.meta.getGlobalId());
        assertEquals(artifactWithReferences.meta.getContentId(), upgradedArtifact.meta.getContentId());
    }

    public static class SqlStorageUpgradeTestInitializer extends SqlUpgradeTestInitializer {

        @Override
        public void prepareData(String tenantManagerUrl, String registryBaseUrl) throws Exception {
            MultitenancySupport mt = new MultitenancySupport(tenantManagerUrl, registryBaseUrl);
            TenantUser tenantUser = new TenantUser(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "storageUpgrade", UUID.randomUUID().toString());
            final TenantUserClient tenantUpgradeClient = mt.createTenant(tenantUser);

            //Prepare the data for the content and canonical hash upgraders using an isolated tenant so we don't have data conflicts.
            UpgradeTestsDataInitializer.prepareReferencesUpgradeTest(tenantUpgradeClient.client);

            upgradeTenantClient = tenantUpgradeClient.client;
        }

        public String getRegistryImage() {
            return "quay.io/apicurio/apicurio-registry-sql:2.3.0.Final";
        }
    }
}
