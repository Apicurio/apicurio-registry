package io.apicurio.registry.cluster;

import static io.apicurio.registry.cluster.support.ClusterUtils.getClusterProperties;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.client.RegistryRestClient;
import io.apicurio.registry.client.RegistryRestClientFactory;
import io.apicurio.registry.cluster.support.ClusterUtils;
import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.rest.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.beans.EditableMetaData;
import io.apicurio.registry.rest.beans.Rule;
import io.apicurio.registry.rest.beans.SearchOver;
import io.apicurio.registry.rest.beans.SortOrder;
import io.apicurio.registry.support.HealthResponse;
import io.apicurio.registry.support.HealthUtils;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.confluent.kafka.schemaregistry.ParsedSchema;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.quarkus.runtime.configuration.QuarkusConfigFactory;
import io.smallrye.config.SmallRyeConfig;

/**
 * @author Ales Justin
 */
public class ClusterIT {

    @BeforeAll
    public static void startCluster() throws Exception {
        // hack around Quarkus core config factory ...
        ClassLoader systemCL = new ClassLoader(null) {};
        Config config = ConfigProviderResolver.instance().getConfig(systemCL);
        QuarkusConfigFactory.setConfig((SmallRyeConfig) config);

        ClusterUtils.startCluster();
    }

    @AfterAll
    public static void stopCluster() {
        ClusterUtils.stopCluster();
    }

    private void testReadiness(int port) throws Exception {
        HealthUtils.assertHealthCheck(port, HealthUtils.Type.READY, HealthResponse.Status.UP);
    }

    @Test
    public void testReadiness() throws Exception {
        Properties properties = getClusterProperties();
        Assumptions.assumeTrue(properties != null);

        testReadiness(8080);
        testReadiness(8081);
    }

    @Test
    public void testSmoke() throws Exception {
        Properties properties = getClusterProperties();
        Assumptions.assumeTrue(properties != null);

        RegistryRestClient client1 = RegistryRestClientFactory.create("http://localhost:8080/api");
        RegistryRestClient client2 = RegistryRestClientFactory.create("http://localhost:8081/api");

        // warm-up both nodes (its storages)
        client1.listArtifacts();
        client2.listArtifacts();

        String artifactId = UUID.randomUUID().toString();
        ByteArrayInputStream stream = new ByteArrayInputStream("{\"name\":\"redhat\"}".getBytes(StandardCharsets.UTF_8));
        long globalId = client1.createArtifact(artifactId, ArtifactType.JSON, stream).getGlobalId();
        try {
            TestUtils.retry(() -> {
                ArtifactMetaData amd = client2.getArtifactMetaData(artifactId);
                Assertions.assertEquals(1, amd.getVersion());
            }, "ClusterIT-SmokeTest-CreateArtifact", 10);

            String name = UUID.randomUUID().toString();
            String desc = UUID.randomUUID().toString();

            EditableMetaData emd = new EditableMetaData();
            emd.setName(name);
            emd.setDescription(desc);
            client1.updateArtifactMetaData(artifactId, emd);

            TestUtils.retry(() -> {
                ArtifactMetaData amd = client2.getArtifactMetaDataByGlobalId(globalId);
                Assertions.assertEquals(name, amd.getName());
                Assertions.assertEquals(desc, amd.getDescription());
            });

            Rule rule = new Rule();
            rule.setType(RuleType.VALIDITY);
            rule.setConfig("myconfig");
            client1.createArtifactRule(artifactId, rule);

            TestUtils.retry(() -> {
                Rule config = client2.getArtifactRuleConfig(artifactId, RuleType.VALIDITY);
                Assertions.assertEquals(rule.getConfig(), config.getConfig());
            });

            List<Long> v1 = client1.listArtifactVersions(artifactId);
            List<Long> v2 = client2.listArtifactVersions(artifactId);
            Assertions.assertEquals(v1, v2);

            List<RuleType> rt1 = client1.listArtifactRules(artifactId);
            List<RuleType> rt2 = client2.listArtifactRules(artifactId);
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
            client1.deleteArtifact(artifactId);
        }

        client1.close();
        client2.close();
    }

    @Test
    public void testConfluent() throws Exception {
        Properties properties = getClusterProperties();
        Assumptions.assumeTrue(properties != null);

        SchemaRegistryClient client1 = new CachedSchemaRegistryClient("http://localhost:8080/api/ccompat", 3);
        SchemaRegistryClient client2 = new CachedSchemaRegistryClient("http://localhost:8081/api/ccompat", 3);

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
        Properties properties = getClusterProperties();
        Assumptions.assumeTrue(properties != null);

        RegistryRestClient client1 = RegistryRestClientFactory.create("http://localhost:8080/api");
        RegistryRestClient client2 = RegistryRestClientFactory.create("http://localhost:8081/api");

        // warm-up both nodes (its storages)
        client1.listArtifacts();
        client2.listArtifacts();

        String artifactId = UUID.randomUUID().toString();
        ByteArrayInputStream stream = new ByteArrayInputStream(("{\"name\":\"redhat\"}").getBytes(StandardCharsets.UTF_8));
        client1.createArtifact(artifactId, ArtifactType.JSON, stream);
        try {
            String name = UUID.randomUUID().toString();
            String desc = UUID.randomUUID().toString();

            TestUtils.retry(() -> {
                EditableMetaData emd = new EditableMetaData();
                emd.setName(name);
                emd.setDescription(desc);
                client2.updateArtifactMetaData(artifactId, emd);
            });

            TestUtils.retry(() -> {
                ArtifactSearchResults results = client2.searchArtifacts(name, SearchOver.name, SortOrder.asc, 0, 2);
                Assertions.assertNotNull(results);
                Assertions.assertEquals(1, results.getCount(), "Invalid results count -- name");
                Assertions.assertEquals(1, results.getArtifacts().size(), "Invalid artifacts size -- name");
                Assertions.assertEquals(name, results.getArtifacts().get(0).getName());
                Assertions.assertEquals(desc, results.getArtifacts().get(0).getDescription());
            });
            TestUtils.retry(() -> {
                // client 1 !
                ArtifactSearchResults results = client1.searchArtifacts(desc, SearchOver.description, SortOrder.desc, 0, 2);
                Assertions.assertNotNull(results);
                Assertions.assertEquals(1, results.getCount(), "Invalid results count -- description");
                Assertions.assertEquals(1, results.getArtifacts().size(), "Invalid artifacts size -- description");
                Assertions.assertEquals(name, results.getArtifacts().get(0).getName());
                Assertions.assertEquals(desc, results.getArtifacts().get(0).getDescription());
            });
            TestUtils.retry(() -> {
                ArtifactSearchResults results = client2.searchArtifacts(desc, SearchOver.everything, SortOrder.desc, 0, 2);
                Assertions.assertNotNull(results);
                Assertions.assertEquals(1, results.getCount(), "Invalid results count -- everything");
                Assertions.assertEquals(1, results.getArtifacts().size(), "Invalid artifacts size -- everything");
                Assertions.assertEquals(name, results.getArtifacts().get(0).getName());
                Assertions.assertEquals(desc, results.getArtifacts().get(0).getDescription());
            });
        } finally {
            client1.deleteArtifact(artifactId);
        }

        client1.close();
        client2.close();
    }
}
