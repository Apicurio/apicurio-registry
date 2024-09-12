package io.apicurio.registry.noprofile.maven;

import io.apicurio.registry.maven.RegisterArtifact;
import io.apicurio.registry.maven.RegisterRegistryMojo;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@QuarkusTest
public class RegistryMojoWithMinifyTest extends RegistryMojoTestBase {
    RegisterRegistryMojo registerMojo;

    @BeforeEach
    public void createMojo() {
        this.registerMojo = new RegisterRegistryMojo();
        this.registerMojo.setRegistryUrl(TestUtils.getRegistryV3ApiUrl(testPort));
    }

    @Test
    public void testMinify() throws Exception {
        String groupId = "RegisterWithMinifyRegistryMojoTest";

        File avroFile = new File(getClass().getResource("avro.avsc").getFile());

        RegisterArtifact avroMinifiedArtifact = new RegisterArtifact();
        avroMinifiedArtifact.setGroupId(groupId);
        avroMinifiedArtifact.setArtifactId("userInfoMinified");
        avroMinifiedArtifact.setArtifactType(ArtifactType.AVRO);
        avroMinifiedArtifact.setMinify(true);
        avroMinifiedArtifact.setFile(avroFile);

        RegisterArtifact avroNotMinifiedArtifact = new RegisterArtifact();
        avroNotMinifiedArtifact.setGroupId(groupId);
        avroNotMinifiedArtifact.setArtifactId("userInfoNotMinified");
        avroNotMinifiedArtifact.setArtifactType(ArtifactType.AVRO);
        avroNotMinifiedArtifact.setFile(avroFile);

        registerMojo.setArtifacts(List.of(avroMinifiedArtifact, avroNotMinifiedArtifact));
        registerMojo.execute();

        // Wait for the artifact to be created.
        InputStream artifactInputStream = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId("userInfoMinified").versions().byVersionExpression("branch=latest").content()
                .get();
        String artifactContent = new String(artifactInputStream.readAllBytes(), StandardCharsets.UTF_8);
        Assertions.assertEquals(
                "{\"type\":\"record\",\"name\":\"userInfo\",\"namespace\":\"my.example\",\"fields\":[{\"name\":\"age\",\"type\":\"int\"}]}",
                artifactContent);

        // Wait for the artifact to be created.
        artifactInputStream = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId("userInfoNotMinified").versions().byVersionExpression("branch=latest").content()
                .get();
        artifactContent = new String(artifactInputStream.readAllBytes(), StandardCharsets.UTF_8);
        Assertions.assertEquals(
                "{\n" + "  \"type\" : \"record\",\n" + "  \"name\" : \"userInfo\",\n"
                        + "  \"namespace\" : \"my.example\",\n"
                        + "  \"fields\" : [{\"name\" : \"age\", \"type\" : \"int\"}]\n" + "}",
                artifactContent);
    }

}
