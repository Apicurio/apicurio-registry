package io.apicurio.registry.examples;

import io.apicurio.registry.client.common.DefaultVertxInstance;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.examples.util.RegistryDemoUtil;
import io.apicurio.registry.rest.client.RegistryClient;

import java.util.UUID;

/**
 * Simple demo app that shows how to use the client.
 * <p>
 * 1) Register a new schema in the Registry. 2) Fetch the newly created schema. 3) Delete the schema.
 *
 * @author Carles Arnal <carnalca@redhat.com>
 */
public class SimpleRegistryDemo {

    private static final RegistryClient client;

    static {
        // Create a Service Registry client
        String registryUrl = "http://localhost:8080/apis/registry/v3";
        client = createProperClient(registryUrl);
    }

    public static void main(String[] args) throws Exception {
        // Register the JSON Schema schema in the Apicurio registry.
        final String artifactId = UUID.randomUUID().toString();

        try {
            RegistryDemoUtil.createSchemaInServiceRegistry(client, artifactId, Constants.SCHEMA);
            RegistryDemoUtil.getSchemaFromRegistry(client, artifactId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            // If we do not provide our own instance of Vertx, then we must close the
            // default instance that will get used.
            DefaultVertxInstance.close();
        }

    }

    public static RegistryClient createProperClient(String registryUrl) {
        final String tokenEndpoint = System.getenv("AUTH_TOKEN_ENDPOINT");
        if (tokenEndpoint != null) {
            final String authClient = System.getenv("AUTH_CLIENT_ID");
            final String authSecret = System.getenv("AUTH_CLIENT_SECRET");
            return RegistryClientFactory.create(RegistryClientOptions.create(registryUrl)
                    .oauth2(tokenEndpoint, authClient, authSecret));
        } else {
            return RegistryClientFactory.create(RegistryClientOptions.create(registryUrl));
        }
    }
}
