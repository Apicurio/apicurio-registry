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

package io.apicurio.tests.clustered;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.RegistryClientFactory;
import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v2.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.v2.beans.EditableMetaData;
import io.apicurio.registry.rest.v2.beans.Rule;
import io.apicurio.registry.rest.v2.beans.SortBy;
import io.apicurio.registry.rest.v2.beans.SortOrder;
import io.apicurio.registry.rest.v2.beans.VersionSearchResults;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.ApicurioV2BaseIT;
import io.apicurio.tests.common.Constants;
import io.confluent.kafka.schemaregistry.ParsedSchema;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;

/**
 * @author Fabian Martinez
 */
@Tag(Constants.CLUSTERED)
public class ClusteredRegistryIT  extends ApicurioV2BaseIT {

    private final String groupId = getClass().getSimpleName();

    @Test
    public void testSmoke() throws Exception {

        RegistryClient client1 = RegistryClientFactory.create("http://localhost:" + TestUtils.getRegistryPort() + "/apis/registry/v2");
        int node2port = TestUtils.getRegistryPort() + 1;
        RegistryClient client2 = RegistryClientFactory.create("http://localhost:" + node2port + "/apis/registry/v2");

        // warm-up both nodes (its storages)
        client1.listArtifactsInGroup("testSmoke");
        client2.listArtifactsInGroup("testSmoke");

        String artifactId = UUID.randomUUID().toString();
        ByteArrayInputStream stream = new ByteArrayInputStream("{\"name\":\"redhat\"}".getBytes(StandardCharsets.UTF_8));
        client1.createArtifact(groupId, artifactId, ArtifactType.JSON, stream).getGlobalId();
        try {
            TestUtils.retry(() -> {
                ArtifactMetaData amd = client2.getArtifactMetaData(groupId, artifactId);
                Assertions.assertEquals("1", amd.getVersion());
            }, "ClusterIT-SmokeTest-CreateArtifact", 10);

            String name = UUID.randomUUID().toString();
            String desc = UUID.randomUUID().toString();

            EditableMetaData emd = new EditableMetaData();
            emd.setName(name);
            emd.setDescription(desc);
            client1.updateArtifactMetaData(groupId, artifactId, emd);

            TestUtils.retry(() -> {
                ArtifactMetaData amd = client2.getArtifactMetaData(groupId, artifactId);
                Assertions.assertEquals(name, amd.getName());
                Assertions.assertEquals(desc, amd.getDescription());
            });

            Rule rule = new Rule();
            rule.setType(RuleType.VALIDITY);
            rule.setConfig("myconfig");
            client1.createArtifactRule(groupId, artifactId, rule);

            TestUtils.retry(() -> {
                Rule config = client2.getArtifactRuleConfig(groupId, artifactId, RuleType.VALIDITY);
                Assertions.assertEquals(rule.getConfig(), config.getConfig());
            });

            VersionSearchResults vres1 = client1.listArtifactVersions(groupId, artifactId, 0, 500);
            VersionSearchResults vres2 = client2.listArtifactVersions(groupId, artifactId, 0, 500);
            Assertions.assertEquals(vres1.getCount(), vres2.getCount());

            List<RuleType> rt1 = client1.listArtifactRules(groupId, artifactId);
            List<RuleType> rt2 = client2.listArtifactRules(groupId, artifactId);
            Assertions.assertEquals(rt1, rt2);

            Rule globalRule = new Rule();
            globalRule.setType(RuleType.COMPATIBILITY);
            globalRule.setConfig("gc");
            client1.createGlobalRule(globalRule);
            try {
                TestUtils.retry(() -> {
                    List<RuleType> grts = client2.listGlobalRules();
                    Assertions.assertTrue(grts.contains(globalRule.getType()));
                });
            } finally {
                client1.deleteGlobalRule(RuleType.COMPATIBILITY);
            }
        } finally {
            client1.deleteArtifact(groupId, artifactId);
        }
    }

    @Test
    public void testConfluent() throws Exception {

        SchemaRegistryClient client1 = new CachedSchemaRegistryClient("http://localhost:" + TestUtils.getRegistryPort() + "/apis/ccompat/v6", 3);
        int node2port = TestUtils.getRegistryPort() + 1;
        SchemaRegistryClient client2 = new CachedSchemaRegistryClient("http://localhost:" + node2port + "/apis/ccompat/v6", 3);

        String subject = UUID.randomUUID().toString();
        ParsedSchema schema = new AvroSchema("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"f1\",\"type\":\"string\"}]}");
        int id = client1.register(subject, schema);
        try {
            TestUtils.retry(() -> {
                Collection<String> allSubjects = client2.getAllSubjects();
                Assertions.assertTrue(allSubjects.contains(subject));
            }, "ClusterIT-SmokeTest-RegisterSchema-1", 10);

            TestUtils.retry(() -> {
                ParsedSchema s = client2.getSchemaById(id);
                Assertions.assertNotNull(s);
            }, "ClusterIT-SmokeTest-RegisterSchema-2", 10);
        } finally {
            client1.deleteSchemaVersion(subject, "1");
        }
    }

    @Test
    public void testSearch() throws Exception {

        RegistryClient client1 = RegistryClientFactory.create("http://localhost:" + TestUtils.getRegistryPort() + "/apis/registry/v2");
        int node2port = TestUtils.getRegistryPort() + 1;
        RegistryClient client2 = RegistryClientFactory.create("http://localhost:" + node2port + "/apis/registry/v2");

        // warm-up both nodes (its storages)
        client1.listArtifactsInGroup(groupId);
        client2.listArtifactsInGroup(groupId);

        String artifactId = UUID.randomUUID().toString();
        ByteArrayInputStream stream = new ByteArrayInputStream(("{\"name\":\"redhat\"}").getBytes(StandardCharsets.UTF_8));
        client1.createArtifact(groupId, artifactId, ArtifactType.JSON, stream);
        try {
            String name = UUID.randomUUID().toString();
            String desc = UUID.randomUUID().toString();

            TestUtils.retry(() -> {
                EditableMetaData emd = new EditableMetaData();
                emd.setName(name);
                emd.setDescription(desc);
                client2.updateArtifactMetaData(groupId, artifactId, emd);
            });

            TestUtils.retry(() -> {
                ArtifactSearchResults results = client2.searchArtifacts(groupId, name, null, null, null, SortBy.name, SortOrder.asc, 0, 2);
                Assertions.assertNotNull(results);
                Assertions.assertEquals(1, results.getCount(), "Invalid results count -- name");
                Assertions.assertEquals(1, results.getArtifacts().size(), "Invalid artifacts size -- name");
                Assertions.assertEquals(name, results.getArtifacts().get(0).getName());
                Assertions.assertEquals(desc, results.getArtifacts().get(0).getDescription());
            });
            TestUtils.retry(() -> {
                // client 1 !
                ArtifactSearchResults results = client1.searchArtifacts(groupId, null, desc, null, null, SortBy.name, SortOrder.asc, 0, 2);
                Assertions.assertNotNull(results);
                Assertions.assertEquals(1, results.getCount(), "Invalid results count -- description");
                Assertions.assertEquals(1, results.getArtifacts().size(), "Invalid artifacts size -- description");
                Assertions.assertEquals(name, results.getArtifacts().get(0).getName());
                Assertions.assertEquals(desc, results.getArtifacts().get(0).getDescription());
            });
        } finally {
            client1.deleteArtifact(groupId, artifactId);
        }
    }

    @Test
    public void testGetContentByGlobalId() throws Exception {

        RegistryClient client1 = RegistryClientFactory.create("http://localhost:" + TestUtils.getRegistryPort() + "/apis/registry/v2");
        int node2port = TestUtils.getRegistryPort() + 1;
        RegistryClient client2 = RegistryClientFactory.create("http://localhost:" + node2port + "/apis/registry/v2");

        // warm-up both nodes (its storages)
        client1.listArtifactsInGroup(groupId);
        client2.listArtifactsInGroup(groupId);

        String artifactId = UUID.randomUUID().toString();
        String secondId = UUID.randomUUID().toString();
        String thirdId = UUID.randomUUID().toString();

        ByteArrayInputStream first = new ByteArrayInputStream(("{\"name\":\"redhat\"}").getBytes(StandardCharsets.UTF_8));
        ByteArrayInputStream second = new ByteArrayInputStream(("{\"name\":\"ibm\"}").getBytes(StandardCharsets.UTF_8));
        ByteArrayInputStream third = new ByteArrayInputStream(("{\"name\":\"company\"}").getBytes(StandardCharsets.UTF_8));
        final ArtifactMetaData firstArtifact = client1.createArtifact(groupId, artifactId, ArtifactType.JSON, first);
        final ArtifactMetaData secondArtifact = client2.createArtifact(groupId, secondId, ArtifactType.JSON, second);
        final ArtifactMetaData thirdArtifact = client1.createArtifact(groupId, thirdId, ArtifactType.JSON, third);

        try {
            String name = UUID.randomUUID().toString();
            String desc = UUID.randomUUID().toString();

            TestUtils.retry(() -> {
                EditableMetaData emd = new EditableMetaData();
                emd.setName(name);
                emd.setDescription(desc);
                client2.updateArtifactMetaData(groupId, artifactId, emd);
            });

            TestUtils.retry(() -> {
                client2.getContentByGlobalId(firstArtifact.getGlobalId());
                client1.getContentByGlobalId(secondArtifact.getGlobalId());
                client2.getContentByGlobalId(thirdArtifact.getGlobalId());
            });
        } finally {
            client1.deleteArtifact(groupId, artifactId);
            client1.deleteArtifact(groupId, secondId);
            client1.deleteArtifact(groupId, thirdId);
        }
    }

}