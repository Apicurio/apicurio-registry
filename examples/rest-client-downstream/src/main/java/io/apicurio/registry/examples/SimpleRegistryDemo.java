package io.apicurio.registry.examples;

import java.util.Collections;
import java.util.UUID;

import io.apicurio.registry.examples.util.RegistryDemoUtil;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.RegistryClientFactory;
import io.apicurio.rest.client.JdkHttpClientProvider;
import io.apicurio.rest.client.auth.OidcAuth;
import io.apicurio.rest.client.auth.exception.AuthErrorHandler;
import io.apicurio.rest.client.spi.ApicurioHttpClient;


/**
 * Simple demo app that shows how to use the client.
 * <p>
 * 1) Register a new schema in the Registry.
 * 2) Fetch the newly created schema.
 * 3) Delete the schema.
 *
 * @author Carles Arnal <carnalca@redhat.com>
 */
public class SimpleRegistryDemo {

    private static final RegistryClient client;

    static {
        // Create a Service Registry client
        String registryUrl = System.getenv("REGISTRY_URL");
        client = createProperClient(registryUrl);
    }

    public static void main(String[] args) throws Exception {
        // Register the JSON Schema schema in the Apicurio registry.
        final String artifactId = UUID.randomUUID().toString();

        RegistryDemoUtil.createSchemaInServiceRegistry(client, artifactId, Constants.SCHEMA);

        //Wait for the artifact to be available.
        Thread.sleep(1000);

        RegistryDemoUtil.getSchemaFromRegistry(client, artifactId);

        RegistryDemoUtil.deleteSchema(client, artifactId);

        //Required due to a bug in the version of registry libraries used. Once the new version is released, we'll be able to remove this.
        System.exit(0);
    }

    public static RegistryClient createProperClient(String registryUrl) {
        RegistryClientFactory.setProvider(new JdkHttpClientProvider());

        final String tokenEndpoint = System.getenv("AUTH_TOKEN_ENDPOINT");
        if (tokenEndpoint != null) {
            final String authClient = System.getenv("AUTH_CLIENT_ID");
            final String authSecret = System.getenv("AUTH_CLIENT_SECRET");
            ApicurioHttpClient httpClient = new JdkHttpClientProvider().create(tokenEndpoint, Collections.emptyMap(), null, new AuthErrorHandler());
            return RegistryClientFactory.create(registryUrl, Collections.emptyMap(), new OidcAuth(httpClient, authClient, authSecret));
        } else {
            return RegistryClientFactory.create(registryUrl);
        }
    }
}
