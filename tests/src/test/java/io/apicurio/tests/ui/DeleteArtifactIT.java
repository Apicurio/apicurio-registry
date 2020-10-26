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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtensionContext;

import io.apicurio.registry.client.RegistryRestClient;
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
public class DeleteArtifactIT extends BaseIT {

    SeleniumProvider selenium = SeleniumProvider.getInstance();

    @AfterEach
    void cleanArtifacts(RegistryRestClient client, ExtensionContext ctx) {
        if (ctx.getExecutionException().isPresent()) {
            LOGGER.error("", ctx.getExecutionException().get());
        }
    }

    @RegistryRestClientTest
    void testDeleteArtifacts(RegistryRestClient client) throws Exception {
        RegistryUITester page = new RegistryUITester(selenium);
        page.openWebPage();

        String content1 = resourceToString("artifactTypes/" + "protobuf/tutorial_v1.proto");
        String artifactId1 = page.uploadArtifact(null, ArtifactType.PROTOBUF, content1);
        assertEquals(1, client.listArtifacts().size());
        page.goBackToArtifactsList();

        String content2 = resourceToString("artifactTypes/" + "jsonSchema/person_v1.json");
        String artifactId2 = page.uploadArtifact(null, ArtifactType.JSON, content2);
        assertEquals(2, client.listArtifacts().size());
        page.goBackToArtifactsList();

        List<ArtifactListItem> webArtifacts = page.getArtifactsList();
        assertEquals(2, webArtifacts.size());

        webArtifacts.removeIf(artifact -> {
            return artifact.getArtifactId().equals(artifactId1) || artifact.getArtifactId().equals(artifactId2);
        });
        assertTrue(webArtifacts.isEmpty());

        page.deleteArtifact(artifactId1);

        TestUtils.waitFor("Artifacts list updated", Constants.POLL_INTERVAL, Duration.ofSeconds(60).toMillis(), () -> {
            try {
                return page.getArtifactsList().size() == 1;
            } catch (Exception e) {
                LOGGER.error("", e);
                return false;
            }
        });
        webArtifacts = page.getArtifactsList();
        assertEquals(artifactId2, webArtifacts.get(0).getArtifactId());

        page.deleteArtifact(artifactId2);

        TestUtils.waitFor("Artifacts list updated", Constants.POLL_INTERVAL, Duration.ofSeconds(60).toMillis(), () -> {
            try {
                return page.getArtifactsList().size() == 0;
            } catch (Exception e) {
                LOGGER.error("", e);
                return false;
            }
        });
    }

    @RegistryRestClientTest
    void testDeleteViaApi(RegistryRestClient client) throws Exception {
        RegistryUITester page = new RegistryUITester(selenium);
        page.openWebPage();

        String content1 = resourceToString("artifactTypes/" + "protobuf/tutorial_v1.proto");
        String artifactId1 = page.uploadArtifact(null, ArtifactType.PROTOBUF, content1);
        assertEquals(1, client.listArtifacts().size());
        page.goBackToArtifactsList();

        String content2 = resourceToString("artifactTypes/" + "jsonSchema/person_v1.json");
        String artifactId2 = page.uploadArtifact(null, ArtifactType.JSON, content2);
        assertEquals(2, client.listArtifacts().size());
        page.goBackToArtifactsList();

        List<ArtifactListItem> webArtifacts = page.getArtifactsList();
        assertEquals(2, webArtifacts.size());

        webArtifacts.removeIf(artifact -> {
            return artifact.getArtifactId().equals(artifactId1) || artifact.getArtifactId().equals(artifactId2);
        });
        assertTrue(webArtifacts.isEmpty());

        client.deleteArtifact(artifactId1);
        TestUtils.assertWebError(404, () -> client.getArtifactMetaData(artifactId1), true);

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
        assertEquals(artifactId2, webArtifacts.get(0).getArtifactId());

        client.deleteArtifact(artifactId2);
        TestUtils.assertWebError(404, () -> client.getArtifactMetaData(artifactId2), true);

        selenium.refreshPage();
        TestUtils.waitFor("Artifacts list updated", Constants.POLL_INTERVAL, Constants.TIMEOUT_GLOBAL, () -> {
            try {
                return page.getArtifactsList().size() == 0;
            } catch (Exception e) {
                LOGGER.error("", e);
                return false;
            }
        });
    }

}
