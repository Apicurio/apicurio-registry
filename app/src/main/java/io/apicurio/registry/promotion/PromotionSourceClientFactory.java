package io.apicurio.registry.promotion;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.storage.RegistryStorage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.net.http.HttpClient;
import java.time.Duration;

@ApplicationScoped
public class PromotionSourceClientFactory {

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Inject
    PromotionConfig config;

    @Inject
    @Current
    RegistryStorage storage;

    public PromotionSourceClient client(String sourceName) {
        PromotionSourceDefinition source = config.requireSource(sourceName);
        if (source.isLocal()) {
            return new LocalStoragePromotionSourceClient(storage);
        }
        return new HttpPromotionSourceClient(source, httpClient);
    }
}
