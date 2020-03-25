package io.apicurio.registry.cluster;

import io.apicurio.registry.client.RegistryClient;
import io.apicurio.registry.client.RegistryService;
import io.apicurio.registry.cluster.support.ClusterUtils;
import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.support.HealthResponse;
import io.apicurio.registry.support.HealthUtils;
import io.apicurio.registry.types.ArtifactType;
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.quarkus.runtime.configuration.QuarkusConfigFactory;
import io.smallrye.config.SmallRyeConfig;
import org.apache.avro.Schema;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.apicurio.registry.cluster.support.ClusterUtils.getClusterProperties;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Properties;
import java.util.UUID;

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

        RegistryService client1 = RegistryClient.create("http://localhost:8080");
        RegistryService client2 = RegistryClient.create("http://localhost:8081");

        String artifactId = UUID.randomUUID().toString();
        ByteArrayInputStream stream = new ByteArrayInputStream("{\"name\":\"redhat\"}".getBytes(StandardCharsets.UTF_8));
        client1.createArtifact(ArtifactType.JSON, artifactId, stream);
        try {
            Thread.sleep(1000); // dummy wait

            ArtifactMetaData amd = client2.getArtifactMetaData(artifactId);
            Assertions.assertEquals(1, amd.getVersion());
        } finally {
            client1.deleteArtifact(artifactId);
        }
    }

    @Test
    public void testConfluent() throws Exception {
        Properties properties = getClusterProperties();
        Assumptions.assumeTrue(properties != null);

        SchemaRegistryClient client1 = new CachedSchemaRegistryClient("http://localhost:8080/ccompat", 3);
        SchemaRegistryClient client2 = new CachedSchemaRegistryClient("http://localhost:8081/ccompat", 3);

        String subject = UUID.randomUUID().toString();
        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"f1\",\"type\":\"string\"}]}");
        client1.register(subject, schema);
        try {
            Thread.sleep(1000); // dummy wait

            Collection<String> allSubjects = client2.getAllSubjects();
            Assertions.assertTrue(allSubjects.contains(subject));
        } finally {
            client1.deleteSubject(subject);
        }
    }
}
