package io.apicurio.registry.examples;

import io.apicurio.registry.examples.util.RegistryDemoUtil;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.RegistryClientFactory;
import io.apicurio.rest.client.JdkHttpClient;
import io.apicurio.rest.client.auth.Auth;
import io.apicurio.rest.client.auth.BasicAuth;
import io.apicurio.rest.client.auth.OidcAuth;
import io.apicurio.rest.client.auth.exception.AuthErrorHandler;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

/**
 * Simple demo app that shows how to use the client.
 * <p>
 * 1) Register a new schema in the Registry.
 * 2) Fetch the newly created schema.
 * 3) Delete the schema.
 *
 * @author Carles Arnal <carnalca@redhat.com>
 */
public class SimpleRegistryDemoBasicAuth {

    private static final RegistryClient client;

    static {
        // Create a Service Registry client
        String registryUrl = "http://localhost:8080/apis/registry/v2";
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
    }

    public static RegistryClient createProperClient(String registryUrl) {
        final String tokenEndpoint = System.getenv("AUTH_TOKEN_ENDPOINT");
        if (tokenEndpoint != null) {
            final String authClient = System.getenv("AUTH_CLIENT_ID");
            final String authSecret = System.getenv("AUTH_CLIENT_SECRET");
            Auth auth = new BasicAuth(authClient, authSecret);
            return RegistryClientFactory.create(new JdkHttpClient(registryUrl, Collections.emptyMap(), auth, new AuthErrorHandler()));
        } else {
            return RegistryClientFactory.create(registryUrl);
        }
    }
}
