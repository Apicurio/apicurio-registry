package io.apicurio.registry.contracts.tags;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.types.ArtifactType;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtobufTagExtractorTest {

    private final ProtobufTagExtractor extractor = new ProtobufTagExtractor();

    @Test
    void testGetArtifactType() {
        assertEquals(ArtifactType.PROTOBUF, extractor.getArtifactType());
    }

    @Test
    void testCommentBasedTags() {
        String proto = """
                syntax = "proto3";
                message User {
                  // @tag:PII,SENSITIVE
                  string ssn = 1;
                  string name = 2;
                }
                """;

        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create(proto));

        assertEquals(1, tags.size());
        assertEquals(Set.of("PII", "SENSITIVE"), tags.get("ssn"));
    }

    @Test
    void testMultipleCommentTags() {
        String proto = """
                syntax = "proto3";
                message User {
                  // @tag:PII
                  string ssn = 1;
                  // @tag:EMAIL
                  string email = 2;
                  string name = 3;
                }
                """;

        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create(proto));

        assertEquals(2, tags.size());
        assertEquals(Set.of("PII"), tags.get("ssn"));
        assertEquals(Set.of("EMAIL"), tags.get("email"));
    }

    @Test
    void testNoTags() {
        String proto = """
                syntax = "proto3";
                message User {
                  string name = 1;
                  int32 age = 2;
                }
                """;

        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create(proto));

        assertTrue(tags.isEmpty());
    }

    @Test
    void testInvalidProto() {
        Map<String, Set<String>> tags = extractor
                .extractTags(ContentHandle.create("not valid protobuf {{{"));

        assertTrue(tags.isEmpty());
    }

    @Test
    void testOneOfFieldTags() {
        String proto = """
                syntax = "proto3";
                message Event {
                  oneof payload {
                    // @tag:PII
                    string user_data = 1;
                    string system_data = 2;
                  }
                }
                """;

        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create(proto));

        assertEquals(1, tags.size());
        assertEquals(Set.of("PII"), tags.get("user_data"));
    }

    @Test
    void testMultipleTagAnnotations() {
        String proto = """
                syntax = "proto3";
                message User {
                  // Some documentation about the field.
                  // @tag:PII
                  // More notes. @tag:SENSITIVE
                  string ssn = 1;
                }
                """;

        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create(proto));

        assertEquals(1, tags.size());
        assertEquals(Set.of("PII", "SENSITIVE"), tags.get("ssn"));
    }
}
