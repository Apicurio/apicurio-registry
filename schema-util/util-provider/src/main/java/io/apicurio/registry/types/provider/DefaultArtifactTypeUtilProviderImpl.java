package io.apicurio.registry.types.provider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class DefaultArtifactTypeUtilProviderImpl implements ArtifactTypeUtilProviderFactory {

    protected Map<String, ArtifactTypeUtilProvider> map = new ConcurrentHashMap<>();

    protected List<ArtifactTypeUtilProvider> providers = new ArrayList<ArtifactTypeUtilProvider>(
                List.of(
                        new ProtobufArtifactTypeUtilProvider(),
                        new OpenApiArtifactTypeUtilProvider(),
                        new AsyncApiArtifactTypeUtilProvider(),
                        new JsonArtifactTypeUtilProvider(),
                        new AvroArtifactTypeUtilProvider(),
                        new GraphQLArtifactTypeUtilProvider(),
                        new KConnectArtifactTypeUtilProvider(),
                        new WsdlArtifactTypeUtilProvider(),
                        new XsdArtifactTypeUtilProvider(),
                        new XmlArtifactTypeUtilProvider())
            );

    @Override
    public ArtifactTypeUtilProvider getArtifactTypeProvider(String type) {
        return map.computeIfAbsent(type, t ->
            providers.stream()
                     .filter(a -> a.getArtifactType().equals(t))
                     .findFirst()
                     .orElseThrow(() -> new IllegalStateException("No such artifact type provider: " + t)));
    }

    @Override
    public List<String> getAllArtifactTypes() {
        return providers.stream()
            .map(a -> a.getArtifactType())
            .collect(Collectors.toList());
    }

    @Override
    public List<ArtifactTypeUtilProvider> getAllArtifactTypeProviders() {
        return providers;
    }
}
