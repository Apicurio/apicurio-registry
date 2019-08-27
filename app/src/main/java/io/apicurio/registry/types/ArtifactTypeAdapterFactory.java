
package io.apicurio.registry.types;

import java.util.HashMap;
import java.util.Map;

public class ArtifactTypeAdapterFactory {

    private final static Map<ArtifactType, ArtifactTypeAdapter> ADAPTERS;

    static {
        ADAPTERS = new HashMap<>();
        ADAPTERS.put(ArtifactType.avro, new AvroArtifactTypeAdapter());
        ADAPTERS.put(ArtifactType.protobuf, new ProtobufArtifactTypeAdapter());
        ADAPTERS.put(ArtifactType.json, new JsonArtifactTypeAdapter());
        ADAPTERS.put(ArtifactType.openapi, NoopArtifactTypeAdapter.INSTANCE);
        ADAPTERS.put(ArtifactType.asyncapi, NoopArtifactTypeAdapter.INSTANCE);
    }

    public static ArtifactTypeAdapter toAdapter(ArtifactType type) {
        ArtifactTypeAdapter adapter = ADAPTERS.get(type);
        if (adapter == null) {
            throw new IllegalStateException("No matching adapter!? -- " + type);
        }
        return adapter;
    }
}
