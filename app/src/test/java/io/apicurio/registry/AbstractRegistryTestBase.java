package io.apicurio.registry;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.ParallelizableTest;
import io.apicurio.registry.utils.tests.TestUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Assertions;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * Abstract base class for all registry tests.
 */
@ParallelizableTest
public abstract class AbstractRegistryTestBase {

    @ConfigProperty(name = "quarkus.http.test-port")
    public int testPort;

    protected String generateArtifactId() {
        return TestUtils.generateArtifactId();
    }

    /**
     * Loads a resource as a string. Good e.g. for loading test artifacts.
     * 
     * @param resourceName the resource name
     */
    protected final String resourceToString(String resourceName) {
        try (InputStream stream = getClass().getResourceAsStream(resourceName)) {
            Assertions.assertNotNull(stream, "Resource not found: " + resourceName);
            return new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).lines()
                    .collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Loads a resource as an input stream.
     * 
     * @param resourceName the resource name
     */
    protected final InputStream resourceToInputStream(String resourceName) {
        InputStream stream = getClass().getResourceAsStream(resourceName);
        Assertions.assertNotNull(stream, "Resource not found: " + resourceName);
        return stream;
    }

    protected final ContentHandle resourceToContentHandle(String resourceName) {
        return ContentHandle.create(resourceToString(resourceName));
    }

    protected final TypedContent resourceToTypedContentHandle(String resourceName) {
        String ct = ContentTypes.APPLICATION_JSON;
        if (resourceName.toLowerCase().endsWith("yaml") || resourceName.toLowerCase().endsWith("yml")) {
            ct = ContentTypes.APPLICATION_YAML;
        }
        if (resourceName.toLowerCase().endsWith("xml") || resourceName.toLowerCase().endsWith("wsdl")
                || resourceName.toLowerCase().endsWith("xsd")) {
            ct = ContentTypes.APPLICATION_XML;
        }
        if (resourceName.toLowerCase().endsWith("proto")) {
            ct = ContentTypes.APPLICATION_PROTOBUF;
        }
        if (resourceName.toLowerCase().endsWith("graphql")) {
            ct = ContentTypes.APPLICATION_GRAPHQL;
        }
        return TypedContent.create(resourceToContentHandle(resourceName), ct);
    }

    public static void assertMultilineTextEquals(String expected, String actual) throws Exception {
        Assertions.assertEquals(TestUtils.normalizeMultiLineString(expected),
                TestUtils.normalizeMultiLineString(actual));
    }

    public static InputStream asInputStream(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }

    public static TypedContent asTypedContent(String schema) {
        return asTypedContent(schema, ContentTypes.APPLICATION_JSON);
    }

    public static TypedContent asTypedContent(String schema, String contentType) {
        return TypedContent.create(ContentHandle.create(schema), contentType);
    }

}
