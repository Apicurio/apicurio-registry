package io.apicurio.registry.util;

import io.apicurio.registry.AbstractRegistryTestBase;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.storage.error.InvalidArtifactTypeException;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProvider;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import io.apicurio.registry.types.provider.DefaultArtifactTypeUtilProviderImpl;
import org.apache.avro.Schema;
import org.apache.avro.Schema.Type;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArtifactTypeUtilTest extends AbstractRegistryTestBase {

    static ArtifactTypeUtilProviderFactory artifactTypeUtilProviderFactory;
    static {
        artifactTypeUtilProviderFactory = new DefaultArtifactTypeUtilProviderImpl(true);
    }

    /**
     * Test method for
     * {@link io.apicurio.registry.util.ArtifactTypeUtil#determineArtifactType(TypedContent, String, ArtifactTypeUtilProviderFactory)}
     */
    @Test
    void testDiscoverType_JSON() {
        TypedContent content = resourceToTypedContentHandle("json-schema.json");
        String type = ArtifactTypeUtil.determineArtifactType(content, null, artifactTypeUtilProviderFactory);
        Assertions.assertEquals(ArtifactType.JSON, type);
    }

    /**
     * Test method for
     * {@link io.apicurio.registry.util.ArtifactTypeUtil#determineArtifactType(TypedContent, String, ArtifactTypeUtilProviderFactory)}.
     */
    @Test
    void testDiscoverType_Avro() {
        TypedContent content = resourceToTypedContentHandle("avro.json");
        String type = ArtifactTypeUtil.determineArtifactType(content, null, artifactTypeUtilProviderFactory);
        Assertions.assertEquals(ArtifactType.AVRO, type);
    }

    /**
     * Test method for
     * {@link io.apicurio.registry.util.ArtifactTypeUtil#determineArtifactType(TypedContent, String, ArtifactTypeUtilProviderFactory)}.
     */
    @Test
    void testDiscoverType_Avro_Simple() {
        TypedContent content = resourceToTypedContentHandle("avro-simple.avsc");
        Schema s = new Schema.Parser().parse(content.getContent().content());
        assertEquals(Type.STRING, s.getType());

        String type = ArtifactTypeUtil.determineArtifactType(content, null, artifactTypeUtilProviderFactory);
        Assertions.assertEquals(ArtifactType.AVRO, type);
    }

    /**
     * Test method for
     * {@link io.apicurio.registry.util.ArtifactTypeUtil#determineArtifactType(TypedContent, String, ArtifactTypeUtilProviderFactory)}.
     */
    @Test
    void testDiscoverType_Proto() {
        TypedContent content = resourceToTypedContentHandle("protobuf.proto");
        String type = ArtifactTypeUtil.determineArtifactType(content, null, artifactTypeUtilProviderFactory);
        Assertions.assertEquals(ArtifactType.PROTOBUF, type);

        content = resourceToTypedContentHandle("protobuf.proto");
        type = ArtifactTypeUtil.determineArtifactType(content, null, artifactTypeUtilProviderFactory);
        Assertions.assertEquals(ArtifactType.PROTOBUF, type);
    }

    /**
     * Test method for
     * {@link io.apicurio.registry.util.ArtifactTypeUtil#determineArtifactType(TypedContent, String, ArtifactTypeUtilProviderFactory)}.
     */
    @Test
    void testDiscoverType_OpenApi() {
        TypedContent content = resourceToTypedContentHandle("openapi.json");
        String type = ArtifactTypeUtil.determineArtifactType(content, null, artifactTypeUtilProviderFactory);
        Assertions.assertEquals(ArtifactType.OPENAPI, type);

        content = resourceToTypedContentHandle("swagger.json");
        type = ArtifactTypeUtil.determineArtifactType(content, null, artifactTypeUtilProviderFactory);
        Assertions.assertEquals(ArtifactType.OPENAPI, type);

        content = resourceToTypedContentHandle("swagger.json");
        type = ArtifactTypeUtil.determineArtifactType(content, null, artifactTypeUtilProviderFactory);
        Assertions.assertEquals(ArtifactType.OPENAPI, type);
    }

    /**
     * Test method for
     * {@link io.apicurio.registry.util.ArtifactTypeUtil#determineArtifactType(TypedContent, String, ArtifactTypeUtilProviderFactory)}.
     */
    @Test
    void testDiscoverType_AsyncApi() {
        TypedContent content = resourceToTypedContentHandle("asyncapi.json");
        String type = ArtifactTypeUtil.determineArtifactType(content, null, artifactTypeUtilProviderFactory);
        Assertions.assertEquals(ArtifactType.ASYNCAPI, type);
    }

    /**
     * Test method for
     * {@link io.apicurio.registry.util.ArtifactTypeUtil#determineArtifactType(TypedContent, String, ArtifactTypeUtilProviderFactory)}.
     */
    @Test
    void testDiscoverType_GraphQL() {
        TypedContent content = resourceToTypedContentHandle("example.graphql");
        String type = ArtifactTypeUtil.determineArtifactType(content, null, artifactTypeUtilProviderFactory);
        Assertions.assertEquals(ArtifactType.GRAPHQL, type);
    }

    @Test
    void testDiscoverType_Thrift() {
        TypedContent content = resourceToTypedContentHandle("example.thrift");
        String type = ArtifactTypeUtil.determineArtifactType(content, null, artifactTypeUtilProviderFactory);
        Assertions.assertEquals(ArtifactType.THRIFT, type);
    }

    /**
     * Test method for
     * {@link io.apicurio.registry.util.ArtifactTypeUtil#determineArtifactType(TypedContent, String, ArtifactTypeUtilProviderFactory)}.
     */
    @Test
    void testDiscoverType_DefaultNotFound() {
        Assertions.assertThrows(InvalidArtifactTypeException.class, () -> {
            TypedContent content = resourceToTypedContentHandle("example.txt");
            ArtifactTypeUtil.determineArtifactType(content, null, artifactTypeUtilProviderFactory);
        });
    }

    /**
     * Test method for
     * {@link io.apicurio.registry.util.ArtifactTypeUtil#determineArtifactType(TypedContent, String, ArtifactTypeUtilProviderFactory)}.
     */
    @Test
    void testDiscoverType_Xml() {
        TypedContent content = resourceToTypedContentHandle("xml.xml");
        String type = ArtifactTypeUtil.determineArtifactType(content, null, artifactTypeUtilProviderFactory);
        Assertions.assertEquals(ArtifactType.XML, type);
    }

    /**
     * Test method for
     * {@link io.apicurio.registry.util.ArtifactTypeUtil#determineArtifactType(TypedContent, String, ArtifactTypeUtilProviderFactory)}.
     */
    @Test
    void testDiscoverType_Xsd() {
        TypedContent content = resourceToTypedContentHandle("xml-schema.xsd");
        String type = ArtifactTypeUtil.determineArtifactType(content, null, artifactTypeUtilProviderFactory);
        Assertions.assertEquals(ArtifactType.XSD, type);
    }

    /**
     * Test method for
     * {@link io.apicurio.registry.util.ArtifactTypeUtil#determineArtifactType(TypedContent, String, ArtifactTypeUtilProviderFactory)}.
     */
    @Test
    void testDiscoverType_Wsdl() {
        TypedContent content = resourceToTypedContentHandle("wsdl.wsdl");
        String type = ArtifactTypeUtil.determineArtifactType(content, null, artifactTypeUtilProviderFactory);
        Assertions.assertEquals(ArtifactType.WSDL, type);

        content = resourceToTypedContentHandle("wsdl-2.0.wsdl");
        type = ArtifactTypeUtil.determineArtifactType(content, null, artifactTypeUtilProviderFactory);
        Assertions.assertEquals(ArtifactType.WSDL, type);
    }

    @Test
    void testDetectArtifactTypeFromExtension_AllMappings() {
        assertEquals(ArtifactType.AVRO, ArtifactTypeUtil.detectArtifactTypeFromExtension("schema.avsc", artifactTypeUtilProviderFactory));
        assertEquals(ArtifactType.PROTOBUF, ArtifactTypeUtil.detectArtifactTypeFromExtension("schema.proto", artifactTypeUtilProviderFactory));
        assertEquals(ArtifactType.GRAPHQL, ArtifactTypeUtil.detectArtifactTypeFromExtension("schema.graphql", artifactTypeUtilProviderFactory));
        assertEquals(ArtifactType.GRAPHQL, ArtifactTypeUtil.detectArtifactTypeFromExtension("schema.gql", artifactTypeUtilProviderFactory));
        assertEquals(ArtifactType.WSDL, ArtifactTypeUtil.detectArtifactTypeFromExtension("service.wsdl", artifactTypeUtilProviderFactory));
        assertEquals(ArtifactType.XSD, ArtifactTypeUtil.detectArtifactTypeFromExtension("schema.xsd", artifactTypeUtilProviderFactory));
        assertEquals(ArtifactType.THRIFT, ArtifactTypeUtil.detectArtifactTypeFromExtension("service.thrift", artifactTypeUtilProviderFactory));
    }

    @Test
    void testDetectArtifactTypeFromExtension_CaseInsensitive() {
        assertEquals(ArtifactType.AVRO, ArtifactTypeUtil.detectArtifactTypeFromExtension("USER.AVSC", artifactTypeUtilProviderFactory));
        assertEquals(ArtifactType.PROTOBUF, ArtifactTypeUtil.detectArtifactTypeFromExtension("Schema.Proto", artifactTypeUtilProviderFactory));
        assertEquals(ArtifactType.GRAPHQL, ArtifactTypeUtil.detectArtifactTypeFromExtension("QUERY.GQL", artifactTypeUtilProviderFactory));
    }

    @Test
    void testDetectArtifactTypeFromExtension_AmbiguousReturnsNull() {
        assertNull(ArtifactTypeUtil.detectArtifactTypeFromExtension("schema.json", artifactTypeUtilProviderFactory));
        assertNull(ArtifactTypeUtil.detectArtifactTypeFromExtension("schema.yaml", artifactTypeUtilProviderFactory));
        assertNull(ArtifactTypeUtil.detectArtifactTypeFromExtension("schema.yml", artifactTypeUtilProviderFactory));
        assertNull(ArtifactTypeUtil.detectArtifactTypeFromExtension("schema.xml", artifactTypeUtilProviderFactory));
    }

    @Test
    void testDetectArtifactTypeFromExtension_UnknownReturnsNull() {
        assertNull(ArtifactTypeUtil.detectArtifactTypeFromExtension("schema.txt", artifactTypeUtilProviderFactory));
        assertNull(ArtifactTypeUtil.detectArtifactTypeFromExtension("noextension", artifactTypeUtilProviderFactory));
    }

    @Test
    void testDetectArtifactTypeFromExtension_ExplicitTypeWins() {
        final String result = ArtifactTypeUtil.determineArtifactType(
                resourceToTypedContentHandle("avro.json"), ArtifactType.AVRO, artifactTypeUtilProviderFactory);
        assertEquals(ArtifactType.AVRO, result);
    }

    @Test
    void testDuplicateFileExtensionAcrossProvidersFailsAtStartup() {
        final var factory = new DefaultArtifactTypeUtilProviderImpl(true) {
            public void validate() { validateFileExtensions(); }
            {
                // Add a fake provider that claims .avsc — already claimed by AVRO
                getAllArtifactTypeProviders().add(new ArtifactTypeUtilProvider() {
                    @Override public String getArtifactType() { return "MY_AVRO"; }
                    @Override public Set<String> getContentTypes() { return Set.of("application/json"); }
                    @Override public Set<String> getFileExtensions() { return Set.of(".avsc"); }
                    @Override public io.apicurio.registry.content.ContentAccepter getContentAccepter() { return null; }
                    @Override public io.apicurio.registry.content.canon.ContentCanonicalizer getContentCanonicalizer() { return null; }
                    @Override public io.apicurio.registry.content.extract.ContentExtractor getContentExtractor() { return null; }
                    @Override public io.apicurio.registry.content.refs.ReferenceFinder getReferenceFinder() { return null; }
                    @Override public io.apicurio.registry.content.refs.ReferenceArtifactIdentifierExtractor getReferenceArtifactIdentifierExtractor() { return null; }
                    @Override public io.apicurio.registry.content.dereference.ContentDereferencer getContentDereferencer() { return null; }
                    @Override public io.apicurio.registry.rules.validity.ContentValidator getContentValidator() { return null; }
                    @Override public io.apicurio.registry.rules.compatibility.CompatibilityChecker getCompatibilityChecker() { return null; }
                    @Override public boolean supportsReferencesWithContext() { return false; }
                });
            }
        };

        final IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> factory.validate());
        assertTrue(ex.getMessage().contains(".avsc"));
        assertTrue(ex.getMessage().contains("AVRO"));
        assertTrue(ex.getMessage().contains("MY_AVRO"));
    }

}
