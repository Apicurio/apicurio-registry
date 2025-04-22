package io.apicurio.registry.types.provider;

import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class DefaultArtifactTypeUtilProviderImpl implements ArtifactTypeUtilProviderFactory {

    protected Map<String, ArtifactTypeUtilProvider> providerMap = new ConcurrentHashMap<>();

    protected List<ArtifactTypeUtilProvider> standardProviders = new ArrayList<ArtifactTypeUtilProvider>(
            List.of(new ProtobufArtifactTypeUtilProvider(), new OpenApiArtifactTypeUtilProvider(),
                    new AsyncApiArtifactTypeUtilProvider(), new JsonArtifactTypeUtilProvider(),
                    new AvroArtifactTypeUtilProvider(), new GraphQLArtifactTypeUtilProvider(),
                    new KConnectArtifactTypeUtilProvider(), new WsdlArtifactTypeUtilProvider(),
                    new XsdArtifactTypeUtilProvider(), new XmlArtifactTypeUtilProvider()));

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

    @Override
    public String getArtifactContentType(String type) {
        // The content-type will be different for protobuf artifacts, graphql artifacts, and XML artifacts
        String contentType = ContentTypes.APPLICATION_JSON;
        if (type.equals(ArtifactType.PROTOBUF)) {
            contentType = ContentTypes.APPLICATION_PROTOBUF;
        }
        if (type.equals(ArtifactType.GRAPHQL)) {
            contentType = ContentTypes.APPLICATION_GRAPHQL;
        }
        if (type.equals(ArtifactType.WSDL) || type.equals(ArtifactType.XSD)
                || type.equals(ArtifactType.XML)) {
            contentType = ContentTypes.APPLICATION_XML;
        }

        return contentType;
    }
}
