package io.apicurio.registry.content.refs;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.graphql.content.refs.GraphQLReferenceFinder;
import io.apicurio.registry.rules.validity.ArtifactUtilProviderTestBase;
import io.apicurio.registry.types.ContentTypes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

public class GraphQLReferenceFinderTest extends ArtifactUtilProviderTestBase {

    @Test
    public void testFindImportReference() {
        Set<ExternalReference> refs = new GraphQLReferenceFinder()
                .findExternalReferences(resourceToTypedContentHandle("graphql-with-import.graphql"));
        Assertions.assertEquals(Set.of(new ExternalReference("common-types.graphql")), refs);
    }

    @Test
    public void testFindNamedImportReference() {
        Set<ExternalReference> refs = new GraphQLReferenceFinder().findExternalReferences(
                resourceToTypedContentHandle("graphql-with-named-import.graphql"));
        Assertions.assertEquals(Set.of(new ExternalReference("common-types.graphql")), refs);
    }

    @Test
    public void testNoImportsReturnsEmptySet() {
        Set<ExternalReference> refs = new GraphQLReferenceFinder()
                .findExternalReferences(resourceToTypedContentHandle("graphql-no-import.graphql"));
        Assertions.assertTrue(refs.isEmpty());
    }

    @Test
    public void testImportLookingTextInsideDescriptionIsIgnored() {
        Set<ExternalReference> refs = new GraphQLReferenceFinder().findExternalReferences(
                resourceToTypedContentHandle("graphql-import-inside-description.graphql"));
        Assertions.assertTrue(refs.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 32, 33, 1000 })
    public void testNamedImportWithAnyAmountOfSeparatorWhitespace(int spaces) {
        String sdl = "# import Profile from" + " ".repeat(spaces) + "\"common-types.graphql\"\ntype Query { a: Int }\n";
        Set<ExternalReference> refs = new GraphQLReferenceFinder().findExternalReferences(
                TypedContent.create(ContentHandle.create(sdl), ContentTypes.APPLICATION_GRAPHQL));
        Assertions.assertEquals(Set.of(new ExternalReference("common-types.graphql")), refs);
    }

    @Test
    public void testImportWithoutFromBeforeFileIsNotAReference() {
        String sdl = "#import Profile \"common-types.graphql\"\ntype Query { a: Int }\n";
        Set<ExternalReference> refs = new GraphQLReferenceFinder().findExternalReferences(
                TypedContent.create(ContentHandle.create(sdl), ContentTypes.APPLICATION_GRAPHQL));
        Assertions.assertEquals(Set.of(), refs);
    }
}
