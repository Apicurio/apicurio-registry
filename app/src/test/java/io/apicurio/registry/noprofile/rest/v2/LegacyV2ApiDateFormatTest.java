package io.apicurio.registry.noprofile.rest.v2;

import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.resolver.client.RegistryArtifactReference;
import io.apicurio.registry.resolver.client.RegistryClientFacadeImpl_v2;
import io.apicurio.registry.types.ArtifactType;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
@TestProfile(LegacyV2ApiDateFormatTest.LegacyV2DateFormatTestProfile.class)
class LegacyV2ApiDateFormatTest extends AbstractResourceTestBase {

    private static final String GROUP = "LegacyV2ApiTest";

    public static class LegacyV2DateFormatTestProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("apicurio.apis.date-format", "yyyy-MM-dd'T'HH:mm:ssZ");
        }
    }

    @Test
    void testClientV2FailsLegacyDateFormatParsing() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");
        String artifactId = "testLegacyPropertiesWithLabels";
        Set<RegistryArtifactReference> references = Collections.emptySet();

        try (var clientFacadeV2 = new RegistryClientFacadeImpl_v2(clientV2)) {
            assertThrows(DateTimeParseException.class, () -> clientFacadeV2.createSchema(
                    ArtifactType.OPENAPI,
                    GROUP,
                    artifactId,
                    null, //version
                    "FAIL",
                    true,
                    artifactContent,
                    references));
        }
    }
}
