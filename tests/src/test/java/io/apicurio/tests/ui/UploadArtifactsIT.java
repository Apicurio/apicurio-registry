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

import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtensionContext;

import io.apicurio.registry.client.RegistryService;
import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.ConcurrentUtil;
import io.apicurio.registry.utils.tests.RegistryServiceTest;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.BaseIT;
import io.apicurio.tests.Constants;
import io.apicurio.tests.selenium.SeleniumChrome;
import io.apicurio.tests.selenium.SeleniumProvider;
import io.apicurio.tests.selenium.resources.ArtifactListItem;

import static io.apicurio.tests.Constants.ACCEPTANCE;
import static io.apicurio.tests.Constants.UI;

@Tag(UI)
@SeleniumChrome
public class UploadArtifactsIT extends BaseIT {

    SeleniumProvider selenium = SeleniumProvider.getInstance();

    @AfterEach
    void cleanArtifacts(RegistryService service, ExtensionContext ctx) {
        if (ctx.getExecutionException().isPresent()) {
            LOGGER.error("", ctx.getExecutionException().get());
        }
    }

    public void doTest(RegistryService service, String resource, ArtifactType type, String artifactId, boolean autodetect) throws Exception {
        assertNotNull(type);

        String content = resourceToString("artifactTypes/" + resource);

        RegistryUITester page = new RegistryUITester(selenium);
        page.openWebPage();
        String webArtifactId = page.uploadArtifact(artifactId, autodetect ? null : type, content);

        if (artifactId != null) {
            assertEquals(artifactId, webArtifactId);
        }

        assertEquals(1, service.listArtifacts().size());

        ArtifactMetaData meta = TestUtils.retry(() -> service.getArtifactMetaData(webArtifactId));
        assertEquals(type, meta.getType());
    }

//    @RegistryServiceTest(localOnly = false)
//    void testAvro(RegistryService service) {
//        doTest(service, "avro/multi-field_v1.json", ArtifactType.AVRO, null, false);
//    }

    @RegistryServiceTest
    @Tag(ACCEPTANCE)
    void testProtobuf(RegistryService service) throws Exception {
        doTest(service, "protobuf/tutorial_v1.proto", ArtifactType.PROTOBUF, null, false);
    }

    @RegistryServiceTest
    void testJsonSchema(RegistryService service) throws Exception {
        doTest(service, "jsonSchema/person_v1.json", ArtifactType.JSON, null, false);
    }

    @RegistryServiceTest
    void testKafkaConnect(RegistryService service) throws Exception {
        doTest(service, "kafkaConnect/simple_v1.json", ArtifactType.KCONNECT, null, false);
    }

    @RegistryServiceTest
    void testOpenApi30(RegistryService service) throws Exception {
        doTest(service, "openapi/3.0-petstore_v1.json", ArtifactType.OPENAPI, null, false);
    }

    @RegistryServiceTest
    void testAsyncApi(RegistryService service) throws Exception {
        doTest(service, "asyncapi/2.0-streetlights_v1.json", ArtifactType.ASYNCAPI, null, false);
    }

    @RegistryServiceTest
    void testGraphQL(RegistryService service) throws Exception {
        doTest(service, "graphql/swars_v1.graphql", ArtifactType.GRAPHQL, null, false);
    }

    //auto-detect, kafka connect excluded because it's known it does not work

//    @RegistryServiceTest(localOnly = false)
//    void testAvroAutoDetect(RegistryService service) throws Exception {
//        doTest(service, "avro/multi-field_v1.json", null, null);
//    }

    @RegistryServiceTest
    void testProtobufAutoDetect(RegistryService service) throws Exception {
        doTest(service, "protobuf/tutorial_v1.proto", ArtifactType.PROTOBUF, null, true);
    }

    @RegistryServiceTest
    void testJsonSchemaAutoDetect(RegistryService service) throws Exception {
        doTest(service, "jsonSchema/person_v1.json", ArtifactType.JSON, null, true);
    }

    @RegistryServiceTest
    void testOpenApi30AutoDetect(RegistryService service) throws Exception {
        doTest(service, "openapi/3.0-petstore_v1.json", ArtifactType.OPENAPI, null, true);
    }

    @RegistryServiceTest
    void testAsyncApiAutoDetect(RegistryService service) throws Exception {
        doTest(service, "asyncapi/2.0-streetlights_v1.json", ArtifactType.ASYNCAPI, null, true);
    }

    @RegistryServiceTest
    void testGraphQLAutoDetect(RegistryService service) throws Exception {
        doTest(service, "graphql/swars_v1.graphql", ArtifactType.GRAPHQL, null, true);
    }

    //provide artifact id

    @RegistryServiceTest
    void testSetArtifactId(RegistryService service) throws Exception {
        doTest(service, "openapi/3.0-petstore_v1.json", ArtifactType.OPENAPI, "testArtifactIdOpenApi", false);
    }

    @RegistryServiceTest
    void testSetArtifactIdAndAutodetect(RegistryService service) throws Exception {
        doTest(service, "openapi/3.0-petstore_v1.json", ArtifactType.OPENAPI, "testArtifactIdOpenApi", true);
    }

    @RegistryServiceTest
    void testCreateViaApi(RegistryService service) throws Exception {

        RegistryUITester page = new RegistryUITester(selenium);
        page.openWebPage();

        List<ArtifactListItem> webArtifacts = page.getArtifactsList();
        assertEquals(0, webArtifacts.size());

        String content = resourceToString("artifactTypes/" + "protobuf/tutorial_v1.proto");
        ArtifactMetaData meta = ConcurrentUtil.result(
                service.createArtifact(ArtifactType.PROTOBUF, null, null, new ByteArrayInputStream(content.getBytes())));

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
