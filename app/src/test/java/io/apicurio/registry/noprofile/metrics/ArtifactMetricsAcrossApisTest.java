package io.apicurio.registry.noprofile.metrics;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.metrics.OTelMetricsProvider;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * The artifact and version counters count each write once, whichever API it came through.
 */
@QuarkusTest
class ArtifactMetricsAcrossApisTest extends AbstractResourceTestBase {

    private static final String AVRO = "{\"type\": \"record\", \"name\": \"R\", \"fields\": []}";

    @InjectMock
    OTelMetricsProvider metrics;

    @Test
    void v3Create() throws Exception {
        String group = "metrics-v3-" + UUID.randomUUID();
        createArtifact(group, "a", ArtifactType.AVRO, AVRO, ContentTypes.APPLICATION_JSON);
        verify(metrics, times(1)).recordArtifactCreated(group, ArtifactType.AVRO);
        verify(metrics, times(1)).recordVersionCreated(group, ArtifactType.AVRO);
    }

    @Test
    void v2Create() {
        String group = "metrics-v2-" + UUID.randomUUID();
        given().when().contentType(CT_JSON).header("X-Registry-ArtifactId", "a")
                .header("X-Registry-ArtifactType", ArtifactType.AVRO).pathParam("g", group).body(AVRO)
                .post("/registry/v2/groups/{g}/artifacts").then().statusCode(200);
        verify(metrics, times(1)).recordArtifactCreated(group, ArtifactType.AVRO);
        verify(metrics, times(1)).recordVersionCreated(group, ArtifactType.AVRO);
    }

    @Test
    void confluentCompatibleCreate() throws Exception {
        String subject = "metrics-ccompat-" + UUID.randomUUID();
        confluentClient.registerSchema(AVRO, subject);
        // Subjects are artifacts in the default group, stored with a null group ID
        verify(metrics, times(1)).recordArtifactCreated(null, ArtifactType.AVRO);
        verify(metrics, times(1)).recordVersionCreated(null, ArtifactType.AVRO);
    }
}
