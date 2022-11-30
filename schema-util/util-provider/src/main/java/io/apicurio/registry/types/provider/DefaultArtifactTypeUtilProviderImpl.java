/*
 * Copyright 2020 Red Hat
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

package io.apicurio.registry.types.provider;

import io.apicurio.registry.types.ArtifactMediaTypes;
import io.apicurio.registry.types.ArtifactType;

import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @author Ales Justin
 * @author famartin
 */
public class DefaultArtifactTypeUtilProviderImpl implements ArtifactTypeUtilProviderFactory {

    protected Map<String, ArtifactTypeUtilProvider> map = new ConcurrentHashMap<>();

    protected List<ArtifactTypeUtilProvider> providers = new ArrayList<ArtifactTypeUtilProvider>(
                List.of(
                        new AsyncApiArtifactTypeUtilProvider(),
                        new AvroArtifactTypeUtilProvider(),
                        new GraphQLArtifactTypeUtilProvider(),
                        new JsonArtifactTypeUtilProvider(),
                        new KConnectArtifactTypeUtilProvider(),
                        new OpenApiArtifactTypeUtilProvider(),
                        new ProtobufArtifactTypeUtilProvider(),
                        new WsdlArtifactTypeUtilProvider(),
                        new XmlArtifactTypeUtilProvider(),
                        new XsdArtifactTypeUtilProvider())
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
    public MediaType getArtifactMediaType(String type) {
        // The content-type will be different for protobuf artifacts, graphql artifacts, and XML artifacts
        MediaType contentType = ArtifactMediaTypes.JSON;
        if (type.equals(ArtifactType.PROTOBUF)) {
            contentType = ArtifactMediaTypes.PROTO;
        }
        if (type.equals(ArtifactType.GRAPHQL)) {
            contentType = ArtifactMediaTypes.GRAPHQL;
        }
        if (type.equals(ArtifactType.WSDL) || type.equals(ArtifactType.XSD) || type.equals(ArtifactType.XML)) {
            contentType = ArtifactMediaTypes.XML;
        }

        return contentType;
    }
}
