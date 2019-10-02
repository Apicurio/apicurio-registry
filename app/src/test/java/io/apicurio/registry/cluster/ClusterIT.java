package io.apicurio.registry.cluster;

import io.apicurio.registry.client.RegistryClient;
import io.apicurio.registry.client.RegistryService;
import io.apicurio.registry.cluster.support.ClusterUtils;
import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.types.ArtifactType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.apicurio.registry.cluster.support.ClusterUtils.getClusterProperties;

import java.io.ByteArrayInputStream;
import java.util.Properties;
import java.util.UUID;

/**
 * @author Ales Justin
 */
public class ClusterIT {

    @BeforeAll
    public static void startCluster() throws Exception {
        ClusterUtils.startCluster();
    }

    @AfterAll
    public static void stopCluster() {
        ClusterUtils.stopCluster();
    }

    @Test
    public void testSmoke() throws Exception {
        Properties properties = getClusterProperties();
        Assumptions.assumeTrue(properties != null);

        RegistryService client1 = RegistryClient.create("http://localhost:8080");
        RegistryService client2 = RegistryClient.create("http://localhost:8081");

        String artifactId = UUID.randomUUID().toString();
        ByteArrayInputStream stream = new ByteArrayInputStream("{\"name\":\"\"}".getBytes());
        try {
            client1.createArtifact(ArtifactType.JSON, artifactId, stream);

            Thread.sleep(1000); // dummy wait

            ArtifactMetaData amd = client2.getArtifactMetaData(artifactId);
            Assertions.assertEquals(1, amd.getVersion());
        } finally {
            client1.deleteArtifact(artifactId);
        }
    }
}
