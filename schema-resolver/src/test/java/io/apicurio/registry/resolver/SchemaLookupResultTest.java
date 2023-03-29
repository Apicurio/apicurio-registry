package io.apicurio.registry.resolver;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.apicurio.registry.resolver.strategy.ArtifactReference;

public class SchemaLookupResultTest {
    @Test
    void testWiresContentHashIntoArtifactReference() {
        SchemaLookupResult<Object> result = SchemaLookupResult.builder().contentHash("content hash").build();

        ArtifactReference reference = result.toArtifactReference();

        assertEquals("content hash", reference.getContentHash());
    }
}
