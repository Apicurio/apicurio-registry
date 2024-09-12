package io.apicurio.registry.rules.validity;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.types.ContentTypes;
import org.junit.jupiter.api.Assertions;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public class ArtifactUtilProviderTestBase {

    protected final String resourceToString(String resourceName) {
        try (InputStream stream = getClass().getResourceAsStream(resourceName)) {
            Assertions.assertNotNull(stream, "Resource not found: " + resourceName);
            return new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).lines()
                    .collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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

}
