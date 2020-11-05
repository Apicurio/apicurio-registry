/*
 * Copyright 2020 Red Hat
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
package io.apicurio.tests.ui;

import static io.apicurio.tests.Constants.UI;
import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtensionContext;

import io.apicurio.registry.client.RegistryRestClient;
import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.tests.RegistryRestClientTest;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.BaseIT;
import io.apicurio.tests.Constants;
import io.apicurio.tests.selenium.SeleniumChrome;
import io.apicurio.tests.selenium.SeleniumProvider;
import io.apicurio.tests.selenium.resources.ArtifactListItem;

@Tag(UI)
@SeleniumChrome
public class UploadArtifactsIT extends BaseIT {

    SeleniumProvider selenium = SeleniumProvider.getInstance();

    @AfterEach
    void cleanArtifacts(RegistryRestClient client, ExtensionContext ctx) {
        if (ctx.getExecutionException().isPresent()) {
            LOGGER.error("", ctx.getExecutionException().get());
        }
    }

    public void doTest(RegistryRestClient client, String resource, ArtifactType type, String artifactId, boolean autodetect) throws Exception {
        assertNotNull(type);

        String content = resourceToString("artifactTypes/" + resource);

        RegistryUITester page = new RegistryUITester(selenium);
        page.openWebPage();
        String webArtifactId = page.uploadArtifact(artifactId, autodetect ? null : type, content);

        if (artifactId != null) {
            assertEquals(artifactId, webArtifactId);
        }

        assertEquals(1, client.listArtifacts().size());

        ArtifactMetaData meta = TestUtils.retry(() -> client.getArtifactMetaData(webArtifactId));
        assertEquals(type, meta.getType());
    }

//    @RegistryServiceTest(localOnly = false)
//    void testAvro(RegistryRestClient client) {
//        doTest(client, "avro/multi-field_v1.json", ArtifactType.AVRO, null, false);
//    }

    @RegistryRestClientTest
    @Tag(ACCEPTANCE)
    void testProtobuf(RegistryRestClient client) throws Exception {
        doTest(client, "protobuf/tutorial_v1.proto", ArtifactType.PROTOBUF, null, false);
    }

    @RegistryRestClientTest
    void testJsonSchema(RegistryRestClient client) throws Exception {
        doTest(client, "jsonSchema/person_v1.json", ArtifactType.JSON, null, false);
    }

    @RegistryRestClientTest
    void testKafkaConnect(RegistryRestClient client) throws Exception {
        doTest(client, "kafkaConnect/simple_v1.json", ArtifactType.KCONNECT, null, false);
    }

    @RegistryRestClientTest
    void testOpenApi30(RegistryRestClient client) throws Exception {
        doTest(client, "openapi/3.0-petstore_v1.json", ArtifactType.OPENAPI, null, false);
    }

    @RegistryRestClientTest
    void testAsyncApi(RegistryRestClient client) throws Exception {
        doTest(client, "asyncapi/2.0-streetlights_v1.json", ArtifactType.ASYNCAPI, null, false);
    }

    @RegistryRestClientTest
    void testGraphQL(RegistryRestClient client) throws Exception {
        doTest(client, "graphql/swars_v1.graphql", ArtifactType.GRAPHQL, null, false);
    }

    //auto-detect, kafka connect excluded because it's known it does not work

//    @RegistryServiceTest(localOnly = false)
//    void testAvroAutoDetect(RegistryRestClient client) throws Exception {
//        doTest(client, "avro/multi-field_v1.json", null, null);
//    }

    @RegistryRestClientTest
    void testProtobufAutoDetect(RegistryRestClient client) throws Exception {
        doTest(client, "protobuf/tutorial_v1.proto", ArtifactType.PROTOBUF, null, true);
    }

    @RegistryRestClientTest
    void testJsonSchemaAutoDetect(RegistryRestClient client) throws Exception {
        doTest(client, "jsonSchema/person_v1.json", ArtifactType.JSON, null, true);
    }

    @RegistryRestClientTest
    void testOpenApi30AutoDetect(RegistryRestClient client) throws Exception {
        doTest(client, "openapi/3.0-petstore_v1.json", ArtifactType.OPENAPI, null, true);
    }

    @RegistryRestClientTest
    void testAsyncApiAutoDetect(RegistryRestClient client) throws Exception {
        doTest(client, "asyncapi/2.0-streetlights_v1.json", ArtifactType.ASYNCAPI, null, true);
    }

    @RegistryRestClientTest
    void testGraphQLAutoDetect(RegistryRestClient client) throws Exception {
        doTest(client, "graphql/swars_v1.graphql", ArtifactType.GRAPHQL, null, true);
    }

    //provide artifact id

    @RegistryRestClientTest
    void testSetArtifactId(RegistryRestClient client) throws Exception {
        doTest(client, "openapi/3.0-petstore_v1.json", ArtifactType.OPENAPI, "testArtifactIdOpenApi", false);
    }

    @RegistryRestClientTest
    void testSetArtifactIdAndAutodetect(RegistryRestClient client) throws Exception {
        doTest(client, "openapi/3.0-petstore_v1.json", ArtifactType.OPENAPI, "testArtifactIdOpenApi", true);
    }

    @RegistryRestClientTest
    void testCreateViaApi(RegistryRestClient client) throws Exception {

        RegistryUITester page = new RegistryUITester(selenium);
        page.openWebPage();

        List<ArtifactListItem> webArtifacts = page.getArtifactsList();
        assertEquals(0, webArtifacts.size());

        String content = resourceToString("artifactTypes/" + "protobuf/tutorial_v1.proto");
        ArtifactMetaData meta = 
                client.createArtifact(null, ArtifactType.PROTOBUF, new ByteArrayInputStream(content.getBytes()));

        selenium.refreshPage();
        TestUtils.waitFor("Artifacts list updated", Constants.POLL_INTERVAL, Constants.TIMEOUT_GLOBAL, () -> {
            try {
                return page.getArtifactsList().size() == 1;
            } catch (Exception e) {
                LOGGER.error("", e);
                return false;
            }
        });

        webArtifacts = page.getArtifactsList();
        assertEquals(meta.getId(), webArtifacts.get(0).getArtifactId());
    }

}
