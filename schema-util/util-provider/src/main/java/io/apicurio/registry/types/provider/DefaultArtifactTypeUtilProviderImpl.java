package io.apicurio.registry.types.provider;

import io.apicurio.registry.types.ArtifactMediaTypes;
import io.apicurio.registry.types.ArtifactType;
import jakarta.ws.rs.core.MediaType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class DefaultArtifactTypeUtilProviderImpl implements ArtifactTypeUtilProviderFactory {

    protected Map<String, ArtifactTypeUtilProvider> map = new ConcurrentHashMap<>();

    protected List<ArtifactTypeUtilProvider> providers = new ArrayList<ArtifactTypeUtilProvider>(
            List.of(new AsyncApiArtifactTypeUtilProvider(), new AvroArtifactTypeUtilProvider(),
                    new GraphQLArtifactTypeUtilProvider(), new JsonArtifactTypeUtilProvider(),
                    new KConnectArtifactTypeUtilProvider(), new OpenApiArtifactTypeUtilProvider(),
                    new ProtobufArtifactTypeUtilProvider(), new WsdlArtifactTypeUtilProvider(),
                    new XmlArtifactTypeUtilProvider(), new XsdArtifactTypeUtilProvider()));

    @Override
    public ArtifactTypeUtilProvider getArtifactTypeProvider(String type) {
        return map.computeIfAbsent(type,
                t -> providers.stream().filter(a -> a.getArtifactType().equals(t)).findFirst().orElseThrow(
                        () -> new IllegalStateException("No such artifact type provider: " + t)));
    }

    @Override
    public List<String> getAllArtifactTypes() {
        return providers.stream().map(a -> a.getArtifactType()).collect(Collectors.toList());
    }

    @Override
    public MediaType getArtifactMediaType(String type) {
        // The content-type will be different for protobuf artifacts, graphql artifacts, and XML artifacts
        MediaType contentType = ArtifactMediaTypes.JSON;
        if (type.equals(ArtifactType.PROTOBUF)) {
            contentType = ArtifactMediaTypes.PROTO;
        }
        if (type.equals(ArtifactType.GRAPHQL)) {
            contentType = ArtifactMediaTypes.GRAPHQL;
        }
        if (type.equals(ArtifactType.WSDL) || type.equals(ArtifactType.XSD)
                || type.equals(ArtifactType.XML)) {
            contentType = ArtifactMediaTypes.XML;
        }

        return contentType;
    }
}
