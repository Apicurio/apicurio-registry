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

package io.apicurio.tests.migration;

import com.microsoft.kiota.authentication.AnonymousAuthenticationProvider;
import com.microsoft.kiota.http.OkHttpRequestAdapter;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.ArtifactContent;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.apicurio.tests.serdes.apicurio.AvroGenericRecordSchemaFactory;
import io.apicurio.tests.serdes.apicurio.JsonSchemaMsgFactory;
import io.apicurio.tests.utils.AbstractTestDataInitializer;
import io.apicurio.tests.utils.Constants;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
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
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Carles Arnal
 */
@QuarkusIntegrationTest
@QuarkusTestResource(value = DoNotPreserveIdsImportIT.DoNotPreserveIdsInitializer.class, restrictToAnnotatedClass = true)
@Tag(Constants.MIGRATION)
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
        var adapter = new OkHttpRequestAdapter(new AnonymousAuthenticationProvider());
        adapter.setBaseUrl(ApicurioRegistryBaseIT.getRegistryV2ApiUrl());
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
                    }).get(3, TimeUnit.SECONDS);
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
            }).get(3, TimeUnit.SECONDS);
            retry(() -> dest.ids().globalIds().byGlobalId(amd.getGlobalId()));
            doNotPreserveIdsImportArtifacts.put("testDoNotPreserveIdsImport:" + artifactId, content);
        }

        // Import the data
        var importReq = dest.admin().importEscaped().toPostRequestInformation(doNotPreserveIdsImportDataToImport, config -> {
            config.headers.add("X-Registry-Preserve-GlobalId", "false");
            config.headers.add("X-Registry-Preserve-ContentId", "false");
        });
        importReq.headers.replace("Content-Type", Set.of("application/zip"));
        adapter.sendPrimitiveAsync(importReq, Void.class, new HashMap<>());



        // Check that the import was successful
        retry(() -> {
            for (var entry : doNotPreserveIdsImportArtifacts.entrySet()) {
                String groupId = entry.getKey().split(":")[0];
                String artifactId = entry.getKey().split(":")[1];
                String content = entry.getValue();
                var registryContent = dest.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get().get(3, TimeUnit.SECONDS);
                assertNotNull(registryContent);
                assertEquals(content, IoUtil.toString(registryContent));
            }
        });
    }

    public static class DoNotPreserveIdsInitializer extends AbstractTestDataInitializer {

        @Override
        public Map<String, String> start() {

            String registryBaseUrl = startRegistryApplication("quay.io/apicurio/apicurio-registry-mem:latest-release");
            var adapter = new OkHttpRequestAdapter(new AnonymousAuthenticationProvider());
            adapter.setBaseUrl(registryBaseUrl);
            RegistryClient source = new RegistryClient(adapter);

            try {
                //Warm up until the source registry is ready.
                TestUtils.retry(() -> {
                    source.groups().byGroupId("default").artifacts().get().get(3, TimeUnit.SECONDS);
                });

                MigrationTestsDataInitializer.initializeDoNotPreserveIdsImport(source, getRegistryUrl(8081));

            } catch (Exception ex) {
                log.error("Error filling origin registry with data:", ex);
            }

            return Collections.emptyMap();
        }
    }
}
