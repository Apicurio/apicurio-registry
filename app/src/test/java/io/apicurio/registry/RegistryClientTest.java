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

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.exception.ArtifactNotFoundException;
import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v2.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.v2.beans.EditableMetaData;
import io.apicurio.registry.rest.v2.beans.IfExists;
import io.apicurio.registry.rest.v2.beans.LogConfiguration;
import io.apicurio.registry.rest.v2.beans.NamedLogConfiguration;
import io.apicurio.registry.rest.v2.beans.Rule;
import io.apicurio.registry.rest.v2.beans.SearchedArtifact;
import io.apicurio.registry.rest.v2.beans.SortBy;
import io.apicurio.registry.rest.v2.beans.SortOrder;
import io.apicurio.registry.rest.v2.beans.UpdateState;
import io.apicurio.registry.rest.v2.beans.VersionMetaData;
import io.apicurio.registry.rest.v2.beans.VersionSearchResults;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.LogLevel;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static io.apicurio.registry.utils.tests.TestUtils.retry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Carles Arnal 'carnalca@redhat.com'
 */
@QuarkusTest
public class RegistryClientTest extends AbstractResourceTestBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegistryClientTest.class);

    private static final String ARTIFACT_CONTENT = "{\"name\":\"redhat\"}";
    private static final String UPDATED_CONTENT = "{\"name\":\"ibm\"}";

    @Test
    public void testAsyncCRUD() throws Exception {
        //Preparation
        final String groupId = "testAsyncCRUD";
        String artifactId = generateArtifactId();

        //Execution
        try {
            InputStream stream = IoUtil.toStream(ARTIFACT_CONTENT.getBytes(StandardCharsets.UTF_8));
            ArtifactMetaData amd = clientV2.createArtifact(groupId, artifactId, ArtifactType.JSON, stream);
            Assertions.assertNotNull(amd);
            waitForArtifact(groupId, artifactId);

            EditableMetaData emd = new EditableMetaData();
            emd.setName("testAsyncCRUD");

            clientV2.updateArtifactMetaData(groupId, artifactId, emd);

            //Assertions
            retry(() -> {
                ArtifactMetaData artifactMetaData = clientV2
                        .getArtifactMetaData(groupId, artifactId);
                Assertions.assertNotNull(artifactMetaData);
                Assertions.assertEquals("testAsyncCRUD", artifactMetaData.getName());
            });

            stream = IoUtil.toStream(UPDATED_CONTENT.getBytes(StandardCharsets.UTF_8));

            //Execution
            clientV2.updateArtifact(groupId, artifactId, stream);

            //Assertions
            assertEquals(UPDATED_CONTENT, IoUtil.toString(clientV2.getLatestArtifact(groupId, artifactId)));

        } finally {
            clientV2.deleteArtifact(groupId, artifactId);
        }
    }

    @Test
    public void testSmoke() throws Exception {
        //Preparation
        final String groupId = "testSmoke";
        final String artifactId1 = generateArtifactId();
        final String artifactId2 = generateArtifactId();

        createArtifact(groupId, artifactId1);
        createArtifact(groupId, artifactId2);

        //Execution
        final ArtifactSearchResults searchResults = clientV2.listArtifactsInGroup(groupId, SortBy.name, SortOrder.asc, 0, 2);

        //Assertions
        assertNotNull(clientV2.toString());
        assertEquals(clientV2.hashCode(), clientV2.hashCode());
        assertEquals(2, searchResults.getCount());

        //Preparation
        clientV2.deleteArtifact(groupId, artifactId1);
        clientV2.deleteArtifact(groupId, artifactId2);

        TestUtils.retry(() -> {
            //Execution
            final ArtifactSearchResults deletedResults = clientV2.listArtifactsInGroup(groupId, SortBy.name, SortOrder.asc, 0, 2);
            //Assertion
            assertEquals(0, deletedResults.getCount());
        });
    }

    @Test
    void testSearchArtifact() throws Exception {
        //PReparation
        final String groupId = "testSearchArtifact";
        clientV2.listArtifactsInGroup(groupId);

        String artifactId = UUID.randomUUID().toString();
        String name = "n" + ThreadLocalRandom.current().nextInt(1000000);
        InputStream artifactData = IoUtil.toStream(
                ("{\"type\":\"record\",\"title\":\"" + name
                        + "\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}")
                        .getBytes(StandardCharsets.UTF_8));

        ArtifactMetaData amd = clientV2.createArtifact(groupId, artifactId, ArtifactType.JSON, artifactData);
        long id = amd.getGlobalId();

        this.waitForGlobalId(id);

        //Execution
        ArtifactSearchResults results = clientV2.searchArtifacts(null, name, null, null, null, SortBy.name, SortOrder.asc, 0, 10);

        //Assertions
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
    void testSearchArtifactSortByCreatedOn() throws Exception {
        //PReparation
        final String groupId = "testSearchArtifactSortByCreatedOn";
        clientV2.listArtifactsInGroup(groupId);

        String artifactId = UUID.randomUUID().toString();
        String name = "n" + ThreadLocalRandom.current().nextInt(1000000);
        byte[] content = ("{\"type\":\"record\",\"title\":\"" + name+ "\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}")
                .getBytes(StandardCharsets.UTF_8);

        ArtifactMetaData amd = clientV2.createArtifact(groupId, artifactId, ArtifactType.JSON, IoUtil.toStream(content));
        long id = amd.getGlobalId();
        this.waitForGlobalId(id);
        LOGGER.info("created " + amd.getId() + " - " + amd.getCreatedOn());

        Thread.sleep(1500);

        String artifactId2 = UUID.randomUUID().toString();
        ArtifactMetaData amd2 = clientV2.createArtifact(groupId, artifactId2, ArtifactType.JSON, IoUtil.toStream(content));
        this.waitForGlobalId(amd2.getGlobalId());
        LOGGER.info("created " + amd2.getId() + " - " + amd2.getCreatedOn());

        //Execution
        ArtifactSearchResults results = clientV2.searchArtifacts(null, name, null, null, null, SortBy.createdOn, SortOrder.asc, 0, 10);

        //Assertions
        Assertions.assertNotNull(results);
        Assertions.assertEquals(2, results.getCount());
        Assertions.assertEquals(2, results.getArtifacts().size());
//        Assertions.assertEquals(name, results.getArtifacts().get(0).getName());

        LOGGER.info("search");
        LOGGER.info(results.getArtifacts().get(0).getId() + " - " + results.getArtifacts().get(0).getCreatedOn());
        LOGGER.info(results.getArtifacts().get(1).getId() + " - " + results.getArtifacts().get(1).getCreatedOn());

        Assertions.assertEquals(artifactId, results.getArtifacts().get(0).getId());

        // Try searching for *everything*.  This test was added due to Issue #661
        results = clientV2.searchArtifacts(null, null, null, null, null, null, null, null, null);
        Assertions.assertNotNull(results);
        Assertions.assertTrue(results.getCount() > 0);
    }

    @Test
    void testSearchVersion() throws Exception {
        //Preparation
        final String groupId = "testSearchVersion";
        clientV2.listArtifactsInGroup(groupId);

        String artifactId = UUID.randomUUID().toString();
        String name = "n" + ThreadLocalRandom.current().nextInt(1000000);
        InputStream artifactData = IoUtil.toStream(
                ("{\"type\":\"record\",\"title\":\"" + name + "\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}")
                        .getBytes(StandardCharsets.UTF_8));

        ArtifactMetaData amd = clientV2.createArtifact(groupId, artifactId, ArtifactType.JSON, artifactData);
        long id1 = amd.getGlobalId();
        this.waitForGlobalId(id1);
        artifactData.reset(); // a must between usage!!
        VersionMetaData vmd = clientV2.createArtifactVersion(groupId, artifactId, null, artifactData);
        long id2 = vmd.getGlobalId();
        this.waitForGlobalId(id2);


        //Execution
        VersionSearchResults results = clientV2.listArtifactVersions(groupId, artifactId, 0, 2);

        //Assertions
        Assertions.assertNotNull(results);
        Assertions.assertEquals(2, results.getCount());
        Assertions.assertEquals(2, results.getVersions().size());
        Assertions.assertEquals(name, results.getVersions().get(0).getName());
    }

    @Test
    void testSearchDisabledArtifacts() throws Exception {
        //Preparation
        final String groupId = "testSearchDisabledArtifacts";
        clientV2.listArtifactsInGroup(groupId);
        String root = "testSearchDisabledArtifact" + ThreadLocalRandom.current().nextInt(1000000);
        List<String> artifactIds = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            String artifactId = root + UUID.randomUUID().toString();
            String name = root + i;
            InputStream artifactData = IoUtil.toStream(
                    ("{\"type\":\"record\",\"title\":\"" + name + "\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}")
                            .getBytes(StandardCharsets.UTF_8));

            clientV2.createArtifact(groupId, artifactId, ArtifactType.JSON, artifactData);
            waitForArtifact(groupId, artifactId);
            artifactIds.add(artifactId);
        }

        //Execution
        ArtifactSearchResults results = clientV2.searchArtifacts(null, root, null, null, null, SortBy.name, SortOrder.asc, 0, 10);

        //Assertions
        Assertions.assertNotNull(results);
        Assertions.assertEquals(5, results.getCount());
        Assertions.assertEquals(5, results.getArtifacts().size());
        Assertions.assertTrue(results.getArtifacts().stream()
                .map(SearchedArtifact::getId)
                .collect(Collectors.toList()).containsAll(artifactIds));

        //Preparation
        // Put 2 of the 5 artifacts in DISABLED state
        UpdateState us = new UpdateState();
        us.setState(ArtifactState.DISABLED);
        clientV2.updateArtifactState(groupId, artifactIds.get(0), us);
        waitForArtifactState(groupId, artifactIds.get(0), ArtifactState.DISABLED);
        clientV2.updateArtifactState(groupId, artifactIds.get(3), us);
        waitForArtifactState(groupId, artifactIds.get(3), ArtifactState.DISABLED);

        //Execution
        // Check the search results still include the DISABLED artifacts
        results = clientV2.searchArtifacts(null, root, null, null, null, SortBy.name, SortOrder.asc, 0, 10);

        //Assertions
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
        //Preparation
        final String groupId = "testSearchDisabledVersions";
        clientV2.listArtifactsInGroup(groupId);

        String artifactId = UUID.randomUUID().toString();
        String name = "testSearchDisabledVersions" + ThreadLocalRandom.current().nextInt(1000000);
        InputStream artifactData = IoUtil.toStream(
                ("{\"type\":\"record\",\"title\":\"" + name + "\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}")
                        .getBytes(StandardCharsets.UTF_8));

        clientV2.createArtifact(groupId, artifactId, ArtifactType.JSON, artifactData);
        waitForArtifact(groupId, artifactId);
        artifactData.reset();

        clientV2.createArtifactVersion(groupId, artifactId, null, artifactData);
        waitForVersion(groupId, artifactId, 2);

        artifactData.reset();

        clientV2.createArtifactVersion(groupId, artifactId, null, artifactData);
        waitForVersion(groupId, artifactId, 3);

        //Execution
        VersionSearchResults results = clientV2.listArtifactVersions(groupId, artifactId, 0, 5);

        //Assertions
        Assertions.assertNotNull(results);
        Assertions.assertEquals(3, results.getCount());
        Assertions.assertEquals(3, results.getVersions().size());
        Assertions.assertTrue(results.getVersions().stream()
                .allMatch(searchedVersion -> name.equals(searchedVersion.getName()) && ArtifactState.ENABLED.equals(searchedVersion.getState())));

        //Preparation
        // Put 2 of the 3 versions in DISABLED state
        UpdateState us = new UpdateState();
        us.setState(ArtifactState.DISABLED);
        clientV2.updateArtifactVersionState(groupId, artifactId, "1", us);
        waitForVersionState(groupId, artifactId, "1", ArtifactState.DISABLED);
        clientV2.updateArtifactVersionState(groupId, artifactId, "3", us);
        waitForVersionState(groupId, artifactId, "3", ArtifactState.DISABLED);

        //Execution
        // Check that the search results still include the DISABLED versions
        results = clientV2.listArtifactVersions(groupId, artifactId, 0, 5);

        //Assertions
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
        //Preparation
        final String groupId = "testLabels";
        String artifactId = generateArtifactId();

        try {
            InputStream stream = IoUtil.toStream("{\"name\":\"redhat\"}".getBytes(StandardCharsets.UTF_8));
            clientV2.createArtifact(groupId, artifactId, ArtifactType.JSON, stream);
            this.waitForArtifact(groupId, artifactId);
            EditableMetaData emd = new EditableMetaData();
            emd.setName("testLabels");

            final List<String> artifactLabels = Arrays.asList("Open Api", "Awesome Artifact", "JSON", "registry-client-test-testLabels");
            emd.setLabels(artifactLabels);

            //Execution
            clientV2.updateArtifactMetaData(groupId, artifactId, emd);

            //Assertions
            retry(() -> {
                ArtifactMetaData artifactMetaData = clientV2.getArtifactMetaData(groupId, artifactId);
                Assertions.assertNotNull(artifactMetaData);
                Assertions.assertEquals("testLabels", artifactMetaData.getName());
                Assertions.assertEquals(4, artifactMetaData.getLabels().size());
                Assertions.assertTrue(artifactMetaData.getLabels().containsAll(artifactLabels));
            });

            retry((() -> {
                ArtifactSearchResults results = clientV2.searchArtifacts(null, "testLabels", null, null, null, SortBy.name, SortOrder.asc, 0, 10);
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
        //Preparation
        final String groupId = "testProperties";
        String artifactId = generateArtifactId();
        try {
            InputStream stream = IoUtil.toStream("{\"name\":\"redhat\"}".getBytes(StandardCharsets.UTF_8));
            clientV2.createArtifact(groupId, artifactId, ArtifactType.JSON, stream);

            this.waitForArtifact(groupId, artifactId);

            EditableMetaData emd = new EditableMetaData();
            emd.setName("testProperties");

            final Map<String, String> artifactProperties = new HashMap<>();
            artifactProperties.put("extraProperty1", "value for extra property 1");
            artifactProperties.put("extraProperty2", "value for extra property 2");
            artifactProperties.put("extraProperty3", "value for extra property 3");
            emd.setProperties(artifactProperties);

            //Execution
            clientV2.updateArtifactMetaData(groupId, artifactId, emd);

            //Assertions
            retry(() -> {
                ArtifactMetaData artifactMetaData = clientV2.getArtifactMetaData(groupId, artifactId);
                Assertions.assertNotNull(artifactMetaData);
                Assertions.assertEquals("testProperties", artifactMetaData.getName());
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
        //Preparation
        final String groupId = "nameOrderingTest";
        final String firstArtifactId = generateArtifactId();
        final String secondArtifactId = generateArtifactId();
        final String thirdArtifactId = "cccTestorder";

        try {
            clientV2.listArtifactsInGroup(groupId);

            // Create artifact 1
            String firstName = "aaaTestorder" + ThreadLocalRandom.current().nextInt(1000000);
            InputStream artifactData = IoUtil.toStream(
                    ("{\"type\":\"record\",\"title\":\"" + firstName + "\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}")
                            .getBytes(StandardCharsets.UTF_8));

            ArtifactMetaData amd = clientV2.createArtifact(groupId, firstArtifactId, ArtifactType.JSON, artifactData);
            long id = amd.getGlobalId();
            this.waitForGlobalId(id);
            // Create artifact 2
            String secondName = "bbbTestorder" + ThreadLocalRandom.current().nextInt(1000000);
            InputStream secondData = IoUtil.toStream(
                    ("{\"type\":\"record\",\"title\":\"" + secondName + "\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}")
                            .getBytes(StandardCharsets.UTF_8));

            ArtifactMetaData secondCs = clientV2.createArtifact(groupId, secondArtifactId, ArtifactType.JSON, secondData);
            long secondId = secondCs.getGlobalId();
            this.waitForGlobalId(secondId);
            // Create artifact 3
            InputStream thirdData = IoUtil.toStream(
                    ("{\"openapi\":\"3.0.2\",\"info\":{\"description\":\"testorder\"}}")
                            .getBytes(StandardCharsets.UTF_8));
            ArtifactMetaData thirdCs = clientV2.createArtifact(groupId, thirdArtifactId, ArtifactType.OPENAPI, thirdData);
            long thirdId = thirdCs.getGlobalId();
            this.waitForGlobalId(thirdId);

            retry(() -> {
                ArtifactMetaData artifactMetaData = clientV2.getArtifactMetaData(groupId, thirdArtifactId);
                Assertions.assertNotNull(artifactMetaData);
                Assertions.assertEquals("testorder", artifactMetaData.getDescription());
            });

            //Execution
            ArtifactSearchResults ascResults = clientV2.searchArtifacts(groupId, "Testorder", null, null, null, SortBy.name, SortOrder.asc, 0, 10);

            //Assertions
            Assertions.assertNotNull(ascResults);
            Assertions.assertEquals(3, ascResults.getCount());
            Assertions.assertEquals(3, ascResults.getArtifacts().size());
            Assertions.assertEquals(firstName, ascResults.getArtifacts().get(0).getName());
            Assertions.assertEquals(secondName, ascResults.getArtifacts().get(1).getName());
            Assertions.assertNull(ascResults.getArtifacts().get(2).getName());

            //Execution
            ArtifactSearchResults descResults = clientV2.searchArtifacts(groupId, "Testorder", null, null, null, SortBy.name, SortOrder.desc, 0, 10);

            //Assertions
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
    public void getLatestArtifact() throws Exception {
        //Preparation
        final String groupId = "getLatestArtifact";
        final String artifactId = generateArtifactId();

        createArtifact(groupId, artifactId);

        //Execution
        InputStream amd = clientV2.getLatestArtifact(groupId, artifactId);

        //Assertions
        assertNotNull(amd);
        assertEquals(ARTIFACT_CONTENT, IoUtil.toString(amd));
    }

    @Test
    public void getContentById() throws Exception {
        //Preparation
        final String groupId = "getContentById";
        final String artifactId = generateArtifactId();

        ArtifactMetaData amd = createArtifact(groupId, artifactId);
        assertNotNull(amd.getContentId());

        //Execution
        InputStream content = clientV2.getContentById(amd.getContentId());

        //Assertions
        assertNotNull(content);
        assertEquals(ARTIFACT_CONTENT, IOUtils.toString(content, StandardCharsets.UTF_8));
    }

    @Test
    public void testArtifactNotFound() {
        Assertions.assertThrows(ArtifactNotFoundException.class, () -> clientV2.getArtifactMetaData(UUID.randomUUID().toString(), UUID.randomUUID().toString()));
    }

    @Test
    public void getContentByHash() throws Exception {
        //Preparation
        final String groupId = "getContentByHash";
        final String artifactId = generateArtifactId();
        String contentHash = DigestUtils.sha256Hex(ARTIFACT_CONTENT);

        createArtifact(groupId, artifactId);

        //Execution
        InputStream content = clientV2.getContentByHash(contentHash);
        assertNotNull(content);

        //Assertions
        String artifactContent = IOUtils.toString(content, StandardCharsets.UTF_8);
        assertEquals(ARTIFACT_CONTENT, artifactContent);
    }

    @Test
    public void getContentByGlobalId() throws Exception {
        //Preparation
        final String groupId = "getContentByGlobalId";
        final String artifactId = generateArtifactId();

        ArtifactMetaData amd = createArtifact(groupId, artifactId);

        //Execution
        TestUtils.retry(() -> {
            InputStream content = clientV2.getContentByGlobalId(amd.getGlobalId());
            assertNotNull(content);

            //Assertions
            String artifactContent = IOUtils.toString(content, StandardCharsets.UTF_8);
            assertEquals(ARTIFACT_CONTENT, artifactContent);
        });



    }

    @Test
    public void getArtifactVersionMetaDataByContent() throws Exception {
        //Preparation
        final String groupId = "getArtifactVersionMetaDataByContent";
        final String artifactId = generateArtifactId();

        final ArtifactMetaData amd = createArtifact(groupId, artifactId);
        //Execution
        final VersionMetaData vmd = clientV2.getArtifactVersionMetaDataByContent(groupId, artifactId, IoUtil.toStream(ARTIFACT_CONTENT.getBytes()));

        //Assertions
        assertEquals(amd.getGlobalId(), vmd.getGlobalId());
        assertEquals(amd.getId(), vmd.getId());
        assertEquals(amd.getContentId(), vmd.getContentId());
    }

    @Test
    public void listArtifactRules() throws Exception {
        //Preparation
        final String groupId = "listArtifactRules";
        final String artifactId = generateArtifactId();

        createArtifact(groupId, artifactId);

        TestUtils.retry(() -> {
            final List<RuleType> emptyRules = clientV2.listArtifactRules(groupId, artifactId);

            //Assertions
            assertNotNull(emptyRules);
            assertTrue(emptyRules.isEmpty());
        });

        //Execution
        createArtifactRule(groupId, artifactId, RuleType.COMPATIBILITY, "BACKWARD");

        TestUtils.retry(() -> {
            final List<RuleType> ruleTypes = clientV2.listArtifactRules(groupId, artifactId);

            //Assertions
            assertNotNull(ruleTypes);
            assertFalse(ruleTypes.isEmpty());
        });
    }

    @Test
    public void deleteArtifactRules() throws Exception {
        //Preparation
        final String groupId = "deleteArtifactRules";
        final String artifactId = generateArtifactId();

        prepareRuleTest(groupId, artifactId, RuleType.COMPATIBILITY, "BACKWARD");

        //Execution
        clientV2.deleteArtifactRules(groupId, artifactId);

        //Assertions
        TestUtils.retry(() -> {
            final List<RuleType> emptyRules = clientV2.listArtifactRules(groupId, artifactId);
            assertNotNull(emptyRules);
            assertTrue(emptyRules.isEmpty());
        });
    }

    @Test
    public void getArtifactRuleConfig() throws Exception {
        //Preparation
        final String groupId = "getArtifactRuleConfig";
        final String artifactId = generateArtifactId();

        prepareRuleTest(groupId, artifactId, RuleType.COMPATIBILITY, "BACKWARD");

        TestUtils.retry(() -> {
            //Execution
            final Rule rule = clientV2.getArtifactRuleConfig(groupId, artifactId, RuleType.COMPATIBILITY);
            //Assertions
            assertNotNull(rule);
            assertEquals("BACKWARD", rule.getConfig());
        });
    }

    @Test
    public void updateArtifactRuleConfig() throws Exception {
        //Preparation
        final String groupId = "updateArtifactRuleConfig";
        final String artifactId = generateArtifactId();

        prepareRuleTest(groupId, artifactId, RuleType.COMPATIBILITY, "BACKWARD");

        TestUtils.retry(() -> {
            final Rule rule = clientV2.getArtifactRuleConfig(groupId, artifactId, RuleType.COMPATIBILITY);
            assertNotNull(rule);
            assertEquals("BACKWARD", rule.getConfig());
        });

        final Rule toUpdate = new Rule();
        toUpdate.setType(RuleType.COMPATIBILITY);
        toUpdate.setConfig("FULL");

        //Execution
        final Rule updated = clientV2.updateArtifactRuleConfig(groupId, artifactId, RuleType.COMPATIBILITY, toUpdate);

        //Assertions
        assertNotNull(updated);
        assertEquals("FULL", updated.getConfig());
    }

    @Test
    public void testUpdateArtifact() throws Exception {

        //Preparation
        final String groupId = "testUpdateArtifact";
        final String artifactId = generateArtifactId();

        createArtifact(groupId, artifactId);
        final String updatedContent = "{\"name\":\"ibm\"}";

        final InputStream stream = IoUtil.toStream(updatedContent.getBytes(StandardCharsets.UTF_8));
        //Execution
        clientV2.updateArtifact(groupId, artifactId, stream);

        //Assertions
        assertEquals(updatedContent, IoUtil.toString(clientV2.getLatestArtifact(groupId, artifactId)));
    }

    @Test
    public void deleteArtifactsInGroup() throws Exception {
        //Preparation
        final String groupId = "deleteArtifactsInGroup";
        final String firstArtifactId = generateArtifactId();
        final String secondArtifactId = generateArtifactId();
        createArtifact(groupId, firstArtifactId);
        createArtifact(groupId, secondArtifactId);

        final ArtifactSearchResults searchResults = clientV2.listArtifactsInGroup(groupId);
        assertFalse(searchResults.getArtifacts().isEmpty());
        assertEquals(2, (int) searchResults.getCount());

        //Execution
        clientV2.deleteArtifactsInGroup(groupId);

        TestUtils.retry(() -> {
            final ArtifactSearchResults deleted = clientV2.listArtifactsInGroup(groupId);

            //Assertions
            assertTrue(deleted.getArtifacts().isEmpty());
            assertEquals(0, (int) deleted.getCount());
        });
    }

    @Test
    public void searchArtifactsByContent() throws Exception {
        //Preparation
        final String groupId = "searchArtifactsByContent";
        final String firstArtifactId = generateArtifactId();
        final String secondArtifactId = generateArtifactId();

        String content = "{\"name\":\"" + TestUtils.generateSubject() + "\"}";
        createArtifact(groupId, firstArtifactId, ArtifactType.AVRO, content);
        createArtifact(groupId, secondArtifactId, ArtifactType.AVRO, content);

        //Execution
        final ArtifactSearchResults searchResults = clientV2.searchArtifactsByContent(IoUtil.toStream(content), null, null, null, null);

        //Assertions
        assertEquals(2, searchResults.getCount());
    }

    @Test
    public void smokeGlobalRules() throws Exception {
        createGlobalRule(RuleType.COMPATIBILITY, "BACKWARD");
        createGlobalRule(RuleType.VALIDITY, "FORWARD");

        TestUtils.retry(() -> {
            final List<RuleType> globalRules = clientV2.listGlobalRules();
            assertEquals(2, globalRules.size());
            assertTrue(globalRules.contains(RuleType.COMPATIBILITY));
            assertTrue(globalRules.contains(RuleType.VALIDITY));
        });
        clientV2.deleteAllGlobalRules();
        TestUtils.retry(() -> {
            final List<RuleType> updatedRules = clientV2.listGlobalRules();
            assertEquals(0, updatedRules.size());
        });
    }

    @Test
    public void getGlobalRuleConfig() throws Exception {
        //Preparation
        createGlobalRule(RuleType.COMPATIBILITY, "BACKWARD");

        TestUtils.retry(() -> {
            //Execution
            final Rule globalRuleConfig = clientV2.getGlobalRuleConfig(RuleType.COMPATIBILITY);
            //Assertions
            assertEquals(globalRuleConfig.getConfig(), "BACKWARD");
        });
    }

    @Test
    public void updateGlobalRuleConfig() throws Exception {
        //Preparation
        createGlobalRule(RuleType.COMPATIBILITY, "BACKWARD");

        TestUtils.retry(() -> {
            final Rule globalRuleConfig = clientV2.getGlobalRuleConfig(RuleType.COMPATIBILITY);
            assertEquals(globalRuleConfig.getConfig(), "BACKWARD");
        });

        final Rule toUpdate = new Rule();
        toUpdate.setType(RuleType.COMPATIBILITY);
        toUpdate.setConfig("FORWARD");

        //Execution
        final Rule updated = clientV2.updateGlobalRuleConfig(RuleType.COMPATIBILITY, toUpdate);

        //Assertions
        assertEquals(updated.getConfig(), "FORWARD");
    }

    @Test
    public void deleteGlobalRule() throws Exception {
        //Preparation
        createGlobalRule(RuleType.COMPATIBILITY, "BACKWARD");

        TestUtils.retry(() -> {
            final Rule globalRuleConfig = clientV2.getGlobalRuleConfig(RuleType.COMPATIBILITY);
            assertEquals(globalRuleConfig.getConfig(), "BACKWARD");
        });

        //Execution
        clientV2.deleteGlobalRule(RuleType.COMPATIBILITY);

        TestUtils.retry(() -> {
            final List<RuleType> ruleTypes = clientV2.listGlobalRules();

            //Assertions
            assertEquals(0, ruleTypes.size());
        });
    }

    @Test
    public void smokeLogLevels() {
        final String logger = "smokeLogLevels";
        final List<NamedLogConfiguration> namedLogConfigurations = clientV2.listLogConfigurations();
        assertEquals(0, namedLogConfigurations.size());

        setLogLevel(logger, LogLevel.DEBUG);
        final NamedLogConfiguration logConfiguration = clientV2.getLogConfiguration(logger);
        assertEquals(LogLevel.DEBUG, logConfiguration.getLevel());
        assertEquals(logger, logConfiguration.getName());

        final List<NamedLogConfiguration> logConfigurations = clientV2.listLogConfigurations();
        assertEquals(1, logConfigurations.size());

        clientV2.removeLogConfiguration(logger);
    }

    @Test
    public void testDefaultGroup() throws Exception {
        String nullDefaultGroup = null;
        String artifactId1 = "testDefaultGroup-" + UUID.randomUUID().toString();
        createArtifact(nullDefaultGroup, artifactId1);
        verifyGroupNullInMetadata(artifactId1, IoUtil.toStream(ARTIFACT_CONTENT.getBytes(StandardCharsets.UTF_8)));

        String defaultDefaultGroup = "default";
        String artifactId2 = "testDefaultGroup-" + UUID.randomUUID().toString();
        createArtifact(defaultDefaultGroup, artifactId2);
        verifyGroupNullInMetadata(artifactId2, IoUtil.toStream(ARTIFACT_CONTENT.getBytes(StandardCharsets.UTF_8)));

        String dummyGroup = "dummy";
        String artifactId3 = "testDefaultGroup-" + UUID.randomUUID().toString();
        createArtifact(dummyGroup, artifactId3);

        ArtifactSearchResults result = clientV2.searchArtifacts(null, null, null, null, null, null, null, null, 100);

        SearchedArtifact artifact1 = result.getArtifacts().stream()
            .filter(s -> s.getId().equals(artifactId1))
            .findFirst()
            .orElseThrow();

        assertNull(artifact1.getGroupId());

        SearchedArtifact artifact2 = result.getArtifacts().stream()
                .filter(s -> s.getId().equals(artifactId2))
                .findFirst()
                .orElseThrow();

        assertNull(artifact2.getGroupId());

        SearchedArtifact artifact3 = result.getArtifacts().stream()
                .filter(s -> s.getId().equals(artifactId3))
                .findFirst()
                .orElseThrow();

        assertEquals(dummyGroup, artifact3.getGroupId());

    }

    private void verifyGroupNullInMetadata(String artifactId, InputStream content) {
        ArtifactMetaData meta = clientV2.getArtifactMetaData(null, artifactId);
        assertNull(meta.getGroupId());

        VersionMetaData vmeta = clientV2.getArtifactVersionMetaData(null, artifactId, meta.getVersion());
        assertNull(vmeta.getGroupId());

        vmeta = clientV2.getArtifactVersionMetaDataByContent(null, artifactId, content);
        assertNull(vmeta.getGroupId());

        clientV2.listArtifactsInGroup(null).getArtifacts()
            .stream()
            .filter(s -> s.getId().equals(artifactId))
            .forEach(s -> assertNull(s.getGroupId()));

    }

    private ArtifactMetaData createArtifact(String groupId, String artifactId) throws Exception {
        final InputStream stream = IoUtil.toStream(ARTIFACT_CONTENT.getBytes(StandardCharsets.UTF_8));
        final ArtifactMetaData created = clientV2.createArtifact(groupId, artifactId, null, ArtifactType.JSON, IfExists.FAIL, false, stream);
        waitForArtifact(groupId, artifactId);

        assertNotNull(created);
        if (groupId == null || groupId.equals("default")) {
            assertNull(created.getGroupId());
        } else {
            assertEquals(groupId, created.getGroupId());
        }
        assertEquals(artifactId, created.getId());

        return created;
    }

    private void createArtifactRule(String groupId, String artifactId, RuleType ruleType, String ruleConfig) {
        final Rule rule = new Rule();
        rule.setConfig(ruleConfig);
        rule.setType(ruleType);
        clientV2.createArtifactRule(groupId, artifactId, rule);
    }

    private Rule createGlobalRule(RuleType ruleType, String ruleConfig) {
        final Rule rule = new Rule();
        rule.setConfig(ruleConfig);
        rule.setType(ruleType);
        clientV2.createGlobalRule(rule);

        return rule;
    }

    private void prepareRuleTest(String groupId, String artifactId, RuleType ruleType, String ruleConfig) throws Exception {
        createArtifact(groupId, artifactId);
        createArtifactRule(groupId, artifactId, ruleType, ruleConfig);
    }

    private void setLogLevel(String log, LogLevel logLevel) {
        final LogConfiguration logConfiguration = new LogConfiguration();
        logConfiguration.setLevel(logLevel);
        clientV2.setLogConfiguration(log, logConfiguration);
    }

    @Test
    void headersCustomizationTest() throws Exception {

        final String groupId = "headersCustomizationTest";
        final Map<String, String> firstRequestHeaders = Collections.singletonMap("FirstHeaderKey", "firstheadervalue");
        final Map<String, String> secondRequestHeaders = Collections.singletonMap("SecondHeaderKey", "secondheaderkey");

        testConcurrentClientCalls(groupId, clientV2, firstRequestHeaders, secondRequestHeaders);
        testNonConcurrentClientCalls(groupId, clientV2, firstRequestHeaders, secondRequestHeaders);
    }

    private void testNonConcurrentClientCalls(String groupId, RegistryClient client, Map<String, String> firstRequestHeaders, Map<String, String> secondRequestHeaders) throws InterruptedException {

        client.setNextRequestHeaders(firstRequestHeaders);
        Assertions.assertTrue(client.getHeaders().keySet().containsAll(firstRequestHeaders.keySet()));
        client.listArtifactsInGroup(groupId);
        Assertions.assertFalse(client.getHeaders().keySet().containsAll(secondRequestHeaders.keySet()));
        Assertions.assertFalse(client.getHeaders().keySet().containsAll(firstRequestHeaders.keySet()));

        client.setNextRequestHeaders(secondRequestHeaders);
        Assertions.assertTrue(client.getHeaders().keySet().containsAll(secondRequestHeaders.keySet()));
        client.listArtifactsInGroup(groupId);
        Assertions.assertFalse(client.getHeaders().keySet().containsAll(secondRequestHeaders.keySet()));
        Assertions.assertFalse(client.getHeaders().keySet().containsAll(firstRequestHeaders.keySet()));

    }

    private void testConcurrentClientCalls(String groupId, RegistryClient client, Map<String, String> firstRequestHeaders, Map<String, String> secondRequestHeaders) throws InterruptedException {

        final CountDownLatch latch = new CountDownLatch(2);

        new Thread(() -> {
            client.setNextRequestHeaders(firstRequestHeaders);
            Assertions.assertTrue(client.getHeaders().keySet().containsAll(firstRequestHeaders.keySet()));
            client.listArtifactsInGroup(groupId);
            Assertions.assertFalse(client.getHeaders().keySet().containsAll(secondRequestHeaders.keySet()));
            Assertions.assertFalse(client.getHeaders().keySet().containsAll(firstRequestHeaders.keySet()));
            latch.countDown();
        }).start();

        new Thread(() -> {
            client.setNextRequestHeaders(secondRequestHeaders);
            Assertions.assertTrue(client.getHeaders().keySet().containsAll(secondRequestHeaders.keySet()));
            client.listArtifactsInGroup(groupId);
            Assertions.assertFalse(client.getHeaders().keySet().containsAll(secondRequestHeaders.keySet()));
            Assertions.assertFalse(client.getHeaders().keySet().containsAll(firstRequestHeaders.keySet()));
            latch.countDown();
        }).start();

        latch.await();
    }
}
