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
package io.apicurio.registry.types.bigquery.provider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import io.apicurio.registry.types.provider.*;

// import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;

// @javax.annotation.Priority(Integer.MAX_VALUE)
@ApplicationScoped
@javax.enterprise.inject.Alternative
public class ArtifactTypeUtilProviderImpl implements ArtifactTypeUtilProviderFactory {

    // @PostConstruct
    // void onConstruct() {
    //     System.out.println("***************** DEBUG BIGQUERY!!!! *********************");
    //     // TODO: back to info
    //     // log.warn("BigQuery support enabled.");
    // }

    private Map<String, ArtifactTypeUtilProvider> map = new ConcurrentHashMap<>();

    private List<ArtifactTypeUtilProvider> providers = new ArrayList<ArtifactTypeUtilProvider>(
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
        System.out.println("***************** DEBUG BIGQUERY!!!! *********************");
        return map.computeIfAbsent(type, t ->
            providers.stream()
                     .filter(a -> a.getArtifactType().equals(t))
                     .findFirst()
                     .orElseThrow(() -> new IllegalStateException("No such artifact type provider: " + t)));
    }

    @Override
    public List<String> getAllArtifactTypes() {
        System.out.println("***************** DEBUG BIGQUERY!!!! *********************");
        return providers.stream()
            .map(a -> a.getArtifactType())
            .collect(Collectors.toList());
    }
}
