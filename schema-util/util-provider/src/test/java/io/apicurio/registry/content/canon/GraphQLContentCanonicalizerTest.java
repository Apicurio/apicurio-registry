package io.apicurio.registry.content.canon;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.graphql.content.canon.GraphQLContentCanonicalizer;
import io.apicurio.registry.types.ContentTypes;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphQLContentCanonicalizerTest {

    private final GraphQLContentCanonicalizer canonicalizer = new GraphQLContentCanonicalizer();

    private TypedContent createGraphQL(String sdl) {
        return TypedContent.create(ContentHandle.create(sdl), ContentTypes.APPLICATION_GRAPHQL);
    }

    @Test
    void testCanonicalizeReprintsPlainSchema() {
        String input = """
                type Query {
                  user(id: ID!): User
                }

                type User {
                  id: ID!
                }
                """;

        TypedContent result = canonicalizer.canonicalize(createGraphQL(input), Collections.emptyMap());
        assertEquals(ContentTypes.APPLICATION_GRAPHQL, result.getContentType());
        assertTrue(result.getContent().content().contains("type Query"));
    }

    @Test
    void testCanonicalizePreservesImportDirective() {
        // Content using the #import convention must be returned unchanged, since SchemaPrinter would
        // otherwise silently drop the (non-standard) import comment when reprinting the schema.
        String input = """
                #import "common-types.graphql"

                type Query {
                  user(id: ID!): User
                }

                type User {
                  id: ID!
                  profile: Profile
                }
                """;

        TypedContent content = createGraphQL(input);
        TypedContent result = canonicalizer.canonicalize(content, Collections.emptyMap());

        assertEquals(content.getContent().content(), result.getContent().content());
        assertTrue(result.getContent().content().contains("#import \"common-types.graphql\""));
    }

    @Test
    void testCanonicalizePreservesNamedImportDirective() {
        String input = """
                # import Profile from "common-types.graphql"

                type Query {
                  user(id: ID!): User
                }

                type User {
                  id: ID!
                  profile: Profile
                }
                """;

        TypedContent content = createGraphQL(input);
        TypedContent result = canonicalizer.canonicalize(content, Collections.emptyMap());

        assertEquals(content.getContent().content(), result.getContent().content());
    }
}
