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

package io.apicurio.registry.search.client.hotrod;

import io.apicurio.registry.search.client.SearchResponse;
import io.apicurio.registry.search.client.SearchResults;
import io.apicurio.registry.search.client.common.InfinispanSearchClient;
import io.apicurio.registry.search.common.Search;
import io.apicurio.registry.utils.IoUtil;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ClientIntelligence;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.client.hotrod.impl.ConfigurationProperties;
import org.infinispan.client.hotrod.marshall.MarshallerUtil;
import org.infinispan.commons.configuration.BasicConfiguration;
import org.infinispan.commons.configuration.XMLStringConfiguration;
import org.infinispan.commons.marshall.ProtoStreamMarshaller;
import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.SerializationContext;
import org.infinispan.query.dsl.QueryFactory;

import static io.apicurio.registry.search.client.SearchUtil.property;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * @author Ales Justin
 */
public class HotRodSearchClient extends InfinispanSearchClient {
    private RemoteCacheManager manager;
    private RemoteCache<String, Search.Artifact> cache;

    public HotRodSearchClient(Properties properties) {
        super(properties);
    }

    private RemoteCache<String, Search.Artifact> getCache() {
        if (cache == null) {
            cache = manager.getCache(cacheName);
        }
        return cache;
    }

    @Override
    protected int defaultPort() {
        return ConfigurationProperties.DEFAULT_HOTROD_PORT;
    }

    @Override
    protected void initialize(Properties properties, String host, int port, String username, String password, String cacheName) {
        String realm = property(properties, "search.realm", "default");
        String serverName = property(properties, "search.server-name", "infinispan");
        ClientIntelligence ci = ClientIntelligence.valueOf(property(properties, "search.client-intelligence", "BASIC"));
        ConfigurationBuilder clientBuilder = new ConfigurationBuilder();
        clientBuilder
            .addServer().host(host).port(port)
            .security().authentication().username(username).password(password).realm(realm).serverName(serverName)
            .clientIntelligence(ci)
            .marshaller(new ProtoStreamMarshaller());
        manager = new RemoteCacheManager(clientBuilder.build());
    }

    private void registerProto(boolean reset, String... protoKeys) {
        RemoteCache<Object, Object> cache = manager.getCache(PROTO_CACHE);
        if (cache == null) {
            throw new IllegalStateException(String.format("Missing %s cache!", PROTO_CACHE));
        }

        SerializationContext ctx = MarshallerUtil.getSerializationContext(manager);
        FileDescriptorSource fds = new FileDescriptorSource();
        for (String protoKey : protoKeys) {
            if (reset || !cache.containsKey(protoKey)) {
                String protoContent = IoUtil.toString(getClass().getResourceAsStream("/" + protoKey));
                log.info(String.format("Using proto schema: %s%n%s", protoKey, protoContent));
                fds.addProtoFile(protoKey, protoContent);
                cache.put(protoKey, protoContent);
            }
        }
        ctx.registerProtoFiles(fds);
        ctx.registerMarshaller(new ArtifactTypeMarshaller());
        ctx.registerMarshaller(new ArtifactMarshaller());
    }

    @Override
    public CompletionStage<Boolean> initialize(boolean reset) {
        registerProto(reset, COMMON_PROTO_KEY, SEARCH_PROTO_KEY);

        Set<String> caches = manager.getCacheNames();
        boolean hasSearch = caches.contains(cacheName);
        if (reset || !hasSearch) {
            if (hasSearch) {
                manager.administration().removeCache(cacheName);
            }
            String xml = String.format(XML_CACHE_CONFIG, cacheName);
            BasicConfiguration configuration = new XMLStringConfiguration(xml);
            cache = manager.administration().createCache(cacheName, configuration);
        }
        return CompletableFuture.completedFuture(Boolean.TRUE);
    }

    @Override
    public CompletionStage<SearchResponse> index(Search.Artifact artifact) {
        return getCache().putAsync(toKey(artifact), artifact)
                         .thenApply(a -> HotRodSearchResponse.INSTANCE);
    }

    @Override
    public CompletionStage<SearchResponse> index(List<Search.Artifact> artifacts) {
        Map<String, Search.Artifact> data = new HashMap<>();
        for (Search.Artifact artifact : artifacts) {
            data.put(toKey(artifact), artifact);
        }
        return getCache().putAllAsync(data)
                         .thenApply(a -> HotRodSearchResponse.INSTANCE);
    }

    @Override
    public CompletionStage<SearchResults> search(String query) {
        QueryFactory qf = org.infinispan.client.hotrod.Search.getQueryFactory(getCache());
        query = query.replace("$Artifact", toFqn()); // simplify usage
        List<Search.Artifact> list = qf.create(query).list();
        return CompletableFuture.completedFuture(new HotRodSearchResults(list));
    }

    @Override
    public void close() {
        IoUtil.closeIgnore(manager);
    }
}
