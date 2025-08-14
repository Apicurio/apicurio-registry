package io.apicurio.registry.examples;

import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.RegistryClientOptions;
import io.apicurio.registry.examples.util.RegistryDemoUtil;
import io.apicurio.registry.rest.client.RegistryClient;
import io.vertx.core.Vertx;

import java.util.UUID;

/**
 * Simple demo app that shows how to use the client.
 * <p>
 * 1) Register a new schema in the Registry. 2) Fetch the newly created schema. 3) Delete the schema.
 *
 * @author Carles Arnal <carnalca@redhat.com>
 */
public class SimpleRegistryDemoBasicAuth {

    private static final RegistryClient client;
    private static final Vertx vertx = Vertx.vertx();

    static {
        // Create a Service Registry client
        String registryUrl = "http://localhost:8080/apis/registry/v3";
        client = createProperClient(registryUrl);
    }

    public static void main(String[] args) throws Exception {
        // Register the JSON Schema schema in the Apicurio registry.
        final String artifactId = UUID.randomUUID().toString();

        RegistryDemoUtil.createSchemaInServiceRegistry(client, artifactId, Constants.SCHEMA);

        // Wait for the artifact to be available.
        Thread.sleep(1000);

        RegistryDemoUtil.getSchemaFromRegistry(client, artifactId);

        RegistryDemoUtil.deleteSchema(client, artifactId);
        vertx.close();
    }

    public static RegistryClient createProperClient(String registryUrl) {
        final String tokenEndpoint = System.getenv("AUTH_TOKEN_ENDPOINT");
        if (tokenEndpoint != null) {
            final String authClient = System.getenv("AUTH_CLIENT_ID");
            final String authSecret = System.getenv("AUTH_CLIENT_SECRET");
            return RegistryClientFactory.create(RegistryClientOptions.create(registryUrl, vertx)
                    .oauth2(tokenEndpoint, authClient, authSecret));
        } else {
            return RegistryClientFactory.create(RegistryClientOptions.create(registryUrl, vertx));
        }
    }
}
