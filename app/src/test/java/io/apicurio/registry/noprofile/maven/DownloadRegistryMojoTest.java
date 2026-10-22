package io.apicurio.registry.noprofile.maven;

import io.apicurio.registry.maven.DownloadArtifact;
import io.apicurio.registry.maven.DownloadRegistryMojo;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.avro.Schema;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@QuarkusTest
public class DownloadRegistryMojoTest extends RegistryMojoTestBase {
    DownloadRegistryMojo mojo;

    @BeforeEach
    public void createMojo() {
        this.mojo = new DownloadRegistryMojo();
        this.mojo.setRegistryUrl(TestUtils.getRegistryV3ApiUrl(testPort));
    }

    @Test
    public void testDownloadIds() throws Exception {
        String groupId = DownloadRegistryMojoTest.class.getName();
        String artifactId = generateArtifactId();

        Schema schema = Schema.createUnion(
                Arrays.asList(Schema.create(Schema.Type.STRING), Schema.create(Schema.Type.NULL)));
        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.AVRO,
                schema.toString(), ContentTypes.APPLICATION_JSON);
        clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);

        List<DownloadArtifact> artifacts = new ArrayList<>();
        DownloadArtifact artifact = new DownloadArtifact();
        artifact.setGroupId(groupId);
        artifact.setArtifactId(artifactId);
        artifact.setOverwrite(true);
        artifact.setFile(new File(this.tempDirectory, "avro-schema.avsc"));
        artifacts.add(artifact);

        Assertions.assertFalse(artifact.getFile().isFile());

        mojo.setArtifacts(artifacts);
        mojo.execute();

        Assertions.assertTrue(artifact.getFile().isFile());
    }

}
