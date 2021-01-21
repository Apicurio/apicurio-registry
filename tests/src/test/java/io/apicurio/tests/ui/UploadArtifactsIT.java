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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.openqa.selenium.WebElement;

import io.apicurio.registry.client.RegistryRestClient;
import io.apicurio.registry.rest.v1.beans.ArtifactMetaData;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.BaseIT;
import io.apicurio.tests.Constants;
import io.apicurio.tests.selenium.SeleniumChrome;
import io.apicurio.tests.selenium.SeleniumProvider;
import io.apicurio.tests.selenium.resources.ArtifactListItem;
import io.apicurio.tests.ui.pages.ArtifactDetailsPage;
import io.apicurio.tests.ui.pages.UploadArtifactDialog;

@Tag(UI)
@SeleniumChrome
public class UploadArtifactsIT extends BaseIT {

    SeleniumProvider selenium = SeleniumProvider.getInstance();

    @AfterEach
    void logIfError(ExtensionContext ctx) {
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

    @Test
    @Tag(ACCEPTANCE)
    void testProtobuf() throws Exception {
        doTest(registryClient, "protobuf/tutorial_v1.proto", ArtifactType.PROTOBUF, null, false);
    }

    @Test
    void testJsonSchema() throws Exception {
        doTest(registryClient, "jsonSchema/person_v1.json", ArtifactType.JSON, null, false);
    }

    @Test
    void testKafkaConnect() throws Exception {
        doTest(registryClient, "kafkaConnect/simple_v1.json", ArtifactType.KCONNECT, null, false);
    }

    @Test
    void testOpenApi30() throws Exception {
        doTest(registryClient, "openapi/3.0-petstore_v1.json", ArtifactType.OPENAPI, null, false);
    }

    @Test
    void testAsyncApi() throws Exception {
        doTest(registryClient, "asyncapi/2.0-streetlights_v1.json", ArtifactType.ASYNCAPI, null, false);
    }

    @Test
    void testGraphQL() throws Exception {
        doTest(registryClient, "graphql/swars_v1.graphql", ArtifactType.GRAPHQL, null, false);
    }

    //auto-detect, kafka connect excluded because it's known it does not work

//    @RegistryServiceTest(localOnly = false)
//    void testAvroAutoDetect(RegistryRestClient client) throws Exception {
//        doTest(client, "avro/multi-field_v1.json", null, null);
//    }

    @Test
    void testProtobufAutoDetect() throws Exception {
        doTest(registryClient, "protobuf/tutorial_v1.proto", ArtifactType.PROTOBUF, null, true);
    }

    @Test
    void testJsonSchemaAutoDetect() throws Exception {
        doTest(registryClient, "jsonSchema/person_v1.json", ArtifactType.JSON, null, true);
    }

    @Test
    void testOpenApi30AutoDetect() throws Exception {
        doTest(registryClient, "openapi/3.0-petstore_v1.json", ArtifactType.OPENAPI, null, true);
    }

    @Test
    void testAsyncApiAutoDetect() throws Exception {
        doTest(registryClient, "asyncapi/2.0-streetlights_v1.json", ArtifactType.ASYNCAPI, null, true);
    }

    @Test
    void testGraphQLAutoDetect() throws Exception {
        doTest(registryClient, "graphql/swars_v1.graphql", ArtifactType.GRAPHQL, null, true);
    }

    //provide artifact id

    @Test
    void testSetArtifactId() throws Exception {
        doTest(registryClient, "openapi/3.0-petstore_v1.json", ArtifactType.OPENAPI, "testArtifactIdOpenApi", false);
    }

    @Test
    void testSetArtifactIdAndAutodetect() throws Exception {
        doTest(registryClient, "openapi/3.0-petstore_v1.json", ArtifactType.OPENAPI, "testArtifactIdOpenApi", true);
    }

    @Test
    void testCreateViaApi() throws Exception {

        RegistryUITester page = new RegistryUITester(selenium);
        page.openWebPage();

        List<ArtifactListItem> webArtifacts = page.getArtifactsList();
        assertEquals(0, webArtifacts.size());

        String content = resourceToString("artifactTypes/" + "protobuf/tutorial_v1.proto");
        ArtifactMetaData meta =
            registryClient.createArtifact(null, ArtifactType.PROTOBUF, new ByteArrayInputStream(content.getBytes()));

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

    @Test
    void testSpecialCharactersAccepted() throws Exception {

        String artifactId = "._:-'`?0=)(/&$!<>,;,:";

        String content = resourceToString("artifactTypes/" + "jsonSchema/person_v1.json");

        RegistryUITester page = new RegistryUITester(selenium);
        page.openWebPage();
        String webArtifactId = page.uploadArtifact(artifactId, ArtifactType.JSON, content);

        assertEquals(artifactId, webArtifactId);

        assertEquals(1, registryClient.listArtifacts().size());
        page.goBackToArtifactsList();

        selenium.refreshPage();
        TestUtils.waitFor("Artifacts list updated", Constants.POLL_INTERVAL, Constants.TIMEOUT_GLOBAL, () -> {
            try {
                return page.getArtifactsList().size() == 1;
            } catch (Exception e) {
                LOGGER.error("", e);
                return false;
            }
        });

        List<ArtifactListItem> webArtifacts = page.getArtifactsList();
        assertEquals(artifactId, webArtifacts.get(0).getArtifactId());


        ArtifactDetailsPage artifactPage = page.getArtifactsListPage().openArtifactDetailsPage(artifactId);

        artifactPage.verifyIsOpen();

    }

    @Test
    void testForbiddenSpecialCharacters() throws Exception {
        forbiddenSpecialCharactersTest("ab%c");
        forbiddenSpecialCharactersTest("._:-ç'`¡¿?0=)(/&$·!ªº<>,;,:");
    }


    void forbiddenSpecialCharactersTest(String artifactId) throws Exception {

        RegistryUITester page = new RegistryUITester(selenium);
        page.openWebPage();

        UploadArtifactDialog dialog = page.openUploadArtifactDialog();

        dialog.fillArtifactId(artifactId);

        WebElement inputElement = dialog.getArtifactIdInput();

        String invalid = inputElement.getAttribute("aria-invalid");

        selenium.takeScreenShot();

        assertEquals("true", invalid, "UI is not marking " + artifactId + " artifact id as invalid");

    }

}
