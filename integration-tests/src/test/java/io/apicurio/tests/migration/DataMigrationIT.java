package io.apicurio.tests.migration;

import io.apicurio.registry.client.auth.VertXAuthFactory;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.ArtifactReference;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.apicurio.tests.utils.AbstractTestDataInitializer;
import io.apicurio.tests.utils.Constants;
import io.kiota.http.vertx.VertXRequestAdapter;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.apicurio.registry.utils.tests.TestUtils.getRegistryV2ApiUrl;
import static io.apicurio.tests.migration.MigrationTestsDataInitializer.matchesReferencesV2V3;
import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.anything;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusIntegrationTest
@QuarkusTestResource(value = DataMigrationIT.MigrateTestInitializer.class, restrictToAnnotatedClass = true)
@Tag(Constants.MIGRATION)
public class DataMigrationIT extends ApicurioRegistryBaseIT {

    private static final Logger log = LoggerFactory.getLogger(DataMigrationIT.class);

    public static InputStream migrateDataToImport;
    public static HashMap<Long, List<io.apicurio.registry.rest.client.v2.models.ArtifactReference>> migrateReferencesMap = new HashMap<>();
    public static List<Long> migrateGlobalIds = new ArrayList<>();
    public static Map<String, String> doNotPreserveIdsImportArtifacts = new HashMap<>();

    /**
     * The data required for this test is initialized by
     * MigrationTestsDataInitializer.initializeMigrateTest(RegistryClient)
     *
     * @throws Exception
     */
    @Test
    public void migrate() throws Exception {
        var adapter = new VertXRequestAdapter(VertXAuthFactory.defaultVertx);
        adapter.setBaseUrl(ApicurioRegistryBaseIT.getRegistryV3ApiUrl());
        RegistryClient dest = new RegistryClient(adapter);

        given().when().contentType("application/zip").body(migrateDataToImport)
                .post("/apis/registry/v2/admin/import").then().statusCode(204).body(anything());

        for (long gid : migrateGlobalIds) {
            dest.ids().globalIds().byGlobalId(gid).get();
            if (migrateReferencesMap.containsKey(gid)) {
                List<io.apicurio.registry.rest.client.v2.models.ArtifactReference> srcReferences = migrateReferencesMap
                        .get(gid);
                List<ArtifactReference> destReferences = dest.ids().globalIds().byGlobalId(gid).references()
                        .get();
                assertTrue(matchesReferencesV2V3(srcReferences, destReferences));
            }
        }
        try {
            assertEquals("SYNTAX_ONLY", dest.groups().byGroupId("migrateTest").artifacts()
                    .byArtifactId("avro-0").rules().byRuleType(RuleType.VALIDITY.name()).get().getConfig());
            assertEquals("BACKWARD",
                    dest.admin().rules().byRuleType(RuleType.COMPATIBILITY.name()).get().getConfig());
        } catch (ProblemDetails e) {
            log.error("REST Client error: " + e.getTitle());
            log.error("                 : " + e.getDetail());
            throw e;
        }
    }

    public static class MigrateTestInitializer extends AbstractTestDataInitializer {

        @Override
        public Map<String, String> start() {

            String registryBaseUrl = startRegistryApplication(
                    "quay.io/apicurio/apicurio-registry-mem:latest-release");
            var adapter = new VertXRequestAdapter(VertXAuthFactory.defaultVertx);
            adapter.setBaseUrl(getRegistryV2ApiUrl());
            io.apicurio.registry.rest.client.v2.RegistryClient source = new io.apicurio.registry.rest.client.v2.RegistryClient(
                    adapter);

            try {

                // Warm up until the source registry is ready.
                TestUtils.retry(() -> {
                    source.groups().byGroupId("default").artifacts().get();
                });

                MigrationTestsDataInitializer.initializeMigrateTest(source, this.getRegistryUrl(8081));

            } catch (Exception ex) {
                log.error("Error filling origin registry with data:", ex);
            }

            return Map.of("apicurio.rest.deletion.artifact.enabled", "true");
        }
    }
}