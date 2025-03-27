package io.apicurio.registry.customTypes;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.ArtifactTypeInfo;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@QuarkusTest
@TestProfile(CustomArtifactTypesTestProfile.class)
public class CustomArtifactTypesTest extends AbstractResourceTestBase {

    @Test
    public void testArtifactTypeList() {
        List<ArtifactTypeInfo> infos = clientV3.admin().config().artifactTypes().get();
        Assertions.assertNotNull(infos);
        Assertions.assertFalse(infos.isEmpty());
        infos.forEach(info -> {
            System.out.println(info.getName());
        });
        Set<ArtifactTypeInfo> raml = infos.stream().filter(info -> info.getName().equals("RAML")).collect(Collectors.toSet());
        Assertions.assertFalse(raml.isEmpty());
    }

}