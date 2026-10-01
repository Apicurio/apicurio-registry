package io.apicurio.registry.types.provider.configured;

import io.apicurio.registry.json.rules.compatibility.ApitomyJsonSchemaCompatibilityChecker;
import io.apicurio.registry.json.rules.compatibility.JsonSchemaCompatibilityChecker;
import io.apicurio.registry.types.ArtifactType;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@code apicurio.compat.json-schema.use-apitomy} selects the JSON Schema compatibility checker.
 * Both values are covered: the standard JSON provider is built with the legacy checker, so checking
 * only that {@code false} gives the legacy checker would pass even if the property did nothing.
 */
class ArtifactTypeUtilProviderImplTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void useApitomySelectsTheJsonSchemaCompatibilityChecker(boolean useApitomy, @TempDir Path dir) {
        var log = mock(Logger.class);
        var factory = new ArtifactTypeUtilProviderImpl();
        factory.log = log;
        // No artifact types configuration file, so the standard providers are loaded.
        factory.configFile = dir.resolve("absent.json").toString();
        factory.useApitomyJsonSchemaChecker = useApitomy;

        factory.init();

        var checker = factory.getArtifactTypeProvider(ArtifactType.JSON).getCompatibilityChecker();
        if (useApitomy) {
            assertInstanceOf(ApitomyJsonSchemaCompatibilityChecker.class, checker);
        } else {
            assertInstanceOf(JsonSchemaCompatibilityChecker.class, checker);
        }
        // Opting out of the default is the deprecated choice, so only that one is warned about.
        verify(log, useApitomy ? never() : times(1)).warn(contains("deprecated"));
    }
}
