package io.apicurio.registry.types.provider;

import static io.apicurio.registry.types.provider.StandardArtifactTypeProviderRegistry.createStandardProviders;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class DefaultArtifactTypeUtilProviderImpl implements ArtifactTypeUtilProviderFactory {

    protected Map<String, ArtifactTypeUtilProvider> providerMap = new ConcurrentHashMap<>();

    // Intentionally per-factory, not a shared static list: AbstractArtifactTypeUtilProvider caches
    // lazily-created components in mutable fields, so sharing one provider across factories would alias that cache.
    protected List<ArtifactTypeUtilProvider> standardProviders = new ArrayList<ArtifactTypeUtilProvider>(createStandardProviders());

    protected List<ArtifactTypeUtilProvider> providers = new ArrayList<>();

    public DefaultArtifactTypeUtilProviderImpl() {
    }

    public DefaultArtifactTypeUtilProviderImpl(boolean initStandardProviders) {
        if (initStandardProviders) {
            loadStandardProviders();
        }
    }

    protected void loadStandardProviders() {
        providers.addAll(standardProviders);
    }

    @Override
    public ArtifactTypeUtilProvider getArtifactTypeProvider(String type) {
        return providerMap.computeIfAbsent(type,
                t -> providers.stream().filter(a -> a.getArtifactType().equals(t)).findFirst().orElseThrow(
                        () -> new IllegalStateException("No such artifact type provider: " + t)));
    }

    @Override
    public List<String> getAllArtifactTypes() {
        return providers.stream().map(a -> a.getArtifactType()).collect(Collectors.toList());
    }

    @Override
    public List<ArtifactTypeUtilProvider> getAllArtifactTypeProviders() {
        return providers;
    }

}
