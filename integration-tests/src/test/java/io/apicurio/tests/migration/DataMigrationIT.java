package io.apicurio.tests.migration;



import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.ArtifactReference;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.apicurio.tests.utils.AbstractTestDataInitializer;
import io.apicurio.tests.utils.Constants;
import io.kiota.http.vertx.VertXRequestAdapter;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.apicurio.registry.client.auth.VertXAuthFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.apicurio.tests.migration.MigrationTestsDataInitializer.matchesReferences;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusIntegrationTest
@QuarkusTestResource(value = DataMigrationIT.MigrateTestInitializer.class, restrictToAnnotatedClass = true)
@Tag(Constants.MIGRATION)
@Disabled
public class DataMigrationIT extends ApicurioRegistryBaseIT {

    private static final Logger log = LoggerFactory.getLogger(DataMigrationIT.class);

    public static InputStream migrateDataToImport;
    public static HashMap<Long, List<ArtifactReference>> migrateReferencesMap = new HashMap<>();
    public static List<Long> migrateGlobalIds = new ArrayList<>();
    public static Map<String, String> doNotPreserveIdsImportArtifacts = new HashMap<>();

    /**
     * The data required for this test is initialized by MigrationTestsDataInitializer.initializeMigrateTest(RegistryClient)
     *
     * @throws Exception
     */
    @Test
    public void migrate() throws Exception {
        var adapter = new VertXRequestAdapter(VertXAuthFactory.defaultVertx);
        adapter.setBaseUrl(ApicurioRegistryBaseIT.getRegistryV3ApiUrl());
        RegistryClient dest = new RegistryClient(adapter);

        var importReq = dest.admin().importEscaped().toPostRequestInformation(migrateDataToImport);
        importReq.headers.replace("Content-Type", Set.of("application/zip"));
        adapter.sendPrimitive(importReq, new HashMap<>(), Void.class);

        retry(() -> {
            for (long gid : migrateGlobalIds) {
                dest.ids().globalIds().byGlobalId(gid).get();
                if (migrateReferencesMap.containsKey(gid)) {
                    List<ArtifactReference> srcReferences = migrateReferencesMap.get(gid);
                    List<ArtifactReference> destReferences = dest.ids().globalIds().byGlobalId(gid).references().get();
                    assertTrue(matchesReferences(srcReferences, destReferences));
                }
            }
            assertEquals("SYNTAX_ONLY", dest.groups().byGroupId("migrateTest").artifacts().byArtifactId("avro-0").rules().byRule(RuleType.VALIDITY.name()).get().getConfig());
            assertEquals("BACKWARD", dest.admin().rules().byRule(RuleType.COMPATIBILITY.name()).get().getConfig());
        });
    }

    @AfterEach
    public void tearDownRegistries() throws IOException {
    }

    public static class MigrateTestInitializer extends AbstractTestDataInitializer {

        @Override
        public Map<String, String> start() {
            // TODO we will need to change this to 3.0.0 whenever that is released!
            String registryBaseUrl = startRegistryApplication("quay.io/apicurio/apicurio-registry:latest-snapshot");
            var adapter = new VertXRequestAdapter(VertXAuthFactory.defaultVertx);
            adapter.setBaseUrl(registryBaseUrl);
            RegistryClient source = new RegistryClient(adapter);

            try {

                //Warm up until the source registry is ready.
                TestUtils.retry(() -> {
                    source.groups().byGroupId("default").artifacts().get();
                });

                MigrationTestsDataInitializer.initializeMigrateTest(source, this.getRegistryUrl(8081));

            } catch (Exception ex) {
                log.error("Error filling origin registry with data:", ex);
            }

            return Collections.emptyMap();
        }
    }
}