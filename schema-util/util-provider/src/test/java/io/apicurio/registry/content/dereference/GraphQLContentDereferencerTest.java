package io.apicurio.registry.content.dereference;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.graphql.content.dereference.GraphQLContentDereferencer;
import io.apicurio.registry.rules.validity.ArtifactUtilProviderTestBase;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

public class GraphQLContentDereferencerTest extends ArtifactUtilProviderTestBase {

    private static final String URL =
            "https://registry.example.com/apis/registry/v3/groups/g/artifacts/common-types/versions/1/content?references=REWRITE";

    @Test
    public void testDereference() {
        TypedContent content = resourceToTypedContentHandle("graphql-to-deref.graphql");
        TypedContent common = resourceToTypedContentHandle("common-types.graphql");
        TypedContent result = new GraphQLContentDereferencer().dereference(content,
                Map.of("common-types.graphql", common));
        Assertions.assertEquals(resourceToString("expected-testDereference-graphql.graphql"),
                result.getContent().content());
        Assertions.assertEquals(content.getContentType(), result.getContentType());
    }

    @Test
    public void testDereferenceNoMatchReturnsOriginal() {
        TypedContent content = resourceToTypedContentHandle("graphql-to-deref.graphql");
        Assertions.assertSame(content,
                new GraphQLContentDereferencer().dereference(content, Map.of("other.graphql",
                        resourceToTypedContentHandle("common-types.graphql"))));
        Assertions.assertSame(content, new GraphQLContentDereferencer().dereference(content, Map.of()));
    }

    @Test
    public void testRewriteReferences() {
        TypedContent content = resourceToTypedContentHandle("graphql-to-deref.graphql");
        TypedContent result = new GraphQLContentDereferencer().rewriteReferences(content,
                Map.of("common-types.graphql", URL));
        Assertions.assertEquals(resourceToString("expected-testRewrite-graphql.graphql"),
                result.getContent().content());
        Assertions.assertEquals(content.getContentType(), result.getContentType());
    }

    @Test
    public void testRewriteReferencesNoMatchReturnsOriginal() {
        TypedContent content = resourceToTypedContentHandle("graphql-to-deref.graphql");
        Assertions.assertSame(content, new GraphQLContentDereferencer().rewriteReferences(content,
                Map.of("other.graphql", URL)));
        Assertions.assertSame(content, new GraphQLContentDereferencer().rewriteReferences(content, Map.of()));
    }
}
