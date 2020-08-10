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

package io.apicurio.registry;

import static io.apicurio.registry.utils.tests.TestUtils.retry;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import io.apicurio.registry.auth.KeycloakResourceManager;
import io.quarkus.test.common.QuarkusTestResource;
import org.junit.jupiter.api.Assertions;

import io.apicurio.registry.client.RegistryService;
import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.rest.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.beans.EditableMetaData;
import io.apicurio.registry.rest.beans.SearchOver;
import io.apicurio.registry.rest.beans.SortOrder;
import io.apicurio.registry.rest.beans.VersionMetaData;
import io.apicurio.registry.rest.beans.VersionSearchResults;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.ConcurrentUtil;
import io.apicurio.registry.utils.tests.RegistryServiceTest;
import io.quarkus.test.junit.QuarkusTest;

/**
 * @author Ales Justin
 */
@QuarkusTest
@QuarkusTestResource(KeycloakResourceManager.class)
public class RegistryClientTest extends AbstractResourceTestBase {

    @RegistryServiceTest
    public void testSmoke(Supplier<RegistryService> supplier) {
        RegistryService service = supplier.get();

        service.deleteAllGlobalRules();

        Assertions.assertNotNull(service.toString());
        Assertions.assertEquals(service.hashCode(), service.hashCode());
        Assertions.assertEquals(service, service);
    }

    @RegistryServiceTest
    public void testAsyncCRUD(Supplier<RegistryService> supplier) throws Exception {
        String artifactId = generateArtifactId();
        try {
            ByteArrayInputStream stream = new ByteArrayInputStream("{\"name\":\"redhat\"}".getBytes(StandardCharsets.UTF_8));
            CompletionStage<ArtifactMetaData> csResult = supplier.get().createArtifact(ArtifactType.JSON, artifactId, null, stream);
            ConcurrentUtil.result(csResult);
            waitForArtifact(artifactId);

            EditableMetaData emd = new EditableMetaData();
            emd.setName("myname");
            supplier.get().updateArtifactMetaData(artifactId, emd);
            retry(() -> {
                ArtifactMetaData artifactMetaData = supplier.get().getArtifactMetaData(artifactId);
                Assertions.assertNotNull(artifactMetaData);
                Assertions.assertEquals("myname", artifactMetaData.getName());
            });

            stream = new ByteArrayInputStream("{\"name\":\"ibm\"}".getBytes(StandardCharsets.UTF_8));
            csResult = supplier.get().updateArtifact(artifactId, ArtifactType.JSON, stream);
            ConcurrentUtil.result(csResult);
        } finally {
//            supplier.get().deleteArtifact(artifactId);
        }
    }

    @RegistryServiceTest
    void testSearchArtifact(Supplier<RegistryService> supplier) throws Exception {
        RegistryService client = supplier.get();

        // warm-up
        client.listArtifacts();

        String artifactId = UUID.randomUUID().toString();
        String name = "n" + ThreadLocalRandom.current().nextInt(1000000);
        ByteArrayInputStream artifactData = new ByteArrayInputStream(
                ("{\"type\":\"record\",\"title\":\""+ name + "\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}")
                        .getBytes(StandardCharsets.UTF_8));

        CompletionStage<ArtifactMetaData> cs = client.createArtifact(ArtifactType.JSON, artifactId, null, artifactData);
        long id = ConcurrentUtil.result(cs).getGlobalId();
        
        this.waitForGlobalId(id);

        retry(() -> {
            ArtifactMetaData artifactMetaData = client.getArtifactMetaDataByGlobalId(id);
            Assertions.assertNotNull(artifactMetaData);
        });

        ArtifactSearchResults results = client.searchArtifacts(name.toUpperCase(), 0, 2, SearchOver.name, SortOrder.asc);
        Assertions.assertNotNull(results);
        Assertions.assertEquals(1, results.getCount());
        Assertions.assertEquals(1, results.getArtifacts().size());
        Assertions.assertEquals(name, results.getArtifacts().get(0).getName());
    }

    @RegistryServiceTest
    void testSearchVersion(Supplier<RegistryService> supplier) throws Exception {
        RegistryService client = supplier.get();

        // warm-up
        client.listArtifacts();

        String artifactId = UUID.randomUUID().toString();
        String name = "n" + ThreadLocalRandom.current().nextInt(1000000);
        ByteArrayInputStream artifactData = new ByteArrayInputStream(
                ("{\"type\":\"record\",\"title\":\""+ name + "\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}")
                        .getBytes(StandardCharsets.UTF_8));

        CompletionStage<ArtifactMetaData> amd = client.createArtifact(ArtifactType.JSON, artifactId, null, artifactData);
        long id1 = ConcurrentUtil.result(amd).getGlobalId();
        
        this.waitForGlobalId(id1);

        retry(() -> {
            ArtifactMetaData artifactMetaData = client.getArtifactMetaDataByGlobalId(id1);
            Assertions.assertNotNull(artifactMetaData);
        });

        artifactData.reset(); // a must between usage!!

        CompletionStage<VersionMetaData> vmd = client.createArtifactVersion(artifactId, ArtifactType.JSON, artifactData);
        long id2 = ConcurrentUtil.result(vmd).getGlobalId();
        
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

    @RegistryServiceTest
    public void testLabels(Supplier<RegistryService> supplier) throws Exception {
        String artifactId = generateArtifactId();
        RegistryService client = supplier.get();
        try {
            ByteArrayInputStream stream = new ByteArrayInputStream("{\"name\":\"redhat\"}".getBytes(StandardCharsets.UTF_8));
            CompletionStage<ArtifactMetaData> csResult = client.createArtifact(ArtifactType.JSON, artifactId, null, stream);
            ConcurrentUtil.result(csResult);
            
            this.waitForArtifact(artifactId);

            EditableMetaData emd = new EditableMetaData();
            emd.setName("myname");

            final List<String> artifactLabels = Arrays.asList("Open Api", "Awesome Artifact", "JSON");
            emd.setLabels(artifactLabels);
            client.updateArtifactMetaData(artifactId, emd);

            retry(() -> {
                ArtifactMetaData artifactMetaData = client.getArtifactMetaData(artifactId);
                Assertions.assertNotNull(artifactMetaData);
                Assertions.assertEquals("myname", artifactMetaData.getName());
                Assertions.assertEquals(3, artifactMetaData.getLabels().size());
                Assertions.assertTrue(artifactMetaData.getLabels().containsAll(artifactLabels));
            });

            retry((() -> {

                ArtifactSearchResults results = client
                        .searchArtifacts("open api", 0, 2, SearchOver.labels, SortOrder.asc);
                Assertions.assertNotNull(results);
                Assertions.assertEquals(1, results.getCount());
                Assertions.assertEquals(1, results.getArtifacts().size());
                Assertions.assertTrue(results.getArtifacts().get(0).getLabels().containsAll(artifactLabels));
            }));
        } finally {
            client.deleteArtifact(artifactId);
        }
    }

    @RegistryServiceTest
    void nameOrderingTest(Supplier<RegistryService> supplier) throws Exception {
        final String firstArtifactId = generateArtifactId();
        final String secondArtifactId = generateArtifactId();
        final String thirdArtifactId = "cccTestorder";
        RegistryService client = supplier.get();

        try {
            // warm-up
            client.listArtifacts();

            // Create artifact 1
            String firstName = "aaaTestorder" + ThreadLocalRandom.current().nextInt(1000000);
            ByteArrayInputStream artifactData = new ByteArrayInputStream(
                    ("{\"type\":\"record\",\"title\":\"" + firstName + "\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}")
                            .getBytes(StandardCharsets.UTF_8));

            CompletionStage<ArtifactMetaData> cs = client.createArtifact(ArtifactType.JSON, firstArtifactId, null, artifactData);
            long id = ConcurrentUtil.result(cs).getGlobalId();
            
            this.waitForGlobalId(id);

            // Create artifact 2

            // Create artifact 2

            // Create artifact 2

            String secondName = "bbbTestorder" + ThreadLocalRandom.current().nextInt(1000000);
            ByteArrayInputStream secondData = new ByteArrayInputStream(
                    ("{\"type\":\"record\",\"title\":\"" + secondName + "\",\"fields\":[{\"name\":\"foo\",\"type\":\"string\"}]}")
                            .getBytes(StandardCharsets.UTF_8));
            
            CompletionStage<ArtifactMetaData> secondCs = client.createArtifact(ArtifactType.JSON, secondArtifactId, null, secondData);
            long secondId = ConcurrentUtil.result(secondCs).getGlobalId();

            this.waitForGlobalId(secondId);
            
            // Create artifact 3
            ByteArrayInputStream thirdData = new ByteArrayInputStream(
                    ("{\"openapi\":\"3.0.2\",\"info\":{\"description\":\"testorder\"}}")
                            .getBytes(StandardCharsets.UTF_8));
            CompletionStage<ArtifactMetaData> thirdCs = client.createArtifact(ArtifactType.OPENAPI, thirdArtifactId, null, thirdData);
            long thirdId = ConcurrentUtil.result(thirdCs).getGlobalId();

            this.waitForGlobalId(thirdId);

            retry(() -> {
                ArtifactMetaData artifactMetaData = client.getArtifactMetaDataByGlobalId(thirdId);
                Assertions.assertNotNull(artifactMetaData);
                Assertions.assertEquals("testorder", artifactMetaData.getDescription());
            });

            ArtifactSearchResults ascResults = client.searchArtifacts("Testorder", 0, 5, SearchOver.everything, SortOrder.asc);
            Assertions.assertNotNull(ascResults);
            Assertions.assertEquals(3, ascResults.getCount());
            Assertions.assertEquals(3, ascResults.getArtifacts().size());
            Assertions.assertEquals(firstName, ascResults.getArtifacts().get(0).getName());
            Assertions.assertEquals(secondName, ascResults.getArtifacts().get(1).getName());
            Assertions.assertNull(ascResults.getArtifacts().get(2).getName());

            ArtifactSearchResults descResults = client.searchArtifacts("Testorder", 0, 5, SearchOver.everything, SortOrder.desc);
            Assertions.assertNotNull(descResults);
            Assertions.assertEquals(3, descResults.getCount());
            Assertions.assertEquals(3, descResults.getArtifacts().size());
            Assertions.assertNull(descResults.getArtifacts().get(0).getName());
            Assertions.assertEquals(secondName, descResults.getArtifacts().get(1).getName());
            Assertions.assertEquals(firstName, descResults.getArtifacts().get(2).getName());

            ArtifactSearchResults searchIdOverName = client.searchArtifacts(firstArtifactId, 0, 5, SearchOver.name, SortOrder.asc);
            Assertions.assertEquals(firstName, ascResults.getArtifacts().get(0).getName());
            Assertions.assertEquals(firstArtifactId, ascResults.getArtifacts().get(0).getId());

        } finally {
            client.deleteArtifact(firstArtifactId);
            client.deleteArtifact(secondArtifactId);
            client.deleteArtifact(thirdArtifactId);
        }
    }
}
