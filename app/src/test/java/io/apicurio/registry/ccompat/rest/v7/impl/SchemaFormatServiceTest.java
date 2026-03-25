package io.apicurio.registry.ccompat.rest.v7.impl;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.types.ArtifactType;
import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;

class SchemaFormatServiceTest {

    private final SchemaFormatService service = new SchemaFormatService();

    @Test
    void shouldRejectFormatForCustomArtifactType() {
        BadRequestException exception = Assertions.assertThrows(BadRequestException.class,
                () -> service.applyFormat(ContentHandle.create("{}"), "MODEL_SCHEMA", "resolved",
                        Collections.emptyMap()));

        Assertions.assertTrue(exception.getMessage().contains("Invalid format 'resolved'"));
    }

    @Test
    void shouldRejectLowercaseBuiltInArtifactType() {
        BadRequestException exception = Assertions.assertThrows(BadRequestException.class,
                () -> service.applyFormat(ContentHandle.create("{}"), "avro", "resolved",
                        Collections.emptyMap()));

        Assertions.assertTrue(exception.getMessage().contains("Invalid format 'resolved'"));
    }

    @Test
    void shouldPassThroughCustomArtifactTypeWithoutFormat() {
        ContentHandle content = ContentHandle.create("{\"openapi\":\"3.0.2\"}");

        ContentHandle result = service.applyFormat(content, "MODEL_SCHEMA", null, Collections.emptyMap());

        Assertions.assertEquals(content.content(), result.content());
    }

    @Test
    void shouldPassThroughBuiltInArtifactTypeWithoutFormat() {
        ContentHandle content = ContentHandle.create("{\"openapi\":\"3.0.2\"}");

        ContentHandle result = service.applyFormat(content, ArtifactType.OPENAPI, null, Collections.emptyMap());

        Assertions.assertEquals(content.content(), result.content());
    }
}
