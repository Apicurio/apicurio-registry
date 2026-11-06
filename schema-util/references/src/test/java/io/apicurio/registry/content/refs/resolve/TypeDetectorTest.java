package io.apicurio.registry.content.refs.resolve;

import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.types.provider.DefaultArtifactTypeUtilProviderImpl;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TypeDetectorTest {

    private final TypeDetector detector = new TypeDetector(new DefaultArtifactTypeUtilProviderImpl(true));

    @Test
    void avroReferencingUnknownTypeIsStillAvro() {
        SchemaSource s = InMemorySchemaSource.of("Trade.json",
                "{\"type\":\"record\",\"name\":\"Trade\",\"fields\":[{\"name\":\"x\",\"type\":\"com.a.Missing\"}]}",
                ContentTypes.APPLICATION_JSON);
        assertEquals(Optional.of(ArtifactType.AVRO), detector.detect(s));
    }

    @Test
    void explicitTypeWins() {
        SchemaSource s = InMemorySchemaSource.of("x.json", "{}", ContentTypes.APPLICATION_JSON, ArtifactType.JSON);
        assertEquals(Optional.of(ArtifactType.JSON), detector.detect(s));
    }

    @Test
    void detectsJsonSchemaAsyncApiProtoAndGraphql() {
        assertEquals(Optional.of(ArtifactType.JSON), detector.detect(InMemorySchemaSource.of("a.json",
                "{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\"}", ContentTypes.APPLICATION_JSON)));
        assertEquals(Optional.of(ArtifactType.ASYNCAPI), detector.detect(InMemorySchemaSource.of("a.yaml",
                "asyncapi: 2.6.0\ninfo:\n  title: t\n  version: '1'\nchannels: {}\n", ContentTypes.APPLICATION_YAML)));
        assertEquals(Optional.of(ArtifactType.PROTOBUF), detector.detect(InMemorySchemaSource.of("a.proto",
                "syntax = \"proto3\"; message A { string b = 1; }", ContentTypes.APPLICATION_PROTOBUF)));
        assertEquals(Optional.of(ArtifactType.GRAPHQL), detector.detect(InMemorySchemaSource.of("a.graphql",
                "type Query { a: String }", ContentTypes.APPLICATION_GRAPHQL)));
    }

    @Test
    void undetectableIsEmpty() {
        assertEquals(Optional.empty(), detector.detect(InMemorySchemaSource.of("a.json", "[1,2]",
                ContentTypes.APPLICATION_JSON)));
    }
}
