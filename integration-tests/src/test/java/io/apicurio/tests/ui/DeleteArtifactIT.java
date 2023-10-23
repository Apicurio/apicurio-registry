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

import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.apicurio.tests.ui.selenium.SeleniumChrome;
import io.apicurio.tests.ui.selenium.SeleniumProvider;
import io.apicurio.tests.ui.selenium.resources.ArtifactListItem;
import io.apicurio.tests.utils.Constants;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag(Constants.UI)
@SeleniumChrome
@QuarkusIntegrationTest
public class DeleteArtifactIT extends ApicurioRegistryBaseIT {

    SeleniumProvider selenium = SeleniumProvider.getInstance();

    @AfterEach
    void logIfError(ExtensionContext ctx) {
        if (ctx.getExecutionException().isPresent()) {
            logger.error("", ctx.getExecutionException().get());
        }
    }

    @Test
    void testDeleteArtifacts() throws Exception {
        String groupId = TestUtils.generateGroupId();

        RegistryUITester page = new RegistryUITester(selenium);
        page.openWebPage();

        String content1 = resourceToString("artifactTypes/" + "protobuf/tutorial_v1.proto");
        String artifactId1 = page.uploadArtifact(groupId, null, ArtifactType.PROTOBUF, content1);
        assertEquals(1, registryClient.groups().byGroupId(groupId).artifacts().get().get(3, TimeUnit.SECONDS).getCount());
        page.goBackToArtifactsList();

        String content2 = resourceToString("artifactTypes/" + "jsonSchema/person_v1.json");
        String artifactId2 = page.uploadArtifact(groupId, null, ArtifactType.JSON, content2);
        assertEquals(2, registryClient.groups().byGroupId(groupId).artifacts().get().get(3, TimeUnit.SECONDS).getCount());
        page.goBackToArtifactsList();

        TestUtils.retry(() -> {
            List<ArtifactListItem> webArtifacts = page.getArtifactsList();
            assertEquals(2, webArtifacts.size());
        });

        List<String> webArtifactIds = page.getArtifactsList().stream()
                .map(a-> a.getArtifactId())
                .collect(Collectors.toList());

        assertThat(webArtifactIds, hasItems(artifactId1, artifactId2));

        page.deleteArtifact(groupId, artifactId1);

        TestUtils.waitFor("Artifacts list updated", Constants.POLL_INTERVAL, Duration.ofSeconds(60).toMillis(), () -> {
            try {
                return page.getArtifactsList().size() == 1;
            } catch (Exception e) {
                logger.error("", e);
                return false;
            }
        });
        List<ArtifactListItem> webArtifacts = page.getArtifactsList();
        assertEquals(artifactId2, webArtifacts.get(0).getArtifactId());

        page.deleteArtifact(groupId, artifactId2);

        TestUtils.waitFor("Artifacts list updated", Constants.POLL_INTERVAL, Duration.ofSeconds(60).toMillis(), () -> {
            try {
                return page.getArtifactsList().size() == 0;
            } catch (Exception e) {
                logger.error("", e);
                return false;
            }
        });
    }

    @Test
    void testDeleteViaApi() throws Exception {
        RegistryUITester page = new RegistryUITester(selenium);
        page.openWebPage();

        String content1 = resourceToString("artifactTypes/" + "protobuf/tutorial_v1.proto");
        String artifactId1 = page.uploadArtifact("default", null, ArtifactType.PROTOBUF, content1);
        assertEquals(1, registryClient.groups().byGroupId("default").artifacts().get().get(3, TimeUnit.SECONDS).getCount());
        page.goBackToArtifactsList();

        String content2 = resourceToString("artifactTypes/" + "jsonSchema/person_v1.json");
        String artifactId2 = page.uploadArtifact("default", null, ArtifactType.JSON, content2);
        assertEquals(2, registryClient.groups().byGroupId("default").artifacts().get().get(3, TimeUnit.SECONDS).getCount());
        page.goBackToArtifactsList();

        TestUtils.waitFor("Artifacts list updated", Constants.POLL_INTERVAL, Constants.TIMEOUT_GLOBAL, () -> {
            try {
                return page.getArtifactsList().size() == 2;
            } catch (Exception e) {
                logger.error("", e);
                return false;
            }
        });
        List<ArtifactListItem> webArtifacts = page.getArtifactsList();
        assertEquals(2, webArtifacts.size());

        webArtifacts.removeIf(artifact -> {
            return artifact.getArtifactId().equals(artifactId1) || artifact.getArtifactId().equals(artifactId2);
        });
        assertTrue(webArtifacts.isEmpty());

        registryClient.groups().byGroupId("default").artifacts().byArtifactId(artifactId1).delete().get(3, TimeUnit.SECONDS);
        retryAssertClientError("ArtifactNotFoundException", 404, (rc) -> rc.groups().byGroupId("default").artifacts().byArtifactId(artifactId1).meta().get().get(3, TimeUnit.SECONDS), errorCodeExtractor);

        selenium.refreshPage();
        TestUtils.waitFor("Artifacts list updated", Constants.POLL_INTERVAL, Constants.TIMEOUT_GLOBAL, () -> {
            try {
                return page.getArtifactsList().size() == 1;
            } catch (Exception e) {
                logger.error("", e);
                return false;
            }
        });
        webArtifacts = page.getArtifactsList();
        assertEquals(artifactId2, webArtifacts.get(0).getArtifactId());

        registryClient.groups().byGroupId("default").artifacts().byArtifactId(artifactId2).delete().get(3, TimeUnit.SECONDS);
        retryAssertClientError("ArtifactNotFoundException", 404, (rc) -> rc.groups().byGroupId("default").artifacts().byArtifactId(artifactId2).meta().get().get(3, TimeUnit.SECONDS), errorCodeExtractor);

        selenium.refreshPage();
        TestUtils.waitFor("Artifacts list updated", Constants.POLL_INTERVAL, Constants.TIMEOUT_GLOBAL, () -> {
            try {
                return page.getArtifactsList().size() == 0;
            } catch (Exception e) {
                logger.error("", e);
                return false;
            }
        });
    }

}
