package io.apicurio.registry.examples;


import io.apicurio.registry.examples.util.RegistryDemoUtil;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.RegistryClientFactory;
import io.apicurio.rest.client.VertxHttpClientProvider;
import io.vertx.core.Vertx;

import java.util.UUID;

/**
 * Simple demo app that shows how to use the Vertx client client.
 * <p>
 * 1) Register a new schema in the Registry.
 * 2) Fetch the newly created schema.
 * 3) Delete the schema.
 *
 * @author Carles Arnal <carnalca@redhat.com>
 */
public class SimpleVertxClientExample {

    private static RegistryClient client;

    static {
        // Create a Service Registry client
        String registryUrl = "http://localhost:8080/apis/registry/v2";
        RegistryClientFactory.setProvider(new VertxHttpClientProvider(Vertx.vertx()));
        client = RegistryClientFactory.create(registryUrl);
    }

    public static void main(String[] args) throws Exception {
        // Register the JSON Schema schema in the Apicurio registry.
        final String artifactId = UUID.randomUUID().toString();

        RegistryDemoUtil.createSchemaInServiceRegistry(client, artifactId, Constants.SCHEMA);

        //Wait for the artifact to be available.
        Thread.sleep(1000);

        RegistryDemoUtil.getSchemaFromRegistry(client, artifactId);

        RegistryDemoUtil.deleteSchema(client, artifactId);
    }
}
