package io.apicurio.registry.content.refs;

import io.apicurio.registry.graphql.content.refs.GraphQLReferenceFinder;
import io.apicurio.registry.rules.validity.ArtifactUtilProviderTestBase;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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
}
