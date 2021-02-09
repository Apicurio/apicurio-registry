/*
 * Copyright 2021 Red Hat
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

package io.apicurio.registry;

import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v2.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.v2.beans.EditableMetaData;
import io.apicurio.registry.rest.v2.beans.IfExists;
import io.apicurio.registry.rest.v2.beans.SearchedArtifact;
import io.apicurio.registry.rest.v2.beans.SortBy;
import io.apicurio.registry.rest.v2.beans.SortOrder;
import io.apicurio.registry.rest.v2.beans.UpdateState;
import io.apicurio.registry.rest.v2.beans.VersionMetaData;
import io.apicurio.registry.rest.v2.beans.VersionSearchResults;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static io.apicurio.registry.utils.tests.TestUtils.retry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Carles Arnal <carnalca@redhat.com>
 */
@QuarkusTest
public class RegistryClientV2Test extends AbstractResourceTestBase {

    private static final String ARTIFACT_CONTENT = "{\"name\":\"redhat\"}";

    @Test
    public void testAsyncCRUD() throws Exception {
        final String groupId = "testAsyncCRUD";
        String artifactId = generateArtifactId();
        try {
            ByteArrayInputStream stream = new ByteArrayInputStream(
                    "{\"name\":\"redhat\"}".getBytes(StandardCharsets.UTF_8));
            ArtifactMetaData amd = clientV2.createArtifact(groupId, ArtifactType.JSON, artifactId, stream);
            Assertions.assertNotNull(amd);
            waitForArtifact(groupId, artifactId);

            EditableMetaData emd = new EditableMetaData();
            emd.setName("myname");

            clientV2.updateArtifactMetaData(groupId, artifactId, emd);

            retry(() -> {
                ArtifactMetaData artifactMetaData = clientV2
                        .getArtifactMetaData(groupId, artifactId);
                Assertions.assertNotNull(artifactMetaData);
                Assertions.assertEquals("myname", artifactMetaData.getName());
            });

            stream = new ByteArrayInputStream("{\"name\":\"ibm\"}".getBytes(StandardCharsets.UTF_8));
            clientV2.updateArtifact(groupId, artifactId, stream);
        } finally {
            clientV2.deleteArtifact(groupId, artifactId);
        }
    }

    @Test
    public void testSmoke() throws Exception {
        final String groupId = "testSmoke";
        final String artifactId1 = generateArtifactId();
        final String artifactId2 = generateArtifactId();

        createArtifact(groupId, artifactId1);
        createArtifact(groupId, artifactId2);

        final ArtifactSearchResults searchResults = clientV2
                .listArtifactsInGroup(groupId, 2, 0, SortOrder.asc, SortBy.name);

        assertNotNull(clientV2.toString());
        assertEquals(clientV2.hashCode(), clientV2.hashCode());
        assertEquals(2, searchResults.getCount());

        clientV2.deleteArtifact(groupId, artifactId1);
        clientV2.deleteArtifact(groupId, artifactId2);

        final ArtifactSearchResults deletedResults = clientV2
                .listArtifactsInGroup(groupId, 2, 0, SortOrder.asc, SortBy.name);
        assertEquals(0, deletedResults.getCount());
    }

    @Test
    void testSearchArtifact() throws Exception {

        final String groupId = "testSearchArtifact";
        // warm-up
        clientV2.listArtifactsInGroup(groupId);

        String artifactId = UUID.randomUUID().toString();
        String name = "n" + ThreadLocalRandom.current().nextInt(1000000);
        ByteArrayInputStream artifactData = new ByteArrayInputStream(
                ("{\"type\":\"record\",\"title\":\"" + name
                        + "\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}")
                        .getBytes(StandardCharsets.UTF_8));

        ArtifactMetaData amd = clientV2.createArtifact(groupId, ArtifactType.JSON, artifactId, artifactData);
        long id = amd.getGlobalId();

        this.waitForGlobalId(id);

        ArtifactSearchResults results = clientV2
                .searchArtifacts(name, 0, 10, SortOrder.asc, SortBy.name, Collections.emptyList(),
                        Collections.emptyList(), "", "");
        Assertions.assertNotNull(results);
        Assertions.assertEquals(1, results.getCount());
        Assertions.assertEquals(1, results.getArtifacts().size());
        Assertions.assertEquals(name, results.getArtifacts().get(0).getName());

        // Try searching for *everything*.  This test was added due to Issue #661
        results = clientV2.searchArtifacts(null, null, null, null, null, null, null, null, null);
        Assertions.assertNotNull(results);
        Assertions.assertTrue(results.getCount() > 0);
    }

    @Test
    void testSearchVersion() throws Exception {
        final String groupId = "testSearchVersion";

        // warm-up
        clientV2.listArtifactsInGroup(groupId);

        String artifactId = UUID.randomUUID().toString();
        String name = "n" + ThreadLocalRandom.current().nextInt(1000000);
        ByteArrayInputStream artifactData = new ByteArrayInputStream(
                ("{\"type\":\"record\",\"title\":\"" + name + "\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}")
                        .getBytes(StandardCharsets.UTF_8));

        ArtifactMetaData amd = clientV2.createArtifact(groupId, ArtifactType.JSON, artifactId, artifactData);
        long id1 = amd.getGlobalId();

        this.waitForGlobalId(id1);


        artifactData.reset(); // a must between usage!!

        VersionMetaData vmd = clientV2.createArtifactVersion(groupId, artifactId, null, artifactData);
        long id2 = vmd.getGlobalId();

        this.waitForGlobalId(id2);


        VersionSearchResults results = clientV2.listArtifactVersions(groupId, artifactId, 0, 2);
        Assertions.assertNotNull(results);
        Assertions.assertEquals(2, results.getCount());
        Assertions.assertEquals(2, results.getVersions().size());
        Assertions.assertEquals(name, results.getVersions().get(0).getName());
    }

    @Test
    void testSearchDisabledArtifacts() throws Exception {
        final String groupId = "testSearchDisabledArtifacts";
        // warm-up
        clientV2.listArtifactsInGroup(groupId);
        String root = "testSearchDisabledArtifact" + ThreadLocalRandom.current().nextInt(1000000);
        List<String> artifactIds = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            String artifactId = root + UUID.randomUUID().toString();
            String name = root + i;
            ByteArrayInputStream artifactData = new ByteArrayInputStream(
                    ("{\"type\":\"record\",\"title\":\"" + name + "\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}")
                            .getBytes(StandardCharsets.UTF_8));

            clientV2.createArtifact(groupId, ArtifactType.JSON, artifactId, artifactData);
            waitForArtifact(groupId, artifactId);
            artifactIds.add(artifactId);
        }

        ArtifactSearchResults results = clientV2.searchArtifacts(root, 0, 10, SortOrder.asc, SortBy.name, null, null, null, null);
        Assertions.assertNotNull(results);
        Assertions.assertEquals(5, results.getCount());
        Assertions.assertEquals(5, results.getArtifacts().size());
        Assertions.assertTrue(results.getArtifacts().stream()
                .map(SearchedArtifact::getId)
                .collect(Collectors.toList()).containsAll(artifactIds));

        // Put 2 of the 5 artifacts in DISABLED state
        UpdateState us = new UpdateState();
        us.setState(ArtifactState.DISABLED);
        clientV2.updateArtifactState(groupId, artifactIds.get(0), us);
        waitForArtifactState(groupId, artifactIds.get(0), ArtifactState.DISABLED);
        clientV2.updateArtifactState(groupId, artifactIds.get(3), us);
        waitForArtifactState(groupId, artifactIds.get(3), ArtifactState.DISABLED);

        // Check the search results still include the DISABLED artifacts
        results = clientV2.searchArtifacts(root, 0, 10, SortOrder.asc, SortBy.name, null, null, null, null);
        Assertions.assertNotNull(results);
        Assertions.assertEquals(5, results.getCount());
        Assertions.assertEquals(5, results.getArtifacts().size());
        Assertions.assertTrue(results.getArtifacts().stream()
                .map(SearchedArtifact::getId)
                .collect(Collectors.toList()).containsAll(artifactIds));
        Assertions.assertEquals(2, results.getArtifacts().stream()
                .filter(searchedArtifact -> ArtifactState.DISABLED.equals(searchedArtifact.getState()))
                .count());
        Assertions.assertEquals(3, results.getArtifacts().stream()
                .filter(searchedArtifact -> ArtifactState.ENABLED.equals(searchedArtifact.getState()))
                .count());
    }

    @Test
    void testSearchDisabledVersions() throws Exception {
        final String groupId = "testSearchDisabledVersions";
        // warm-up
        clientV2.listArtifactsInGroup(groupId);

        String artifactId = UUID.randomUUID().toString();
        String name = "testSearchDisabledVersions" + ThreadLocalRandom.current().nextInt(1000000);
        ByteArrayInputStream artifactData = new ByteArrayInputStream(
                ("{\"type\":\"record\",\"title\":\"" + name + "\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}")
                        .getBytes(StandardCharsets.UTF_8));

        clientV2.createArtifact(groupId, ArtifactType.JSON, artifactId, artifactData);
        waitForArtifact(groupId, artifactId);

        artifactData.reset();

        clientV2.createArtifactVersion(groupId, artifactId, null, artifactData);
        waitForVersion(groupId, artifactId, 2);

        artifactData.reset();

        clientV2.createArtifactVersion(groupId, artifactId, null, artifactData);
        waitForVersion(groupId, artifactId, 3);

        VersionSearchResults results = clientV2.listArtifactVersions(groupId, artifactId, 0, 5);
        Assertions.assertNotNull(results);
        Assertions.assertEquals(3, results.getCount());
        Assertions.assertEquals(3, results.getVersions().size());
        Assertions.assertTrue(results.getVersions().stream()
                .allMatch(searchedVersion -> name.equals(searchedVersion.getName()) && ArtifactState.ENABLED.equals(searchedVersion.getState())));

        // Put 2 of the 3 versions in DISABLED state
        UpdateState us = new UpdateState();
        us.setState(ArtifactState.DISABLED);
        clientV2.updateArtifactVersionState(groupId, artifactId, "1", us);
        waitForVersionState(groupId, artifactId, 1, ArtifactState.DISABLED);
        clientV2.updateArtifactVersionState(groupId, artifactId, "3", us);
        waitForVersionState(groupId, artifactId, 3, ArtifactState.DISABLED);

        // Check that the search results still include the DISABLED versions
        results = clientV2.listArtifactVersions(groupId, artifactId, 0, 5);
        Assertions.assertNotNull(results);
        Assertions.assertEquals(3, results.getCount());
        Assertions.assertEquals(3, results.getVersions().size());
        Assertions.assertTrue(results.getVersions().stream()
                .allMatch(searchedVersion -> name.equals(searchedVersion.getName())));
        Assertions.assertEquals(2, results.getVersions().stream()
                .filter(searchedVersion -> ArtifactState.DISABLED.equals(searchedVersion.getState()))
                .count());
        Assertions.assertEquals(1, results.getVersions().stream()
                .filter(searchedVersion -> ArtifactState.ENABLED.equals(searchedVersion.getState()))
                .count());
    }

    @Test
    public void testLabels() throws Exception {
        final String groupId = "testLabels";
        String artifactId = generateArtifactId();
        try {
            ByteArrayInputStream stream = new ByteArrayInputStream("{\"name\":\"redhat\"}".getBytes(StandardCharsets.UTF_8));
            clientV2.createArtifact(groupId, ArtifactType.JSON, artifactId, stream);

            this.waitForArtifact(groupId, artifactId);

            EditableMetaData emd = new EditableMetaData();
            emd.setName("myname");

            final List<String> artifactLabels = Arrays.asList("Open Api", "Awesome Artifact", "JSON", "registry-client-test-testLabels");
            emd.setLabels(artifactLabels);
            clientV2.updateArtifactMetaData(groupId, artifactId, emd);

            retry(() -> {
                ArtifactMetaData artifactMetaData = clientV2.getArtifactMetaData(groupId, artifactId);
                Assertions.assertNotNull(artifactMetaData);
                Assertions.assertEquals("myname", artifactMetaData.getName());
                Assertions.assertEquals(4, artifactMetaData.getLabels().size());
                Assertions.assertTrue(artifactMetaData.getLabels().containsAll(artifactLabels));
            });

            retry((() -> {
                ArtifactSearchResults results = clientV2
                        .searchArtifacts("myname", 0, 10, SortOrder.asc, SortBy.name, artifactLabels, null, null, groupId);
                Assertions.assertNotNull(results);
                Assertions.assertEquals(1, results.getCount());
                Assertions.assertEquals(1, results.getArtifacts().size());
                Assertions.assertTrue(results.getArtifacts().get(0).getLabels().containsAll(artifactLabels));
            }));
        } finally {
            clientV2.deleteArtifact(groupId, artifactId);
        }
    }

    @Test
    public void testProperties() throws Exception {
        final String groupId = "testProperties";

        String artifactId = generateArtifactId();
        try {
            ByteArrayInputStream stream = new ByteArrayInputStream("{\"name\":\"redhat\"}".getBytes(StandardCharsets.UTF_8));
            clientV2.createArtifact(groupId, ArtifactType.JSON, artifactId, stream);

            this.waitForArtifact(groupId, artifactId);

            EditableMetaData emd = new EditableMetaData();
            emd.setName("myname");

            final Map<String, String> artifactProperties = new HashMap<>();
            artifactProperties.put("extraProperty1", "value for extra property 1");
            artifactProperties.put("extraProperty2", "value for extra property 2");
            artifactProperties.put("extraProperty3", "value for extra property 3");
            emd.setProperties(artifactProperties);
            clientV2.updateArtifactMetaData(groupId, artifactId, emd);

            retry(() -> {
                ArtifactMetaData artifactMetaData = clientV2.getArtifactMetaData(groupId, artifactId);
                Assertions.assertNotNull(artifactMetaData);
                Assertions.assertEquals("myname", artifactMetaData.getName());
                Assertions.assertEquals(3, artifactMetaData.getProperties().size());
                Assertions.assertTrue(artifactMetaData.getProperties().keySet().containsAll(artifactProperties.keySet()));
                for (String key : artifactMetaData.getProperties().keySet()) {
                    assertEquals(artifactMetaData.getProperties().get(key), artifactProperties.get(key));
                }
            });
        } finally {
            clientV2.deleteArtifact(groupId, artifactId);
        }
    }

    @Test
    void nameOrderingTest() throws Exception {
        final String groupId = "nameOrderingTest";
        final String firstArtifactId = generateArtifactId();
        final String secondArtifactId = generateArtifactId();
        final String thirdArtifactId = "cccTestorder";

        try {
            // warm-up
            client.listArtifacts();

            // Create artifact 1
            String firstName = "aaaTestorder" + ThreadLocalRandom.current().nextInt(1000000);
            ByteArrayInputStream artifactData = new ByteArrayInputStream(
                    ("{\"type\":\"record\",\"title\":\"" + firstName + "\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}")
                            .getBytes(StandardCharsets.UTF_8));

            ArtifactMetaData amd = clientV2.createArtifact(groupId, ArtifactType.JSON, firstArtifactId, artifactData);
            long id = amd.getGlobalId();

            this.waitForGlobalId(id);

            // Create artifact 2

            String secondName = "bbbTestorder" + ThreadLocalRandom.current().nextInt(1000000);
            ByteArrayInputStream secondData = new ByteArrayInputStream(
                    ("{\"type\":\"record\",\"title\":\"" + secondName + "\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}")
                            .getBytes(StandardCharsets.UTF_8));

            ArtifactMetaData secondCs = clientV2.createArtifact(groupId, ArtifactType.JSON, secondArtifactId, secondData);
            long secondId = secondCs.getGlobalId();

            this.waitForGlobalId(secondId);

            // Create artifact 3
            ByteArrayInputStream thirdData = new ByteArrayInputStream(
                    ("{\"openapi\":\"3.0.2\",\"info\":{\"description\":\"testorder\"}}")
                            .getBytes(StandardCharsets.UTF_8));
            ArtifactMetaData thirdCs = clientV2.createArtifact(groupId, ArtifactType.OPENAPI, thirdArtifactId, thirdData);
            long thirdId = thirdCs.getGlobalId();

            this.waitForGlobalId(thirdId);

            retry(() -> {
                ArtifactMetaData artifactMetaData = clientV2.getArtifactMetaData(groupId, thirdArtifactId);
                Assertions.assertNotNull(artifactMetaData);
                Assertions.assertEquals("testorder", artifactMetaData.getDescription());
            });

            ArtifactSearchResults ascResults = clientV2.searchArtifacts("Testorder",0, 10, SortOrder.asc, SortBy.name, null, null, "Testorder", groupId);

            Assertions.assertNotNull(ascResults);
            Assertions.assertEquals(3, ascResults.getCount());
            Assertions.assertEquals(3, ascResults.getArtifacts().size());
            Assertions.assertEquals(firstName, ascResults.getArtifacts().get(0).getName());
            Assertions.assertEquals(secondName, ascResults.getArtifacts().get(1).getName());
            Assertions.assertNull(ascResults.getArtifacts().get(2).getName());

            ArtifactSearchResults descResults = clientV2.searchArtifacts("Testorder",0, 10, SortOrder.desc, SortBy.name, null, null, "Testorder", groupId);

            Assertions.assertNotNull(descResults);
            Assertions.assertEquals(3, descResults.getCount());
            Assertions.assertEquals(3, descResults.getArtifacts().size());
            Assertions.assertNull(descResults.getArtifacts().get(0).getName());
            Assertions.assertEquals(secondName, descResults.getArtifacts().get(1).getName());
            Assertions.assertEquals(firstName, descResults.getArtifacts().get(2).getName());

        } finally {
            clientV2.deleteArtifact(groupId, firstArtifactId);
            clientV2.deleteArtifact(groupId, secondArtifactId);
            clientV2.deleteArtifact(groupId, thirdArtifactId);
        }
    }


    @Test
    public void getLatestArtifact() {
        final String groupId = "getLatestArtifact";
        final String artifactId = generateArtifactId();

        createArtifact(groupId, artifactId);

        InputStream amd = clientV2.getLatestArtifact(groupId, artifactId);

        assertNotNull(amd);
    }

    @Test
    public void getContentById() throws IOException {
        final String groupId = "getContentById";
        final String artifactId = generateArtifactId();

        ArtifactMetaData amd = createArtifact(groupId, artifactId);

        assertNotNull(amd.getContentId());

        InputStream content = clientV2.getContentById(amd.getContentId());
        assertNotNull(content);

        String artifactContent = IOUtils.toString(content, StandardCharsets.UTF_8);
        assertEquals(ARTIFACT_CONTENT, artifactContent);
    }

    @Test
    public void getContentByHash() throws IOException {
        final String groupId = "getContentByHash";
        final String artifactId = generateArtifactId();
        String contentHash = DigestUtils.sha256Hex(ARTIFACT_CONTENT);

        createArtifact(groupId, artifactId);

        InputStream content = clientV2.getContentByHash(contentHash);
        assertNotNull(content);

        String artifactContent = IOUtils.toString(content, StandardCharsets.UTF_8);
        assertEquals(ARTIFACT_CONTENT, artifactContent);
    }

    @Test
    public void getContentByGlobalId() throws IOException {
        final String groupId = "getContentByGlobalId";
        final String artifactId = generateArtifactId();

        ArtifactMetaData amd = createArtifact(groupId, artifactId);

        InputStream content = clientV2.getContentByGlobalId(amd.getGlobalId());
        assertNotNull(content);

        String artifactContent = IOUtils.toString(content, StandardCharsets.UTF_8);
        assertEquals(ARTIFACT_CONTENT, artifactContent);
    }

    private ArtifactMetaData createArtifact(String groupId, String artifactId) {
        ByteArrayInputStream stream = new ByteArrayInputStream(
                ARTIFACT_CONTENT.getBytes(StandardCharsets.UTF_8));

        ArtifactMetaData created = clientV2
                .createArtifact(groupId, ArtifactType.JSON, artifactId, "1", IfExists.RETURN, false, stream);

        assertNotNull(created);
        assertEquals(groupId, created.getGroupId());
        assertEquals(artifactId, created.getId());

        return created;
    }
}
