package io.apicurio.registry.client;

import com.microsoft.kiota.RequestAdapter;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.client.common.RegistryClientRequestAdapterFactory;
import io.apicurio.registry.client.common.Version;
import io.apicurio.registry.rest.client.v2.RegistryClient;

/**
 * Factory for creating instances of {@link RegistryClient}. This factory centralizes
 * the creation logic and provides a unified method for creating clients with different
 * authentication configurations using {@link RegistryClientOptions}.
 *
 * @deprecated REST API v2 is deprecated and the {@code client-v2} SDK module will be
 *             removed in a future release once REST API v2 is removed (see
 *             <a href="https://github.com/Apicurio/apicurio-registry/issues/7330">#7330</a>
 *             and <a href="https://github.com/Apicurio/apicurio-registry/issues/7336">#7336</a>).
 *             Use {@code io.apicurio.registry.client.RegistryClientFactory} from the
 *             {@code java-sdk/client} (v3) module instead. See
 *             <a href="https://github.com/Apicurio/apicurio-registry/blob/main/java-sdk/MIGRATING_FROM_V2.md">java-sdk/MIGRATING_FROM_V2.md</a>
 *             for step-by-step instructions on migrating from the v2 SDK to the v3 SDK.
 */
@Deprecated(forRemoval = true)
public final class RegistryV2ClientFactory {

    /**
     * Creates a RegistryClient using the provided options.
     *
     * @param options the configuration options for the client
     * @return a new RegistryClient instance
     * @throws IllegalArgumentException if options are invalid
     * @deprecated Use {@code RegistryClientFactory.create(RegistryClientOptions)}
     *             instead. See
     *             <a href="https://github.com/Apicurio/apicurio-registry/blob/main/java-sdk/MIGRATING_FROM_V2.md">java-sdk/MIGRATING_FROM_V2.md</a>.
     */
    @Deprecated(forRemoval = true)
    public static RegistryClient create(RegistryClientOptions options) {
        RequestAdapter adapter = RegistryClientRequestAdapterFactory.createRequestAdapter(options, Version.V2);
        return new RegistryClient(adapter);
    }
}