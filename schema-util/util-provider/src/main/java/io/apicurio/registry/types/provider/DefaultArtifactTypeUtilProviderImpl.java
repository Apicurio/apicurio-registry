package io.apicurio.registry.types.provider;

import static io.apicurio.registry.types.provider.StandardArtifactTypeProviderRegistry.createStandardProviders;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class DefaultArtifactTypeUtilProviderImpl implements ArtifactTypeUtilProviderFactory {

    protected Map<String, ArtifactTypeUtilProvider> providerMap = new ConcurrentHashMap<>();

    // Intentionally per-factory, not a shared static list: AbstractArtifactTypeUtilProvider caches
    // lazily-created components in mutable fields, so sharing one provider across factories would alias that cache.
    protected List<ArtifactTypeUtilProvider> standardProviders = new ArrayList<ArtifactTypeUtilProvider>(createStandardProviders());

    protected List<ArtifactTypeUtilProvider> providers = new ArrayList<>();

    private Map<String, String> extensionToTypeMap = Collections.emptyMap();

    public DefaultArtifactTypeUtilProviderImpl() {
    }

    public DefaultArtifactTypeUtilProviderImpl(boolean initStandardProviders) {
        if (initStandardProviders) {
            loadStandardProviders();
            validateFileExtensions();
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

    @Override
    public Map<String, String> getFileExtensionToTypeMap() {
        return extensionToTypeMap;
    }

    /**
     * Validates that no file extension is claimed by more than one provider and
     * builds the normalized extension-to-type map used for lookups.
     */
    protected void validateFileExtensions() {
        final Map<String, String> map = new HashMap<>();
        for (ArtifactTypeUtilProvider provider : providers) {
            for (String ext : provider.getFileExtensions()) {
                final String normalized = normalizeExtension(ext);
                final String existing = map.put(normalized, provider.getArtifactType());
                if (existing != null) {
                    throw new IllegalStateException("File extension '" + normalized
                            + "' is claimed by both " + existing + " and " + provider.getArtifactType());
                }
            }
        }
        extensionToTypeMap = Collections.unmodifiableMap(map);
    }

    private static String normalizeExtension(String ext) {
        final String lower = ext.toLowerCase(Locale.ROOT);
        return lower.startsWith(".") ? lower : "." + lower;
    }

}
