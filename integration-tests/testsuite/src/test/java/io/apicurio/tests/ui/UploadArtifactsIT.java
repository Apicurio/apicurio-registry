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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.ApicurioV2BaseIT;
import io.apicurio.tests.common.Constants;
import io.apicurio.tests.selenium.SeleniumChrome;
import io.apicurio.tests.selenium.SeleniumProvider;
import io.apicurio.tests.selenium.resources.ArtifactListItem;
import io.apicurio.tests.ui.pages.ArtifactDetailsPage;
import io.apicurio.tests.ui.pages.UploadArtifactDialog;

@Tag(Constants.UI)
@SeleniumChrome
public class UploadArtifactsIT extends ApicurioV2BaseIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(UploadArtifactsIT.class);


    SeleniumProvider selenium = SeleniumProvider.getInstance();

    @AfterEach
    void logIfError(ExtensionContext ctx) {
        if (ctx.getExecutionException().isPresent()) {
            LOGGER.error("", ctx.getExecutionException().get());
        }
    }

    public void doTestFromURL(RegistryClient client, String fromURL, String type, String artifactId, boolean autodetect) throws Exception {
        doTest(client, null, type, artifactId, autodetect, fromURL);
    }

    public void doTest(RegistryClient client, String resource, String type, String artifactId, boolean autodetect) throws Exception {
        doTest(client, resource, type, artifactId, autodetect, null);
    }

    public void doTest(RegistryClient client, String resource, String type, String artifactId, boolean autodetect, String fromURL) throws Exception {
        String groupId = UploadArtifactsIT.class.getName();

        assertNotNull(type);

        String content = (fromURL == null) ? resourceToString("artifactTypes/" + resource) : null;

        RegistryUITester page = new RegistryUITester(selenium);
        page.openWebPage();
        String webArtifactId = (fromURL == null) ? page.uploadArtifact(groupId, artifactId, autodetect ? null : type, content) :
                page.uploadArtifactFromURL(groupId, artifactId, autodetect ? null : type, fromURL);

        if (artifactId != null) {
            assertEquals(artifactId, webArtifactId);
        }

        page.goBackToArtifactsList();

        waitForArtifactWeb(page, groupId, webArtifactId);

        ArtifactMetaData meta = TestUtils.retry(() -> client.getArtifactMetaData(groupId, webArtifactId));
        assertEquals(type, meta.getType());
    }

//    @RegistryServiceTest(localOnly = false)
//    void testAvro(RegistryRestClient client) {
//        doTest(client, "avro/multi-field_v1.json", ArtifactType.AVRO, null, false);
//    }

    @Test
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

    @Test
    void testOpenApiFromURL() throws Exception {
        String testUrl = SeleniumProvider.getInstance().getUiUrl().replace("/ui", "/api-specifications/registry/v2/openapi.json");
        doTestFromURL(
                registryClient,
                testUrl,
                ArtifactType.OPENAPI, null, false);
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
            registryClient.createArtifact(null, null, ArtifactType.PROTOBUF, new ByteArrayInputStream(content.getBytes()));

        ArtifactListItem webArtifact = waitForArtifactWeb(page, null, meta.getId());

        assertEquals(meta.getId(), webArtifact.getArtifactId());

    }

    @Test
    void testSpecialCharactersAccepted() throws Exception {

        String artifactId = "._:-'`?0=)(/&$!<>,;,:";

        String content = resourceToString("artifactTypes/" + "jsonSchema/person_v1.json");

        RegistryUITester page = new RegistryUITester(selenium);
        page.openWebPage();
        String webArtifactId = page.uploadArtifact(null, artifactId, ArtifactType.JSON, content);

        assertEquals(artifactId, webArtifactId);

        assertEquals(1, registryClient.listArtifactsInGroup(null).getCount());
        page.goBackToArtifactsList();

        ArtifactListItem webArtifact = waitForArtifactWeb(page, null, webArtifactId);

        assertEquals(artifactId, webArtifact.getArtifactId());

        ArtifactDetailsPage artifactPage = page.getArtifactsListPage().openArtifactDetailsPage(null, artifactId);

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

    private ArtifactListItem waitForArtifactWeb(RegistryUITester page, String groupId, String webArtifactId) throws Exception {
        return TestUtils.retry(() -> {
            selenium.refreshPage();
            List<ArtifactListItem> webArtifacts = page.getArtifactsList();
            return webArtifacts.stream()
                    .filter(item -> item.matches(groupId, webArtifactId))
                    .findFirst()
                    .orElseThrow();
        });
    }

}
