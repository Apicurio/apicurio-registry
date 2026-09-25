package io.apicurio.registry.storage.impl.search;

import io.apicurio.registry.asyncapi.content.extract.AsyncApiStructuredContentExtractor;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.extract.StructuredElement;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncApiStructuredContentExtractorTest {

    private final AsyncApiStructuredContentExtractor extractor = new AsyncApiStructuredContentExtractor();

    @Test
    void testExtractChannels() {
        String asyncApi = """
                {
                  "asyncapi": "2.6.0",
                  "info": { "title": "Test API", "version": "1.0" },
                  "channels": {
                    "user/signedup": {
                      "subscribe": { "operationId": "onUserSignup" }
                    },
                    "order/created": {
                      "publish": { "operationId": "publishOrderCreated" }
                    }
                  }
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(asyncApi));

        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("channel") && e.name().equals("user/signedup")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("channel") && e.name().equals("order/created")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("operation") && e.name().equals("onUserSignup")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("operation") && e.name().equals("publishOrderCreated")));
    }

    @Test
    void testExtractMessages() {
        String asyncApi = """
                {
                  "asyncapi": "2.6.0",
                  "info": { "title": "Test API", "version": "1.0" },
                  "channels": {},
                  "components": {
                    "messages": {
                      "UserSignedUp": { "payload": { "type": "object" } },
                      "OrderCreated": { "payload": { "type": "object" } }
                    }
                  }
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(asyncApi));

        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("message") && e.name().equals("UserSignedUp")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("message") && e.name().equals("OrderCreated")));
    }

    @Test
    void testExtractSchemas() {
        String asyncApi = """
                {
                  "asyncapi": "2.6.0",
                  "info": { "title": "Test API", "version": "1.0" },
                  "channels": {},
                  "components": {
                    "schemas": {
                      "User": { "type": "object" },
                      "Address": { "type": "object" }
                    }
                  }
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(asyncApi));

        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("schema") && e.name().equals("User")));
        assertTrue(
                elements.stream().anyMatch(e -> e.kind().equals("schema") && e.name().equals("Address")));
    }

    @Test
    void testExtractServers() {
        String asyncApi = """
                {
                  "asyncapi": "2.6.0",
                  "info": { "title": "Test API", "version": "1.0" },
                  "channels": {},
                  "servers": {
                    "production": { "url": "mqtt://broker.example.com", "protocol": "mqtt" },
                    "staging": { "url": "mqtt://staging.example.com", "protocol": "mqtt" }
                  }
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(asyncApi));

        assertTrue(
                elements.stream().anyMatch(e -> e.kind().equals("server") && e.name().equals("production")));
        assertTrue(
                elements.stream().anyMatch(e -> e.kind().equals("server") && e.name().equals("staging")));
    }

    @Test
    void testExtractTags() {
        String asyncApi = """
                {
                  "asyncapi": "2.6.0",
                  "info": { "title": "Test API", "version": "1.0" },
                  "channels": {},
                  "tags": [
                    { "name": "user" },
                    { "name": "signup" }
                  ]
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(asyncApi));

        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("tag") && e.name().equals("user")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("tag") && e.name().equals("signup")));
    }

    @Test
    void testExtractFromInvalidContent() {
        List<StructuredElement> elements = extractor.extract(ContentHandle.create("not json at all"));

        assertTrue(elements.isEmpty());
    }
}
