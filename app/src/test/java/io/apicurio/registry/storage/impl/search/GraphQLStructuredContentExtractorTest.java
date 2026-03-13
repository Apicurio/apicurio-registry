package io.apicurio.registry.storage.impl.search;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.extract.StructuredElement;
import io.apicurio.registry.graphql.content.extract.GraphQLStructuredContentExtractor;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphQLStructuredContentExtractorTest {

    private final GraphQLStructuredContentExtractor extractor = new GraphQLStructuredContentExtractor();

    @Test
    void testExtractObjectTypesAndFields() {
        String schema = """
                type User {
                    id: ID!
                    name: String!
                    email: String
                }

                type Post {
                    title: String!
                    content: String
                    author: User!
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(schema));

        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("type") && e.name().equals("User")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("type") && e.name().equals("Post")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("field") && e.name().equals("id")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("field") && e.name().equals("name")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("field") && e.name().equals("email")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("field") && e.name().equals("title")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("field") && e.name().equals("content")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("field") && e.name().equals("author")));
    }

    @Test
    void testExtractEnums() {
        String schema = """
                enum Status {
                    ACTIVE
                    INACTIVE
                    PENDING
                }

                enum Role {
                    ADMIN
                    USER
                    GUEST
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(schema));

        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("enum") && e.name().equals("Status")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("enum") && e.name().equals("Role")));
    }

    @Test
    void testExtractInputTypes() {
        String schema = """
                input CreateUserInput {
                    name: String!
                    email: String!
                }

                input UpdateUserInput {
                    name: String
                    email: String
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(schema));

        assertTrue(
                elements.stream().anyMatch(e -> e.kind().equals("input") && e.name().equals("CreateUserInput")));
        assertTrue(
                elements.stream().anyMatch(e -> e.kind().equals("input") && e.name().equals("UpdateUserInput")));
    }

    @Test
    void testExtractInterfaces() {
        String schema = """
                interface Node {
                    id: ID!
                }

                interface Timestamped {
                    createdAt: String!
                    updatedAt: String!
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(schema));

        assertTrue(
                elements.stream().anyMatch(e -> e.kind().equals("interface") && e.name().equals("Node")));
        assertTrue(
                elements.stream().anyMatch(e -> e.kind().equals("interface") && e.name().equals("Timestamped")));
    }

    @Test
    void testExtractUnions() {
        String schema = """
                type Cat {
                    name: String!
                    purrs: Boolean
                }

                type Dog {
                    name: String!
                    barks: Boolean
                }

                union Pet = Cat | Dog
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(schema));

        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("union") && e.name().equals("Pet")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("type") && e.name().equals("Cat")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("type") && e.name().equals("Dog")));
    }

    @Test
    void testExtractDirectives() {
        String schema = """
                directive @auth(requires: Role!) on FIELD_DEFINITION

                directive @deprecated(reason: String) on FIELD_DEFINITION | ENUM_VALUE

                enum Role {
                    ADMIN
                    USER
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(schema));

        assertTrue(
                elements.stream().anyMatch(e -> e.kind().equals("directive") && e.name().equals("auth")));
        assertTrue(
                elements.stream().anyMatch(e -> e.kind().equals("directive") && e.name().equals("deprecated")));
    }

    @Test
    void testExtractComprehensiveSchema() {
        String schema = """
                interface Node {
                    id: ID!
                }

                enum Status {
                    ACTIVE
                    INACTIVE
                }

                type User implements Node {
                    id: ID!
                    name: String!
                    email: String
                    status: Status
                }

                type Post implements Node {
                    id: ID!
                    title: String!
                    author: User!
                }

                input CreatePostInput {
                    title: String!
                    content: String!
                }

                union SearchResult = User | Post

                directive @cacheControl(maxAge: Int) on FIELD_DEFINITION

                type Query {
                    user(id: ID!): User
                    posts: [Post!]!
                    search(term: String!): [SearchResult!]!
                }

                type Mutation {
                    createPost(input: CreatePostInput!): Post!
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(schema));

        // Object types (Query and Mutation should be excluded)
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("type") && e.name().equals("User")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("type") && e.name().equals("Post")));
        assertTrue(elements.stream().noneMatch(e -> e.kind().equals("type") && e.name().equals("Query")));
        assertTrue(
                elements.stream().noneMatch(e -> e.kind().equals("type") && e.name().equals("Mutation")));

        // Fields
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("field") && e.name().equals("name")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("field") && e.name().equals("title")));

        // Enum
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("enum") && e.name().equals("Status")));

        // Interface
        assertTrue(
                elements.stream().anyMatch(e -> e.kind().equals("interface") && e.name().equals("Node")));

        // Input
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("input") && e.name().equals("CreatePostInput")));

        // Union
        assertTrue(
                elements.stream().anyMatch(e -> e.kind().equals("union") && e.name().equals("SearchResult")));

        // Directive
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("directive") && e.name().equals("cacheControl")));
    }

    @Test
    void testExtractFromInvalidContent() {
        List<StructuredElement> elements = extractor.extract(ContentHandle.create("not valid graphql {{{"));

        assertTrue(elements.isEmpty());
    }

    @Test
    void testRootTypesExcluded() {
        String schema = """
                type Query {
                    hello: String
                }

                type Mutation {
                    doSomething: Boolean
                }

                type Subscription {
                    onEvent: String
                }

                type User {
                    id: ID!
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(schema));

        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("type") && e.name().equals("User")));
        assertTrue(elements.stream().noneMatch(e -> e.kind().equals("type") && e.name().equals("Query")));
        assertTrue(
                elements.stream().noneMatch(e -> e.kind().equals("type") && e.name().equals("Mutation")));
        assertTrue(elements.stream()
                .noneMatch(e -> e.kind().equals("type") && e.name().equals("Subscription")));
    }
}
