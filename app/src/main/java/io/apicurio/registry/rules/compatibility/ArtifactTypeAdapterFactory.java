/*
 * Copyright 2019 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.rules.compatibility;

import java.util.HashMap;
import java.util.Map;

import io.apicurio.registry.types.ArtifactType;

/**
 * Factory that creates an artifact type adapter for a given artifact type.
 * @author eric.wittmann@gmail.com
 */
public class ArtifactTypeAdapterFactory {

    private final static Map<ArtifactType, ArtifactTypeAdapter> ADAPTERS;

    static {
        ADAPTERS = new HashMap<>();
        ADAPTERS.put(ArtifactType.AVRO, new AvroArtifactTypeAdapter());
        ADAPTERS.put(ArtifactType.PROTOBUF, new ProtobufArtifactTypeAdapter());
        ADAPTERS.put(ArtifactType.PROTOBUF_FD, new ProtobufFdArtifactTypeAdapter());
        ADAPTERS.put(ArtifactType.JSON, new JsonArtifactTypeAdapter());
        ADAPTERS.put(ArtifactType.OPENAPI, NoopArtifactTypeAdapter.INSTANCE);
        ADAPTERS.put(ArtifactType.ASYNCAPI, NoopArtifactTypeAdapter.INSTANCE);
        ADAPTERS.put(ArtifactType.GRAPHQL, NoopArtifactTypeAdapter.INSTANCE);
    }

    public static ArtifactTypeAdapter toAdapter(ArtifactType type) {
        ArtifactTypeAdapter adapter = ADAPTERS.get(type);
        if (adapter == null) {
            throw new IllegalStateException("No matching adapter!? -- " + type);
        }
        return adapter;
    }
}
