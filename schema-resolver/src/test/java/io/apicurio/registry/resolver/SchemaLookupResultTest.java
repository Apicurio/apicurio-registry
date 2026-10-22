package io.apicurio.registry.resolver;

import io.apicurio.registry.resolver.strategy.ArtifactReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SchemaLookupResultTest {
    @Test
    void testWiresContentHashIntoArtifactReference() {
        SchemaLookupResult<Object> result = SchemaLookupResult.builder().contentHash("content hash").build();

        ArtifactReference reference = result.toArtifactReference();

        assertEquals("content hash", reference.getContentHash());
    }
}
