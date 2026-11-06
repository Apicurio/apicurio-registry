package io.apicurio.registry.content.refs.resolve;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InMemorySchemaSourceTest {

    @Test
    void threeArgOfNormalizesLocationAndHasNoArtifactType() {
        InMemorySchemaSource source = InMemorySchemaSource.of("./a\\b.json", "{\"x\":1}", "application/json");

        assertEquals("a/b.json", source.location());
        assertEquals("{\"x\":1}", source.content().getContent().content());
        assertEquals("application/json", source.content().getContentType());
        assertEquals(Optional.empty(), source.artifactType());
        assertEquals(List.of("a/b.json"), source.aliases());
    }

    @Test
    void fourArgOfKeepsArtifactType() {
        InMemorySchemaSource source = InMemorySchemaSource.of("/x.proto", "syntax = \"proto3\";",
                "application/x-protobuf", "PROTOBUF");

        assertEquals("x.proto", source.location());
        assertEquals("syntax = \"proto3\";", source.content().getContent().content());
        assertEquals("application/x-protobuf", source.content().getContentType());
        assertEquals(Optional.of("PROTOBUF"), source.artifactType());
    }
}
