package io.apicurio.registry.content.refs;

import io.apicurio.registry.asyncapi.content.refs.AsyncApiReferenceFinder;
import io.apicurio.registry.avro.content.refs.AvroReferenceFinder;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.json.content.refs.JsonSchemaReferenceFinder;
import io.apicurio.registry.openapi.content.refs.OpenApiReferenceFinder;
import io.apicurio.registry.protobuf.content.refs.ProtobufReferenceFinder;
import io.apicurio.registry.types.ContentTypes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests that all ReferenceFinder implementations throw ReferenceFinderException
 * when given invalid content. This ensures consistent error handling across all implementations.
 * See issue #6945.
 */
public class ReferenceFinderExceptionTest {

    private static final String INVALID_JSON = "{ this is not valid json }}}";
    private static final String INVALID_PROTOBUF = "this is not valid protobuf %%% syntax";

    @Test
    public void testAvroReferenceFinderThrowsException() {
        TypedContent content = TypedContent.create(
                ContentHandle.create(INVALID_JSON),
                ContentTypes.APPLICATION_JSON);
        AvroReferenceFinder finder = new AvroReferenceFinder();

        ReferenceFinderException exception = Assertions.assertThrows(
                ReferenceFinderException.class,
                () -> finder.findExternalReferences(content));

        Assertions.assertNotNull(exception.getMessage());
        Assertions.assertTrue(exception.getMessage().contains("Avro"));
    }

    @Test
    public void testJsonSchemaReferenceFinderThrowsException() {
        TypedContent content = TypedContent.create(
                ContentHandle.create(INVALID_JSON),
                ContentTypes.APPLICATION_JSON);
        JsonSchemaReferenceFinder finder = new JsonSchemaReferenceFinder();

        ReferenceFinderException exception = Assertions.assertThrows(
                ReferenceFinderException.class,
                () -> finder.findExternalReferences(content));

        Assertions.assertNotNull(exception.getMessage());
        Assertions.assertTrue(exception.getMessage().contains("JSON Schema"));
    }

    @Test
    public void testProtobufReferenceFinderThrowsException() {
        TypedContent content = TypedContent.create(
                ContentHandle.create(INVALID_PROTOBUF),
                ContentTypes.APPLICATION_PROTOBUF);
        ProtobufReferenceFinder finder = new ProtobufReferenceFinder();

        ReferenceFinderException exception = Assertions.assertThrows(
                ReferenceFinderException.class,
                () -> finder.findExternalReferences(content));

        Assertions.assertNotNull(exception.getMessage());
        Assertions.assertTrue(exception.getMessage().contains("Protobuf"));
    }

    @Test
    public void testOpenApiReferenceFinderThrowsException() {
        TypedContent content = TypedContent.create(
                ContentHandle.create(INVALID_JSON),
                ContentTypes.APPLICATION_JSON);
        OpenApiReferenceFinder finder = new OpenApiReferenceFinder();

        ReferenceFinderException exception = Assertions.assertThrows(
                ReferenceFinderException.class,
                () -> finder.findExternalReferences(content));

        Assertions.assertNotNull(exception.getMessage());
        Assertions.assertTrue(exception.getMessage().contains("OpenAPI") || exception.getMessage().contains("AsyncAPI"));
    }

    @Test
    public void testAsyncApiReferenceFinderThrowsException() {
        TypedContent content = TypedContent.create(
                ContentHandle.create(INVALID_JSON),
                ContentTypes.APPLICATION_JSON);
        AsyncApiReferenceFinder finder = new AsyncApiReferenceFinder();

        ReferenceFinderException exception = Assertions.assertThrows(
                ReferenceFinderException.class,
                () -> finder.findExternalReferences(content));

        Assertions.assertNotNull(exception.getMessage());
        Assertions.assertTrue(exception.getMessage().contains("OpenAPI") || exception.getMessage().contains("AsyncAPI"));
    }

    @Test
    public void testExceptionHasCause() {
        TypedContent content = TypedContent.create(
                ContentHandle.create(INVALID_JSON),
                ContentTypes.APPLICATION_JSON);
        AvroReferenceFinder finder = new AvroReferenceFinder();

        ReferenceFinderException exception = Assertions.assertThrows(
                ReferenceFinderException.class,
                () -> finder.findExternalReferences(content));

        Assertions.assertNotNull(exception.getCause());
    }
}
