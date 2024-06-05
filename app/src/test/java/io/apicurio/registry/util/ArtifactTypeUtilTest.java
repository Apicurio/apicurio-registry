package io.apicurio.registry.util;

import io.apicurio.registry.AbstractRegistryTestBase;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.storage.error.InvalidArtifactTypeException;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import io.apicurio.registry.types.provider.DefaultArtifactTypeUtilProviderImpl;
import org.apache.avro.Schema;
import org.apache.avro.Schema.Type;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArtifactTypeUtilTest extends AbstractRegistryTestBase {
    
    static ArtifactTypeUtilProviderFactory artifactTypeUtilProviderFactory;
    static {
        artifactTypeUtilProviderFactory = new DefaultArtifactTypeUtilProviderImpl();
    }
    
    /**
     * Test method for {@link io.apicurio.registry.util.ArtifactTypeUtil#determineArtifactType(TypedContent, String, ArtifactTypeUtilProviderFactory)}
     */
    @Test
    void testDiscoverType_JSON() {
        TypedContent content = resourceToTypedContentHandle("json-schema.json");
        String type = ArtifactTypeUtil.determineArtifactType(content, null, artifactTypeUtilProviderFactory);
        Assertions.assertEquals(ArtifactType.JSON, type);
    }

    /**
     * Test method for {@link io.apicurio.registry.util.ArtifactTypeUtil#determineArtifactType(TypedContent, String, ArtifactTypeUtilProviderFactory)}.
     */
    @Test
    void testDiscoverType_Avro() {
        TypedContent content = resourceToTypedContentHandle("avro.json");
        String type = ArtifactTypeUtil.determineArtifactType(content, null, artifactTypeUtilProviderFactory);
        Assertions.assertEquals(ArtifactType.AVRO, type);
    }

    /**
     * Test method for {@link io.apicurio.registry.util.ArtifactTypeUtil#determineArtifactType(TypedContent, String, ArtifactTypeUtilProviderFactory)}.
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
     * Test method for {@link io.apicurio.registry.util.ArtifactTypeUtil#determineArtifactType(TypedContent, String, ArtifactTypeUtilProviderFactory)}.
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
     * Test method for {@link io.apicurio.registry.util.ArtifactTypeUtil#determineArtifactType(TypedContent, String, ArtifactTypeUtilProviderFactory)}.
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
     * Test method for {@link io.apicurio.registry.util.ArtifactTypeUtil#determineArtifactType(TypedContent, String, ArtifactTypeUtilProviderFactory)}.
     */
    @Test
    void testDiscoverType_AsyncApi() {
        TypedContent content = resourceToTypedContentHandle("asyncapi.json");
        String type = ArtifactTypeUtil.determineArtifactType(content, null, artifactTypeUtilProviderFactory);
        Assertions.assertEquals(ArtifactType.ASYNCAPI, type);
    }

    /**
     * Test method for {@link io.apicurio.registry.util.ArtifactTypeUtil#determineArtifactType(TypedContent, String, ArtifactTypeUtilProviderFactory)}.
     */
    @Test
    void testDiscoverType_GraphQL() {
        TypedContent content = resourceToTypedContentHandle("example.graphql");
        String type = ArtifactTypeUtil.determineArtifactType(content, null, artifactTypeUtilProviderFactory);
        Assertions.assertEquals(ArtifactType.GRAPHQL, type);
    }

    /**
     * Test method for {@link io.apicurio.registry.util.ArtifactTypeUtil#determineArtifactType(TypedContent, String, ArtifactTypeUtilProviderFactory)}.
     */
    @Test
    void testDiscoverType_DefaultNotFound() {
        Assertions.assertThrows(InvalidArtifactTypeException.class, () -> {
            TypedContent content = resourceToTypedContentHandle("example.txt");
            ArtifactTypeUtil.determineArtifactType(content, null, artifactTypeUtilProviderFactory);
        });
    }

    /**
     * Test method for {@link io.apicurio.registry.util.ArtifactTypeUtil#determineArtifactType(TypedContent, String, ArtifactTypeUtilProviderFactory)}.
     */
    @Test
    void testDiscoverType_Xml() {
        TypedContent content = resourceToTypedContentHandle("xml.xml");
        String type = ArtifactTypeUtil.determineArtifactType(content, null, artifactTypeUtilProviderFactory);
        Assertions.assertEquals(ArtifactType.XML, type);
    }

    /**
     * Test method for {@link io.apicurio.registry.util.ArtifactTypeUtil#determineArtifactType(TypedContent, String, ArtifactTypeUtilProviderFactory)}.
     */
    @Test
    void testDiscoverType_Xsd() {
        TypedContent content = resourceToTypedContentHandle("xml-schema.xsd");
        String type = ArtifactTypeUtil.determineArtifactType(content, null, artifactTypeUtilProviderFactory);
        Assertions.assertEquals(ArtifactType.XSD, type);
    }

    /**
     * Test method for {@link io.apicurio.registry.util.ArtifactTypeUtil#determineArtifactType(TypedContent, String, ArtifactTypeUtilProviderFactory)}.
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

}
