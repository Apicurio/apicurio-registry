/*
 * Copyright 2020 Red Hat
 * Copyright 2020 IBM
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

import io.apicurio.registry.client.RegistryRestClient;
import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.rest.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.beans.EditableMetaData;
import io.apicurio.registry.rest.beans.SearchOver;
import io.apicurio.registry.rest.beans.SearchedArtifact;
import io.apicurio.registry.rest.beans.SortOrder;
import io.apicurio.registry.rest.beans.UpdateState;
import io.apicurio.registry.rest.beans.VersionMetaData;
import io.apicurio.registry.rest.beans.VersionSearchResults;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
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

/**
 * @author Ales Justin
 */
@QuarkusTest
public class RegistryClientTest extends AbstractResourceTestBase {

    @Test
    public void testSmoke() {
        client.deleteAllGlobalRules();

        Assertions.assertNotNull(client.toString());
        Assertions.assertEquals(client.hashCode(), client.hashCode());
    }

    @Disabled("Doesn't work with H2 test env after code change for Spanner")
    @Test
    public void testAsyncCRUD() throws Exception {
        String artifactId = generateArtifactId();
        try {
            ByteArrayInputStream stream = new ByteArrayInputStream("{\"name\":\"redhat\"}".getBytes(StandardCharsets.UTF_8));
            ArtifactMetaData amd = client.createArtifact(artifactId, ArtifactType.JSON, stream);
            Assertions.assertNotNull(amd);
            waitForArtifact(artifactId);

            EditableMetaData emd = new EditableMetaData();
            emd.setName("myname");
            client.updateArtifactMetaData(artifactId, emd);
            retry(() -> {
                ArtifactMetaData artifactMetaData = client.getArtifactMetaData(artifactId);
                Assertions.assertNotNull(artifactMetaData);
                Assertions.assertEquals("myname", artifactMetaData.getName());
            });

            stream = new ByteArrayInputStream("{\"name\":\"ibm\"}".getBytes(StandardCharsets.UTF_8));
            client.updateArtifact(artifactId, ArtifactType.JSON, stream);
        } finally {
//            client.deleteArtifact(artifactId);
        }
    }

    @Disabled("Doesn't work with H2 test env after code change for Spanner")
    @Test
    void testSearchArtifact() throws Exception {
        // warm-up
        client.listArtifacts();

        String artifactId = UUID.randomUUID().toString();
        String name = "n" + ThreadLocalRandom.current().nextInt(1000000);
        ByteArrayInputStream artifactData = new ByteArrayInputStream(
                ("{\"type\":\"record\",\"title\":\"" + name + "\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}")
                        .getBytes(StandardCharsets.UTF_8));

        ArtifactMetaData amd = client.createArtifact(artifactId, ArtifactType.JSON, artifactData);
        long id = amd.getGlobalId();

        this.waitForGlobalId(id);

        ArtifactSearchResults results = client.searchArtifacts(name, SearchOver.name, SortOrder.asc, 0, 2);
        Assertions.assertNotNull(results);
        Assertions.assertEquals(1, results.getCount());
        Assertions.assertEquals(1, results.getArtifacts().size());
        Assertions.assertEquals(name, results.getArtifacts().get(0).getName());

        // Try searching for *everything*.  This test was added due to Issue #661
        results = client.searchArtifacts(null, null, null, null, null);
        Assertions.assertNotNull(results);
        Assertions.assertTrue(results.getCount() > 0);
    }

    @Disabled("Doesn't work with H2 test env after code change for Spanner")
    @Test
    void testSearchVersion() throws Exception {
        // warm-up
        client.listArtifacts();

        String artifactId = UUID.randomUUID().toString();
        String name = "n" + ThreadLocalRandom.current().nextInt(1000000);
        ByteArrayInputStream artifactData = new ByteArrayInputStream(
                ("{\"type\":\"record\",\"title\":\"" + name + "\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}")
                        .getBytes(StandardCharsets.UTF_8));

        ArtifactMetaData amd = client.createArtifact(artifactId, ArtifactType.JSON, artifactData);
        long id1 = amd.getGlobalId();

        this.waitForGlobalId(id1);

        retry(() -> {
            ArtifactMetaData artifactMetaData = client.getArtifactMetaDataByGlobalId(id1);
            Assertions.assertNotNull(artifactMetaData);
        });

        artifactData.reset(); // a must between usage!!

        VersionMetaData vmd = client.createArtifactVersion(artifactId, ArtifactType.JSON, artifactData);
        long id2 = vmd.getGlobalId();

        this.waitForGlobalId(id2);

        retry(() -> {
            ArtifactMetaData artifactMetaData = client.getArtifactMetaDataByGlobalId(id2);
            Assertions.assertNotNull(artifactMetaData);
        });

        VersionSearchResults results = client.searchVersions(artifactId, 0, 2);
        Assertions.assertNotNull(results);
        Assertions.assertEquals(2, results.getCount());
        Assertions.assertEquals(2, results.getVersions().size());
        Assertions.assertEquals(name, results.getVersions().get(0).getName());
    }

    @Disabled("Doesn't work with H2 test env after code change for Spanner")
    @Test
    void testSearchDisabledArtifacts() throws Exception {
        // warm-up
        client.listArtifacts();
        String root = "testSearchDisabledArtifact" + ThreadLocalRandom.current().nextInt(1000000);
        List<String> artifactIds = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            String artifactId = root + UUID.randomUUID().toString();
            String name = root + i;
            ByteArrayInputStream artifactData = new ByteArrayInputStream(
                    ("{\"type\":\"record\",\"title\":\"" + name + "\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}")
                            .getBytes(StandardCharsets.UTF_8));

            client.createArtifact(artifactId, ArtifactType.JSON, artifactData);
            waitForArtifact(artifactId);
            artifactIds.add(artifactId);
        }

        ArtifactSearchResults results = client.searchArtifacts(root, SearchOver.name, SortOrder.asc, null, null);
        Assertions.assertNotNull(results);
        Assertions.assertEquals(5, results.getCount());
        Assertions.assertEquals(5, results.getArtifacts().size());
        Assertions.assertTrue(results.getArtifacts().stream()
                .map(SearchedArtifact::getId)
                .collect(Collectors.toList()).containsAll(artifactIds));

        // Put 2 of the 5 artifacts in DISABLED state
        UpdateState us = new UpdateState();
        us.setState(ArtifactState.DISABLED);
        client.updateArtifactState(artifactIds.get(0), us);
        waitForArtifactState(artifactIds.get(0), ArtifactState.DISABLED);
        client.updateArtifactState(artifactIds.get(3), us);
        waitForArtifactState(artifactIds.get(3), ArtifactState.DISABLED);

        // Check the search results still include the DISABLED artifacts
        results = client.searchArtifacts(root, SearchOver.name, SortOrder.asc, null, null);
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

    @Disabled("Doesn't work with H2 test env after code change for Spanner")
    @Test
    void testSearchDisabledVersions() throws Exception {
        // warm-up
        client.listArtifacts();

        String artifactId = UUID.randomUUID().toString();
        String name = "testSearchDisabledVersions" + ThreadLocalRandom.current().nextInt(1000000);
        ByteArrayInputStream artifactData = new ByteArrayInputStream(
                ("{\"type\":\"record\",\"title\":\"" + name + "\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}")
                        .getBytes(StandardCharsets.UTF_8));

        client.createArtifact(artifactId, ArtifactType.JSON, artifactData);
        waitForArtifact(artifactId);

        artifactData.reset();

        client.createArtifactVersion(artifactId, ArtifactType.JSON, artifactData);
        waitForVersion(artifactId, 2);

        artifactData.reset();

        client.createArtifactVersion(artifactId, ArtifactType.JSON, artifactData);
        waitForVersion(artifactId, 3);

        VersionSearchResults results = client.searchVersions(artifactId, 0, 5);
        Assertions.assertNotNull(results);
        Assertions.assertEquals(3, results.getCount());
        Assertions.assertEquals(3, results.getVersions().size());
        Assertions.assertTrue(results.getVersions().stream()
                .allMatch(searchedVersion -> name.equals(searchedVersion.getName()) && ArtifactState.ENABLED.equals(searchedVersion.getState())));

        // Put 2 of the 3 versions in DISABLED state
        UpdateState us = new UpdateState();
        us.setState(ArtifactState.DISABLED);
        client.updateArtifactVersionState(artifactId, 1, us);
        waitForVersionState(artifactId, 1, ArtifactState.DISABLED);
        client.updateArtifactVersionState(artifactId, 3, us);
        waitForVersionState(artifactId, 3, ArtifactState.DISABLED);

        // Check that the search results still include the DISABLED versions
        results = client.searchVersions(artifactId, 0, 5);
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

    @Disabled("Doesn't work with H2 test env after code change for Spanner")
    @Test
    public void testLabels() throws Exception {
        String artifactId = generateArtifactId();
        try {
            ByteArrayInputStream stream = new ByteArrayInputStream("{\"name\":\"redhat\"}".getBytes(StandardCharsets.UTF_8));
            client.createArtifact(artifactId, ArtifactType.JSON, stream);

            this.waitForArtifact(artifactId);

            EditableMetaData emd = new EditableMetaData();
            emd.setName("myname");

            final List<String> artifactLabels = Arrays.asList("Open Api", "Awesome Artifact", "JSON", "registry-client-test-testLabels");
            emd.setLabels(artifactLabels);
            client.updateArtifactMetaData(artifactId, emd);

            retry(() -> {
                ArtifactMetaData artifactMetaData = client.getArtifactMetaData(artifactId);
                Assertions.assertNotNull(artifactMetaData);
                Assertions.assertEquals("myname", artifactMetaData.getName());
                Assertions.assertEquals(4, artifactMetaData.getLabels().size());
                Assertions.assertTrue(artifactMetaData.getLabels().containsAll(artifactLabels));
            });

            retry((() -> {
                ArtifactSearchResults results = client
                        .searchArtifacts("registry-client-test-testLabels", SearchOver.labels, SortOrder.asc, 0, 2);
                Assertions.assertNotNull(results);
                Assertions.assertEquals(1, results.getCount());
                Assertions.assertEquals(1, results.getArtifacts().size());
                Assertions.assertTrue(results.getArtifacts().get(0).getLabels().containsAll(artifactLabels));
            }));
        } finally {
            client.deleteArtifact(artifactId);
        }
    }

    @Disabled("Doesn't work with H2 test env after code change for Spanner")
    @Test
    public void testProperties() throws Exception {
        String artifactId = generateArtifactId();
        try {
            ByteArrayInputStream stream = new ByteArrayInputStream("{\"name\":\"redhat\"}".getBytes(StandardCharsets.UTF_8));
            client.createArtifact(artifactId, ArtifactType.JSON, stream);

            this.waitForArtifact(artifactId);

            EditableMetaData emd = new EditableMetaData();
            emd.setName("myname");

            final Map<String, String> artifactProperties = new HashMap<>();
            artifactProperties.put("extraProperty1", "value for extra property 1");
            artifactProperties.put("extraProperty2", "value for extra property 2");
            artifactProperties.put("extraProperty3", "value for extra property 3");
            emd.setProperties(artifactProperties);
            client.updateArtifactMetaData(artifactId, emd);

            retry(() -> {
                ArtifactMetaData artifactMetaData = client.getArtifactMetaData(artifactId);
                Assertions.assertNotNull(artifactMetaData);
                Assertions.assertEquals("myname", artifactMetaData.getName());
                Assertions.assertEquals(3, artifactMetaData.getProperties().size());
                Assertions.assertTrue(artifactMetaData.getProperties().keySet().containsAll(artifactProperties.keySet()));
                for (String key : artifactMetaData.getProperties().keySet()) {
                    Assertions.assertTrue(artifactMetaData.getProperties().get(key).equals(artifactProperties.get(key)));
                }
            });
        } finally {
            client.deleteArtifact(artifactId);
        }
    }

    @Disabled("Doesn't work with H2 test env after code change for Spanner")
    @Test
    void nameOrderingTest() throws Exception {
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

            ArtifactMetaData amd = client.createArtifact(firstArtifactId, ArtifactType.JSON, artifactData);
            long id = amd.getGlobalId();

            this.waitForGlobalId(id);

            // Create artifact 2

            String secondName = "bbbTestorder" + ThreadLocalRandom.current().nextInt(1000000);
            ByteArrayInputStream secondData = new ByteArrayInputStream(
                    ("{\"type\":\"record\",\"title\":\"" + secondName + "\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}")
                            .getBytes(StandardCharsets.UTF_8));

            ArtifactMetaData secondCs = client.createArtifact(secondArtifactId, ArtifactType.JSON, secondData);
            long secondId = secondCs.getGlobalId();

            this.waitForGlobalId(secondId);

            // Create artifact 3
            ByteArrayInputStream thirdData = new ByteArrayInputStream(
                    ("{\"openapi\":\"3.0.2\",\"info\":{\"description\":\"testorder\"}}")
                            .getBytes(StandardCharsets.UTF_8));
            ArtifactMetaData thirdCs = client.createArtifact(thirdArtifactId, ArtifactType.OPENAPI, thirdData);
            long thirdId = thirdCs.getGlobalId();

            this.waitForGlobalId(thirdId);

            retry(() -> {
                ArtifactMetaData artifactMetaData = client.getArtifactMetaDataByGlobalId(thirdId);
                Assertions.assertNotNull(artifactMetaData);
                Assertions.assertEquals("testorder", artifactMetaData.getDescription());
            });

            ArtifactSearchResults ascResults = client.searchArtifacts("Testorder", SearchOver.everything, SortOrder.asc, 0, 5);
            Assertions.assertNotNull(ascResults);
            Assertions.assertEquals(3, ascResults.getCount());
            Assertions.assertEquals(3, ascResults.getArtifacts().size());
            Assertions.assertEquals(firstName, ascResults.getArtifacts().get(0).getName());
            Assertions.assertEquals(secondName, ascResults.getArtifacts().get(1).getName());
            Assertions.assertNull(ascResults.getArtifacts().get(2).getName());

            ArtifactSearchResults descResults = client.searchArtifacts("Testorder", SearchOver.everything, SortOrder.desc, 0, 5);
            Assertions.assertNotNull(descResults);
            Assertions.assertEquals(3, descResults.getCount());
            Assertions.assertEquals(3, descResults.getArtifacts().size());
            Assertions.assertNull(descResults.getArtifacts().get(0).getName());
            Assertions.assertEquals(secondName, descResults.getArtifacts().get(1).getName());
            Assertions.assertEquals(firstName, descResults.getArtifacts().get(2).getName());

            ArtifactSearchResults searchIdOverName = client.searchArtifacts(firstArtifactId, SearchOver.name, SortOrder.asc, 0, 5);
            Assertions.assertEquals(firstName, searchIdOverName.getArtifacts().get(0).getName());
            Assertions.assertEquals(firstArtifactId, searchIdOverName.getArtifacts().get(0).getId());

        } finally {
            client.deleteArtifact(firstArtifactId);
            client.deleteArtifact(secondArtifactId);
            client.deleteArtifact(thirdArtifactId);
        }
    }

    @Test
    void headersCustomizationTest() throws Exception {

        final Map<String, String> firstRequestHeaders = Collections.singletonMap("FirstHeaderKey", "firstheadervalue");
        final Map<String, String> secondRequestHeaders = Collections.singletonMap("SecondHeaderKey", "secondheaderkey");

        testConcurrentClientCalls(client, firstRequestHeaders, secondRequestHeaders);
        testNonConcurrentClientCalls(client, firstRequestHeaders, secondRequestHeaders);
    }

    private void testNonConcurrentClientCalls(RegistryRestClient client, Map<String, String> firstRequestHeaders, Map<String, String> secondRequestHeaders) throws InterruptedException {

        client.setNextRequestHeaders(firstRequestHeaders);
        Assertions.assertTrue(client.getHeaders().keySet().containsAll(firstRequestHeaders.keySet()));
        client.listArtifacts();
        Assertions.assertFalse(client.getHeaders().keySet().containsAll(secondRequestHeaders.keySet()));
        Assertions.assertFalse(client.getHeaders().keySet().containsAll(firstRequestHeaders.keySet()));

        client.setNextRequestHeaders(secondRequestHeaders);
        Assertions.assertTrue(client.getHeaders().keySet().containsAll(secondRequestHeaders.keySet()));
        client.listArtifacts();
        Assertions.assertFalse(client.getHeaders().keySet().containsAll(secondRequestHeaders.keySet()));
        Assertions.assertFalse(client.getHeaders().keySet().containsAll(firstRequestHeaders.keySet()));

    }

    private void testConcurrentClientCalls(RegistryRestClient client, Map<String, String> firstRequestHeaders, Map<String, String> secondRequestHeaders) throws InterruptedException {

        final CountDownLatch latch = new CountDownLatch(2);

        new Thread(() -> {
            client.setNextRequestHeaders(firstRequestHeaders);
            Assertions.assertTrue(client.getHeaders().keySet().containsAll(firstRequestHeaders.keySet()));
            client.listArtifacts();
            Assertions.assertFalse(client.getHeaders().keySet().containsAll(secondRequestHeaders.keySet()));
            Assertions.assertFalse(client.getHeaders().keySet().containsAll(firstRequestHeaders.keySet()));
            latch.countDown();
        }).start();

        new Thread(() -> {
            client.setNextRequestHeaders(secondRequestHeaders);
            Assertions.assertTrue(client.getHeaders().keySet().containsAll(secondRequestHeaders.keySet()));
            client.listArtifacts();
            Assertions.assertFalse(client.getHeaders().keySet().containsAll(secondRequestHeaders.keySet()));
            Assertions.assertFalse(client.getHeaders().keySet().containsAll(firstRequestHeaders.keySet()));
            latch.countDown();
        }).start();

        latch.await();
    }
}
