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
import io.apicurio.registry.search.client.hotrod.HotRodSearchClient;
import io.apicurio.registry.search.common.Search;
import io.apicurio.registry.utils.IoUtil;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ClientIntelligence;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;

import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletionStage;

/**
 * @author Ales Justin
 */
public class HotRodSearchClientTest extends SearchClientTestBase {

    private static RemoteCacheManager raw() {
        ConfigurationBuilder clientBuilder = new ConfigurationBuilder();
        clientBuilder.addServer()
                     .host("localhost")
                     .port(11222)
                     .security()
                     .authentication().username("user").password("pass").realm("default").serverName("infinispan")
                     .clientIntelligence(ClientIntelligence.BASIC);
        return new RemoteCacheManager(clientBuilder.build());
    }

    boolean isServerRunning() {
        RemoteCacheManager manager = null;
        try {
            manager = raw();
            Set<String> cacheNames = manager.getCacheNames();
            return cacheNames != null;
        } catch (Throwable ignored) {
            return false;
        } finally {
            IoUtil.closeIgnore(manager);
        }
    }

    @Override
    SearchClient createSearchClient(Properties properties) {
        return new HotRodSearchClient(properties);
    }

    @Override
    void clearSearchCache() {
        RemoteCacheManager manager = null;
        try {
            manager = raw();
            RemoteCache<String, Search.Artifact> cache = manager.getCache(InfinispanSearchClient.SEARCH_CACHE_DEFAULT);
            if (cache != null) {
                cache.clear();
            }
        } finally {
            IoUtil.closeIgnore(manager);
        }
    }

    @Override
    <T extends SearchResponse> T ok(CompletionStage<T> cs) throws Exception {
        return cs.toCompletableFuture().get();
    }
}
