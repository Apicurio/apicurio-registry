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

import static io.apicurio.registry.utils.tests.TestUtils.retry;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;

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
import io.apicurio.registry.utils.tests.RegistryRestClientTest;
import io.quarkus.test.junit.QuarkusTest;

/**
 * @author Ales Justin
 */
@QuarkusTest
public class RegistryClientTest extends AbstractResourceTestBase {

    @RegistryRestClientTest
    public void testSmoke(RegistryRestClient restClient) {
        restClient.deleteAllGlobalRules();

        Assertions.assertNotNull(restClient.toString());
        Assertions.assertEquals(restClient.hashCode(), restClient.hashCode());
    }

    @RegistryRestClientTest
    public void testAsyncCRUD(RegistryRestClient restClient) throws Exception {
        String artifactId = generateArtifactId();
        try {
            ByteArrayInputStream stream = new ByteArrayInputStream("{\"name\":\"redhat\"}".getBytes(StandardCharsets.UTF_8));
            ArtifactMetaData amd = restClient.createArtifact(artifactId, ArtifactType.JSON, stream);
            Assertions.assertNotNull(amd);
            waitForArtifact(artifactId);

            EditableMetaData emd = new EditableMetaData();
            emd.setName("myname");
            restClient.updateArtifactMetaData(artifactId, emd);
            retry(() -> {
                ArtifactMetaData artifactMetaData = restClient.getArtifactMetaData(artifactId);
                Assertions.assertNotNull(artifactMetaData);
                Assertions.assertEquals("myname", artifactMetaData.getName());
            });

            stream = new ByteArrayInputStream("{\"name\":\"ibm\"}".getBytes(StandardCharsets.UTF_8));
            restClient.updateArtifact(artifactId, ArtifactType.JSON, stream);
        } finally {
//            restClient.deleteArtifact(artifactId);
        }
    }

    @RegistryRestClientTest
    void testSearchArtifact(RegistryRestClient restClient) throws Exception {
        // warm-up
        restClient.listArtifacts();

        String artifactId = UUID.randomUUID().toString();
        String name = "n" + ThreadLocalRandom.current().nextInt(1000000);
        ByteArrayInputStream artifactData = new ByteArrayInputStream(
                ("{\"type\":\"record\",\"title\":\""+ name + "\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}")
                        .getBytes(StandardCharsets.UTF_8));

        ArtifactMetaData amd = restClient.createArtifact(artifactId, ArtifactType.JSON, artifactData);
        long id = amd.getGlobalId();
        
        this.waitForGlobalId(id);

        retry(() -> {
            ArtifactMetaData artifactMetaData = restClient.getArtifactMetaDataByGlobalId(id);
            Assertions.assertNotNull(artifactMetaData);
        });

        ArtifactSearchResults results = restClient.searchArtifacts(name, SearchOver.name, SortOrder.asc, 0, 2);
        Assertions.assertNotNull(results);
        Assertions.assertEquals(1, results.getCount());
        Assertions.assertEquals(1, results.getArtifacts().size());
        Assertions.assertEquals(name, results.getArtifacts().get(0).getName());

        // Try searching for *everything*.  This test was added due to Issue #661
        results = restClient.searchArtifacts(null, null, null, null, null);
        Assertions.assertNotNull(results);
        Assertions.assertTrue(results.getCount() > 0);
    }

    @RegistryRestClientTest
    void testSearchVersion(RegistryRestClient restClient) throws Exception {
        // warm-up
        restClient.listArtifacts();

        String artifactId = UUID.randomUUID().toString();
        String name = "n" + ThreadLocalRandom.current().nextInt(1000000);
        ByteArrayInputStream artifactData = new ByteArrayInputStream(
                ("{\"type\":\"record\",\"title\":\""+ name + "\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}")
                        .getBytes(StandardCharsets.UTF_8));

        ArtifactMetaData amd = restClient.createArtifact(artifactId, ArtifactType.JSON, artifactData);
        long id1 = amd.getGlobalId();
        
        this.waitForGlobalId(id1);

        retry(() -> {
            ArtifactMetaData artifactMetaData = restClient.getArtifactMetaDataByGlobalId(id1);
            Assertions.assertNotNull(artifactMetaData);
        });

        artifactData.reset(); // a must between usage!!

        VersionMetaData vmd = restClient.createArtifactVersion(artifactId, ArtifactType.JSON, artifactData);
        long id2 = vmd.getGlobalId();
        
        this.waitForGlobalId(id2);

        retry(() -> {
            ArtifactMetaData artifactMetaData = restClient.getArtifactMetaDataByGlobalId(id2);
            Assertions.assertNotNull(artifactMetaData);
        });

        VersionSearchResults results = restClient.searchVersions(artifactId, 0, 2);
        Assertions.assertNotNull(results);
        Assertions.assertEquals(2, results.getCount());
        Assertions.assertEquals(2, results.getVersions().size());
        Assertions.assertEquals(name, results.getVersions().get(0).getName());
    }

    @RegistryRestClientTest
    void testSearchDisabledArtifacts(RegistryRestClient restClient) throws Exception {
        // warm-up
        restClient.listArtifacts();
        String root = "testSearchDisabledArtifact" + ThreadLocalRandom.current().nextInt(1000000);
        List<String> artifactIds = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            String artifactId = root + UUID.randomUUID().toString();
            String name = root + i;
            ByteArrayInputStream artifactData = new ByteArrayInputStream(
                ("{\"type\":\"record\",\"title\":\""+ name + "\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}")
                    .getBytes(StandardCharsets.UTF_8));

            restClient.createArtifact(artifactId, ArtifactType.JSON, artifactData);
            waitForArtifact(artifactId);
            artifactIds.add(artifactId);
        }

        ArtifactSearchResults results = restClient.searchArtifacts(root, SearchOver.name, SortOrder.asc, null, null);
        Assertions.assertNotNull(results);
        Assertions.assertEquals(5, results.getCount());
        Assertions.assertEquals(5, results.getArtifacts().size());
        Assertions.assertTrue(results.getArtifacts().stream()
            .map(SearchedArtifact::getId)
            .collect(Collectors.toList()).containsAll(artifactIds));

        // Put 2 of the 5 artifacts in DISABLED state
        UpdateState us = new UpdateState();
        us.setState(ArtifactState.DISABLED);
        restClient.updateArtifactState(artifactIds.get(0), us);
        waitForArtifactState(artifactIds.get(0), ArtifactState.DISABLED);
        restClient.updateArtifactState(artifactIds.get(3), us);
        waitForArtifactState(artifactIds.get(3), ArtifactState.DISABLED);

        // Check the search results still include the DISABLED artifacts
        results = restClient.searchArtifacts(root, SearchOver.name, SortOrder.asc, null, null);
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

    @RegistryRestClientTest
    void testSearchDisabledVersions(RegistryRestClient restClient) throws Exception {
        // warm-up
        restClient.listArtifacts();

        String artifactId = UUID.randomUUID().toString();
        String name = "testSearchDisabledVersions" + ThreadLocalRandom.current().nextInt(1000000);
        ByteArrayInputStream artifactData = new ByteArrayInputStream(
            ("{\"type\":\"record\",\"title\":\""+ name + "\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}")
                .getBytes(StandardCharsets.UTF_8));

        restClient.createArtifact(artifactId, ArtifactType.JSON, artifactData);
        waitForArtifact(artifactId);

        artifactData.reset();

        restClient.createArtifactVersion(artifactId, ArtifactType.JSON, artifactData);
        waitForVersion(artifactId, 2);

        artifactData.reset();

        restClient.createArtifactVersion(artifactId, ArtifactType.JSON, artifactData);
        waitForVersion(artifactId, 3);

        VersionSearchResults results = restClient.searchVersions(artifactId, 0, 5);
        Assertions.assertNotNull(results);
        Assertions.assertEquals(3, results.getCount());
        Assertions.assertEquals(3, results.getVersions().size());
        Assertions.assertTrue(results.getVersions().stream()
            .allMatch(searchedVersion -> name.equals(searchedVersion.getName()) && ArtifactState.ENABLED.equals(searchedVersion.getState())));

        // Put 2 of the 3 versions in DISABLED state
        UpdateState us = new UpdateState();
        us.setState(ArtifactState.DISABLED);
        restClient.updateArtifactVersionState(artifactId, 1, us);
        waitForVersionState(artifactId, 1, ArtifactState.DISABLED);
        restClient.updateArtifactVersionState(artifactId, 3, us);
        waitForVersionState(artifactId, 3, ArtifactState.DISABLED);

        // Check that the search results still include the DISABLED versions
        results = restClient.searchVersions(artifactId, 0, 5);
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

    @RegistryRestClientTest
    public void testLabels(RegistryRestClient restClient) throws Exception {
        String artifactId = generateArtifactId();
        try {
            ByteArrayInputStream stream = new ByteArrayInputStream("{\"name\":\"redhat\"}".getBytes(StandardCharsets.UTF_8));
            restClient.createArtifact(artifactId, ArtifactType.JSON, stream);
            
            this.waitForArtifact(artifactId);

            EditableMetaData emd = new EditableMetaData();
            emd.setName("myname");

            final List<String> artifactLabels = Arrays.asList("Open Api", "Awesome Artifact", "JSON", "registry-client-test-testLabels");
            emd.setLabels(artifactLabels);
            restClient.updateArtifactMetaData(artifactId, emd);

            retry(() -> {
                ArtifactMetaData artifactMetaData = restClient.getArtifactMetaData(artifactId);
                Assertions.assertNotNull(artifactMetaData);
                Assertions.assertEquals("myname", artifactMetaData.getName());
                Assertions.assertEquals(4, artifactMetaData.getLabels().size());
                Assertions.assertTrue(artifactMetaData.getLabels().containsAll(artifactLabels));
            });

            retry((() -> {
                System.out.println("==============================");
                ArtifactSearchResults results = client
                        .searchArtifacts("registry-client-test-testLabels", SearchOver.labels, SortOrder.asc, 0, 2);
                results.getArtifacts().forEach(arty -> {
                    System.out.println(arty);
                });
                System.out.println("==============================");
                Assertions.assertNotNull(results);
                Assertions.assertEquals(1, results.getCount());
                Assertions.assertEquals(1, results.getArtifacts().size());
                Assertions.assertTrue(results.getArtifacts().get(0).getLabels().containsAll(artifactLabels));
            }));
        } finally {
            restClient.deleteArtifact(artifactId);
        }
    }

    @RegistryRestClientTest
    public void testProperties(RegistryRestClient restClient) throws Exception {
        String artifactId = generateArtifactId();
        try {
            ByteArrayInputStream stream = new ByteArrayInputStream("{\"name\":\"redhat\"}".getBytes(StandardCharsets.UTF_8));
            restClient.createArtifact(artifactId, ArtifactType.JSON, stream);

            this.waitForArtifact(artifactId);

            EditableMetaData emd = new EditableMetaData();
            emd.setName("myname");

            final Map<String, String> artifactProperties = new HashMap<>();
            artifactProperties.put("extraProperty1", "value for extra property 1");
            artifactProperties.put("extraProperty2", "value for extra property 2");
            artifactProperties.put("extraProperty3", "value for extra property 3");
            emd.setProperties(artifactProperties);
            restClient.updateArtifactMetaData(artifactId, emd);

            retry(() -> {
                ArtifactMetaData artifactMetaData = restClient.getArtifactMetaData(artifactId);
                Assertions.assertNotNull(artifactMetaData);
                Assertions.assertEquals("myname", artifactMetaData.getName());
                Assertions.assertEquals(3, artifactMetaData.getProperties().size());
                Assertions.assertTrue(artifactMetaData.getProperties().keySet().containsAll(artifactProperties.keySet()));
                for(String key: artifactMetaData.getProperties().keySet()) {
                    Assertions.assertTrue(artifactMetaData.getProperties().get(key).equals(artifactProperties.get(key)));
                }
            });
        } finally {
            restClient.deleteArtifact(artifactId);
        }
    }

    @RegistryRestClientTest
    void nameOrderingTest(RegistryRestClient restClient) throws Exception {
        final String firstArtifactId = generateArtifactId();
        final String secondArtifactId = generateArtifactId();
        final String thirdArtifactId = "cccTestorder";

        try {
            // warm-up
            restClient.listArtifacts();

            // Create artifact 1
            String firstName = "aaaTestorder" + ThreadLocalRandom.current().nextInt(1000000);
            ByteArrayInputStream artifactData = new ByteArrayInputStream(
                    ("{\"type\":\"record\",\"title\":\"" + firstName + "\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}")
                            .getBytes(StandardCharsets.UTF_8));

            ArtifactMetaData amd = restClient.createArtifact(firstArtifactId, ArtifactType.JSON, artifactData);
            long id = amd.getGlobalId();
            
            this.waitForGlobalId(id);

            // Create artifact 2

            String secondName = "bbbTestorder" + ThreadLocalRandom.current().nextInt(1000000);
            ByteArrayInputStream secondData = new ByteArrayInputStream(
                    ("{\"type\":\"record\",\"title\":\"" + secondName + "\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}")
                            .getBytes(StandardCharsets.UTF_8));
            
            ArtifactMetaData secondCs = restClient.createArtifact(secondArtifactId, ArtifactType.JSON, secondData);
            long secondId = secondCs.getGlobalId();

            this.waitForGlobalId(secondId);
            
            // Create artifact 3
            ByteArrayInputStream thirdData = new ByteArrayInputStream(
                    ("{\"openapi\":\"3.0.2\",\"info\":{\"description\":\"testorder\"}}")
                            .getBytes(StandardCharsets.UTF_8));
            ArtifactMetaData thirdCs = restClient.createArtifact(thirdArtifactId, ArtifactType.OPENAPI, thirdData);
            long thirdId = thirdCs.getGlobalId();

            this.waitForGlobalId(thirdId);

            retry(() -> {
                ArtifactMetaData artifactMetaData = restClient.getArtifactMetaDataByGlobalId(thirdId);
                Assertions.assertNotNull(artifactMetaData);
                Assertions.assertEquals("testorder", artifactMetaData.getDescription());
            });

            ArtifactSearchResults ascResults = restClient.searchArtifacts("Testorder", SearchOver.everything, SortOrder.asc, 0, 5);
            Assertions.assertNotNull(ascResults);
            Assertions.assertEquals(3, ascResults.getCount());
            Assertions.assertEquals(3, ascResults.getArtifacts().size());
            Assertions.assertEquals(firstName, ascResults.getArtifacts().get(0).getName());
            Assertions.assertEquals(secondName, ascResults.getArtifacts().get(1).getName());
            Assertions.assertNull(ascResults.getArtifacts().get(2).getName());

            ArtifactSearchResults descResults = restClient.searchArtifacts("Testorder", SearchOver.everything, SortOrder.desc, 0, 5);
            Assertions.assertNotNull(descResults);
            Assertions.assertEquals(3, descResults.getCount());
            Assertions.assertEquals(3, descResults.getArtifacts().size());
            Assertions.assertNull(descResults.getArtifacts().get(0).getName());
            Assertions.assertEquals(secondName, descResults.getArtifacts().get(1).getName());
            Assertions.assertEquals(firstName, descResults.getArtifacts().get(2).getName());

            ArtifactSearchResults searchIdOverName = restClient.searchArtifacts(firstArtifactId, SearchOver.name, SortOrder.asc, 0, 5);
            Assertions.assertEquals(firstName, searchIdOverName.getArtifacts().get(0).getName());
            Assertions.assertEquals(firstArtifactId, searchIdOverName.getArtifacts().get(0).getId());

        } finally {
            restClient.deleteArtifact(firstArtifactId);
            restClient.deleteArtifact(secondArtifactId);
            restClient.deleteArtifact(thirdArtifactId);
        }
    }
}
