package io.apicurio.tests.migration;

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.RegistryClientFactory;
import io.apicurio.registry.rest.v2.beans.ArtifactReference;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.apicurio.tests.utils.AbstractTestDataInitializer;
import io.apicurio.tests.utils.Constants;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.AfterEach;
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

import static io.apicurio.tests.migration.MigrationTestsDataInitializer.matchesReferences;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Carles Arnal
 */
@QuarkusIntegrationTest
@QuarkusTestResource(value = DataMigrationIT.MigrateTestInitializer.class, restrictToAnnotatedClass = true)
@Tag(Constants.MIGRATION)
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
        RegistryClient dest = RegistryClientFactory.create(ApicurioRegistryBaseIT.getRegistryV2ApiUrl());
        dest.importData(migrateDataToImport);

        retry(() -> {
            for (long gid : migrateGlobalIds) {
                dest.getContentByGlobalId(gid);
                if (migrateReferencesMap.containsKey(gid)) {
                    List<ArtifactReference> srcReferences = migrateReferencesMap.get(gid);
                    List<ArtifactReference> destReferences = dest.getArtifactReferencesByGlobalId(gid);
                    assertTrue(matchesReferences(srcReferences, destReferences));
                }
            }
            assertEquals("SYNTAX_ONLY", dest.getArtifactRuleConfig("migrateTest", "avro-0", RuleType.VALIDITY).getConfig());
            assertEquals("BACKWARD", dest.getGlobalRuleConfig(RuleType.COMPATIBILITY).getConfig());
        });
    }

    @AfterEach
    public void tearDownRegistries() throws IOException {
    }

    public static class MigrateTestInitializer extends AbstractTestDataInitializer {

        @Override
        public Map<String, String> start() {

            String registryBaseUrl = startRegistryApplication("quay.io/apicurio/apicurio-registry-mem:latest-release");
            RegistryClient source = RegistryClientFactory.create(registryBaseUrl);

            try {

                //Warm up until the source registry is ready.
                TestUtils.retry(() -> {
                    source.listArtifactsInGroup(null);
                });

                MigrationTestsDataInitializer.initializeMigrateTest(source);

            } catch (Exception ex) {
                log.error("Error filling origin registry with data:", ex);
            }

            return Collections.emptyMap();
        }
    }
}