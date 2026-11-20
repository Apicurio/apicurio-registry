package io.apicurio.registry.content.refs.resolve;

import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.types.provider.DefaultArtifactTypeUtilProviderImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FormatCoverageTest {

    private static final String XSD = "<?xml version=\"1.0\"?>"
            + "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">"
            + "<xs:element name=\"note\" type=\"xs:string\"/></xs:schema>";

    private static final String WSDL = "<?xml version=\"1.0\"?>"
            + "<definitions xmlns=\"http://schemas.xmlsoap.org/wsdl/\" name=\"Svc\" "
            + "targetNamespace=\"urn:svc\"><message name=\"Req\"/></definitions>";

    @Test
    void graphQlRootWithoutReferencesPlansSingleArtifact() {
        SchemaSource gql = InMemorySchemaSource.of("schema.graphql", "type Query { hello: String }",
                ContentTypes.APPLICATION_GRAPHQL);
        RegistrationPlan plan = ReferenceResolver.plan(List.of(RootArtifact.of("schema.graphql", "g", "gql")),
                SchemaSourceSet.of(gql), PlanOptions.builder().build());

        assertEquals(1, plan.artifacts().size());
        PlannedArtifact artifact = plan.artifacts().get(0);
        assertEquals(ArtifactType.GRAPHQL, artifact.artifactType());
        assertEquals(new ArtifactCoordinates("g", "gql"), artifact.coordinates());
        assertEquals(0, artifact.references().size());
    }

    @Test
    void xsdAndWsdlSourcesAreIndexed() {
        SchemaSource xsd = InMemorySchemaSource.of("note.xsd", XSD, ContentTypes.APPLICATION_XML);
        SchemaSource wsdl = InMemorySchemaSource.of("svc.wsdl", WSDL, ContentTypes.APPLICATION_XML);
        SourceIndex index = SourceIndex.build(SchemaSourceSet.of(xsd, wsdl), List.of(),
                new TypeDetector(new DefaultArtifactTypeUtilProviderImpl(true)));

        assertEquals(ArtifactType.XSD, index.bySource(xsd).orElseThrow().artifactType());
        assertEquals(ArtifactType.WSDL, index.bySource(wsdl).orElseThrow().artifactType());
    }
}
