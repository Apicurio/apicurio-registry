package io.apicurio.registry.cluster;

import io.apicurio.registry.client.RegistryClient;
import io.apicurio.registry.client.RegistryService;
import io.apicurio.registry.cluster.support.ClusterUtils;
import io.apicurio.registry.rest.beans.*;
import io.apicurio.registry.support.HealthResponse;
import io.apicurio.registry.support.HealthUtils;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.quarkus.runtime.configuration.QuarkusConfigFactory;
import io.smallrye.config.SmallRyeConfig;
import org.apache.avro.Schema;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.junit.jupiter.api.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Properties;
import java.util.UUID;

import static io.apicurio.registry.cluster.support.ClusterUtils.getClusterProperties;

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

        RegistryService client1 = RegistryClient.create("http://localhost:8080/api");
        RegistryService client2 = RegistryClient.create("http://localhost:8081/api");

        // warm-up both nodes (its storages)
        client1.listArtifacts();
        client2.listArtifacts();

        String artifactId = UUID.randomUUID().toString();
        ByteArrayInputStream stream = new ByteArrayInputStream("{\"name\":\"redhat\"}".getBytes(StandardCharsets.UTF_8));
        client1.createArtifact(ArtifactType.JSON, artifactId, null, stream);
        try {
            TestUtils.retry(() -> {
                ArtifactMetaData amd = client2.getArtifactMetaData(artifactId);
                Assertions.assertEquals(1, amd.getVersion());
            });
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
        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"f1\",\"type\":\"string\"}]}");
        client1.register(subject, schema);
        try {
            TestUtils.retry(() -> {
                Collection<String> allSubjects = client2.getAllSubjects();
                Assertions.assertTrue(allSubjects.contains(subject));
            });
        } finally {
            client1.deleteSubject(subject);
        }
    }

    @Test
    public void testSearch() throws Exception {
        Properties properties = getClusterProperties();
        Assumptions.assumeTrue(properties != null);

        RegistryService client1 = RegistryClient.create("http://localhost:8080/api");
        RegistryService client2 = RegistryClient.create("http://localhost:8081/api");

        // warm-up both nodes (its storages)
        client1.listArtifacts();
        client2.listArtifacts();

        String artifactId = UUID.randomUUID().toString();
        ByteArrayInputStream stream = new ByteArrayInputStream(("{\"name\":\"redhat\"}").getBytes(StandardCharsets.UTF_8));
        client1.createArtifact(ArtifactType.JSON, artifactId, null, stream);
        try {
            String name = UUID.randomUUID().toString();
            String desc = UUID.randomUUID().toString();

            EditableMetaData emd = new EditableMetaData();
            emd.setName(name);
            emd.setDescription(desc);
            client1.updateArtifactMetaData(artifactId, emd);

            TestUtils.retry(() -> {
                ArtifactSearchResults results = client2.searchArtifacts(name, 0, 2, SearchOver.name, SortOrder.asc);
                Assertions.assertNotNull(results);
                Assertions.assertEquals(1, results.getCount());
                Assertions.assertEquals(1, results.getArtifacts().size());
                Assertions.assertEquals(name, results.getArtifacts().get(0).getName());
                Assertions.assertEquals(desc, results.getArtifacts().get(0).getDescription());
            });
            TestUtils.retry(() -> {
                // client 1 !
                ArtifactSearchResults results = client1.searchArtifacts(desc, 0, 2, SearchOver.description, SortOrder.desc);
                Assertions.assertNotNull(results);
                Assertions.assertEquals(1, results.getCount());
                Assertions.assertEquals(1, results.getArtifacts().size());
                Assertions.assertEquals(name, results.getArtifacts().get(0).getName());
                Assertions.assertEquals(desc, results.getArtifacts().get(0).getDescription());
            });
            TestUtils.retry(() -> {
                ArtifactSearchResults results = client2.searchArtifacts(desc, 0, 2, SearchOver.everything, SortOrder.desc);
                Assertions.assertNotNull(results);
                Assertions.assertEquals(1, results.getCount());
                Assertions.assertEquals(1, results.getArtifacts().size());
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
