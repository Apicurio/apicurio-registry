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

package io.apicurio.registry.search.client;

import io.apicurio.registry.search.client.common.InfinispanSearchClient;
import io.apicurio.registry.search.client.rest.RestSearchClient;
import io.apicurio.registry.search.client.rest.RestSearchResponse;
import io.apicurio.registry.utils.IoUtil;
import org.infinispan.client.rest.RestCacheClient;
import org.infinispan.client.rest.RestClient;
import org.infinispan.client.rest.configuration.RestClientConfigurationBuilder;
import org.infinispan.client.rest.impl.okhttp.RestClientOkHttp;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;

import java.util.Properties;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;

/**
 * @author Ales Justin
 */
public class RestSearchClientTest extends SearchClientTestBase {

    private static RestClient rawClient = new RestClientOkHttp(
        new RestClientConfigurationBuilder().addServer().host("localhost").build()
    );

    @AfterAll
    public static void tearDown() {
        IoUtil.closeIgnore(rawClient);
    }

    private static <T> T result(CompletionStage<? extends SearchResponse> csr, BiFunction<SearchResponse, Throwable, T> bif) {
        try {
            return bif.apply(csr.toCompletableFuture().get(), null);
        } catch (Throwable t) {
            return bif.apply(null, t);
        }
    }

    @Override
    void clearSearchCache() {
        RestCacheClient cache = rawClient.cache(InfinispanSearchClient.SEARCH_CACHE_DEFAULT);
        if (cache != null) {
            cache.clear();
        }
    }

    @Override
    <T extends SearchResponse> T ok(CompletionStage<T> cs) {
        SearchResponse response = result(cs, (r, t) -> r    );
        Assertions.assertNotNull(response);
        Assertions.assertTrue(response.ok());
        //noinspection unchecked
        return (T) response;
    }

    boolean isServerRunning() {
        return result(rawClient.server().info().thenApply(r -> new RestSearchResponse(r.getStatus())), (r, t) -> (t == null));
    }

    @Override
    SearchClient createSearchClient(Properties properties) {
        return new RestSearchClient(properties);
    }
}
