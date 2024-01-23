package io.apicurio.tests.migration;



import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.ArtifactContent;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.apicurio.tests.serdes.apicurio.AvroGenericRecordSchemaFactory;
import io.apicurio.tests.serdes.apicurio.JsonSchemaMsgFactory;
import io.apicurio.tests.utils.AbstractTestDataInitializer;
import io.apicurio.tests.utils.Constants;
import io.kiota.http.vertx.VertXRequestAdapter;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;

import io.apicurio.registry.client.auth.VertXAuthFactory;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusIntegrationTest
@QuarkusTestResource(value = DoNotPreserveIdsImportIT.DoNotPreserveIdsInitializer.class, restrictToAnnotatedClass = true)
@Tag(Constants.MIGRATION)
@Disabled
public class DoNotPreserveIdsImportIT extends ApicurioRegistryBaseIT {

    private static final Logger log = LoggerFactory.getLogger(DataMigrationIT.class);
    public static InputStream doNotPreserveIdsImportDataToImport;
    public static JsonSchemaMsgFactory jsonSchema;
    public static Map<String, String> doNotPreserveIdsImportArtifacts = new HashMap<>();

    @Override
    public void cleanArtifacts() throws Exception {
        //Don't clean up
    }

    @Test
    public void testDoNotPreserveIdsImport() throws Exception {
        var adapter = new VertXRequestAdapter(VertXAuthFactory.defaultVertx);
        adapter.setBaseUrl(ApicurioRegistryBaseIT.getRegistryV3ApiUrl());
        RegistryClient dest = new RegistryClient(adapter);

        // Fill the destination registry with data (Avro content is inserted first to ensure that the content IDs are different)
        for (int idx = 0; idx < 15; idx++) {
            AvroGenericRecordSchemaFactory avroSchema = new AvroGenericRecordSchemaFactory(List.of("a" + idx));
            String artifactId = "avro-" + idx + "-" + UUID.randomUUID().toString(); // Artifact ids need to be different we do not support identical artifact ids
            String content = IoUtil.toString(avroSchema.generateSchemaStream());
            ArtifactContent artifactContent = new ArtifactContent();
            artifactContent.setContent(content);
            var amd = dest.groups().byGroupId("testDoNotPreserveIdsImport").artifacts().post(artifactContent, config -> {
                        config.headers.add("X-Registry-ArtifactId", artifactId);
                    });
            retry(() -> dest.ids().globalIds().byGlobalId(amd.getGlobalId()));
            doNotPreserveIdsImportArtifacts.put("testDoNotPreserveIdsImport:" + artifactId, content);
        }

        for (int idx = 0; idx < 50; idx++) {
            String artifactId = idx + "-" + UUID.randomUUID().toString(); // Artifact ids need to be different we do not support identical artifact ids
            String content = IoUtil.toString(jsonSchema.getSchemaStream());
            ArtifactContent artifactContent = new ArtifactContent();
            artifactContent.setContent(content);
            var amd = dest.groups().byGroupId("testDoNotPreserveIdsImport").artifacts().post(artifactContent, config -> {
                config.headers.add("X-Registry-ArtifactId", artifactId);
            });
            retry(() -> dest.ids().globalIds().byGlobalId(amd.getGlobalId()));
            doNotPreserveIdsImportArtifacts.put("testDoNotPreserveIdsImport:" + artifactId, content);
        }

        // Import the data
        var importReq = dest.admin().importEscaped().toPostRequestInformation(doNotPreserveIdsImportDataToImport, config -> {
            config.headers.add("X-Registry-Preserve-GlobalId", "false");
            config.headers.add("X-Registry-Preserve-ContentId", "false");
        });
        importReq.headers.replace("Content-Type", Set.of("application/zip"));
        adapter.sendPrimitive(importReq, new HashMap<>(), Void.class);



        // Check that the import was successful
        retry(() -> {
            for (var entry : doNotPreserveIdsImportArtifacts.entrySet()) {
                String groupId = entry.getKey().split(":")[0];
                String artifactId = entry.getKey().split(":")[1];
                String content = entry.getValue();
                var registryContent = dest.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get();
                assertNotNull(registryContent);
                assertEquals(content, IoUtil.toString(registryContent));
            }
        });
    }

    public static class DoNotPreserveIdsInitializer extends AbstractTestDataInitializer {

        @Override
        public Map<String, String> start() {

            String registryBaseUrl = startRegistryApplication("quay.io/apicurio/apicurio-registry-mem:2.4.14.Final");
            var adapter = new VertXRequestAdapter(VertXAuthFactory.defaultVertx);
            adapter.setBaseUrl(registryBaseUrl);
            RegistryClient source = new RegistryClient(adapter);

            try {
                //Warm up until the source registry is ready.
                TestUtils.retry(() -> {
                    source.groups().byGroupId("default").artifacts().get();
                });

                MigrationTestsDataInitializer.initializeDoNotPreserveIdsImport(source, getRegistryUrl(8081));

            } catch (Exception ex) {
                log.error("Error filling origin registry with data:", ex);
            }

            return Collections.emptyMap();
        }
    }
}
