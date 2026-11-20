package io.apicurio.registry.types.provider.configured;

import io.apicurio.registry.json.rules.compatibility.ApitomyJsonSchemaCompatibilityChecker;
import io.apicurio.registry.types.ArtifactType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;

/**
 * JSON Schema compatibility is checked with Apitomy Data Models. The legacy everit-based checker
 * was removed, and {@code apicurio.compat.json-schema.use-apitomy} no longer selects anything.
 */
class ArtifactTypeUtilProviderImplTest {

    @Test
    void jsonSchemaIsCheckedWithDataModels(@TempDir Path dir) {
        var factory = new ArtifactTypeUtilProviderImpl();
        factory.log = mock(Logger.class);
        // No artifact types configuration file, so the standard providers are loaded.
        factory.configFile = dir.resolve("absent.json").toString();

        factory.init();

        assertInstanceOf(ApitomyJsonSchemaCompatibilityChecker.class,
                factory.getArtifactTypeProvider(ArtifactType.JSON).getCompatibilityChecker());
    }
}
