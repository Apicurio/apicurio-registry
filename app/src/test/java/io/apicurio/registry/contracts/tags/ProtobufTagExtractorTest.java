package io.apicurio.registry.contracts.tags;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.types.ArtifactType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

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

    @ParameterizedTest(name = "{0}")
    @MethodSource("fieldTagTestCases")
    void testFieldTags(String testName, String proto, String expectedField, Set<String> expectedTags) {
        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create(proto));

        assertEquals(1, tags.size());
        assertEquals(expectedTags, tags.get(expectedField));
    }

    private static Stream<Arguments> fieldTagTestCases() {
        return Stream.of(
                Arguments.of(
                        "oneOfFieldTags",
                        """
                        syntax = "proto3";
                        message Event {
                          oneof payload {
                            // @tag:PII
                            string user_data = 1;
                            string system_data = 2;
                          }
                        }
                        """,
                        "user_data",
                        Set.of("PII")
                ),
                Arguments.of(
                        "repeatedNestedMessageTags",
                        """
                        syntax = "proto3";
                        message UserList {
                          repeated User items = 1;
                          message User {
                            // @tag:PII
                            string ssn = 1;
                          }
                        }
                        """,
                        "items[].ssn",
                        Set.of("PII")
                ),
                Arguments.of(
                        "mapFieldTags",
                        """
                        syntax = "proto3";
                        message Account {
                          // @tag:SENSITIVE
                          map<string, string> metadata = 1;
                        }
                        """,
                        "metadata",
                        Set.of("SENSITIVE")
                ),
                Arguments.of(
                        "mapNestedMessageTags",
                        """
                        syntax = "proto3";
                        message Account {
                          map<string, User> users = 1;
                          message User {
                            // @tag:PII
                            string ssn = 1;
                          }
                        }
                        """,
                        "users.values.ssn",
                        Set.of("PII")
                )
        );
    }
}
