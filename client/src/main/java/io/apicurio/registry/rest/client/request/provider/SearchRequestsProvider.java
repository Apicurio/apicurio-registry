/*
 * Copyright 2021 Red Hat
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

package io.apicurio.registry.rest.client.request.provider;

import com.fasterxml.jackson.core.type.TypeReference;
import io.apicurio.registry.rest.client.request.Request;
import io.apicurio.registry.rest.v2.beans.ArtifactSearchResults;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static io.apicurio.registry.rest.client.request.provider.Operation.GET;
import static io.apicurio.registry.rest.client.request.provider.Operation.POST;
import static io.apicurio.registry.rest.client.request.provider.Routes.SEARCH_ARTIFACTS;

/**
 * @author Carles Arnal 'carnalca@redhat.com'
 */
public class SearchRequestsProvider {

    public static Request<ArtifactSearchResults> searchArtifactsByContent(InputStream data, Map<String, List<String>> queryParams) {
        return new Request.RequestBuilder<ArtifactSearchResults>()
                .operation(POST)
                .path(SEARCH_ARTIFACTS)
                .responseType(new TypeReference<ArtifactSearchResults>(){})
                .queryParams(queryParams)
                .data(data)
                .build();
    }

    public static Request<ArtifactSearchResults> searchArtifacts(Map<String, List<String>> queryParams) {
        return new Request.RequestBuilder<ArtifactSearchResults>()
                .operation(GET)
                .path(SEARCH_ARTIFACTS)
                .responseType(new TypeReference<ArtifactSearchResults>(){})
                .queryParams(queryParams)
                .build();
    }
}
