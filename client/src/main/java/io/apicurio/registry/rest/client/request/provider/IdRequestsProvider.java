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

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static io.apicurio.registry.rest.client.request.provider.Operation.GET;
import static io.apicurio.registry.rest.client.request.provider.Routes.IDS_CONTENT_HASH;
import static io.apicurio.registry.rest.client.request.provider.Routes.IDS_CONTENT_ID;
import static io.apicurio.registry.rest.client.request.provider.Routes.IDS_GLOBAL_ID;

/**
 * @author Carles Arnal 'carnalca@redhat.com'
 */
public class IdRequestsProvider {

    public static Request<InputStream> getContentByHash(String contentHash, Boolean canonical, Map<String, List<String>> queryParams) {
        return new Request.RequestBuilder<InputStream>()
                .operation(GET)
                .path(IDS_CONTENT_HASH)
                .pathParams(List.of(String.valueOf(contentHash)))
                .queryParams(queryParams)
                .responseType(new TypeReference<InputStream>(){})
                .build();
    }

    public static Request<InputStream> getContentByGlobalId(long globalId) {
        return new Request.RequestBuilder<InputStream>()
                .operation(GET)
                .path(IDS_GLOBAL_ID)
                .pathParams(List.of(String.valueOf(globalId)))
                .responseType(new TypeReference<InputStream>(){})
                .build();
    }

    public static Request<InputStream> getContentById(long contentId) {
        return new Request.RequestBuilder<InputStream>()
                .operation(GET)
                .path(IDS_CONTENT_ID)
                .pathParams(List.of(String.valueOf(contentId)))
                .responseType(new TypeReference<InputStream>(){})
                .build();
    }
}
