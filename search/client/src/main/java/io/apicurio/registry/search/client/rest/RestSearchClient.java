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

package io.apicurio.registry.search.client.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.apicurio.registry.search.client.SearchResponse;
import io.apicurio.registry.search.client.SearchResults;
import io.apicurio.registry.search.client.common.InfinispanSearchClient;
import io.apicurio.registry.search.common.Search;
import io.apicurio.registry.utils.IoBufferedInputStream;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.ProtoUtil;
import org.infinispan.client.rest.RestCacheClient;
import org.infinispan.client.rest.RestClient;
import org.infinispan.client.rest.RestEntity;
import org.infinispan.client.rest.RestResponse;
import org.infinispan.client.rest.configuration.RestClientConfiguration;
import org.infinispan.client.rest.configuration.RestClientConfigurationBuilder;
import org.infinispan.client.rest.configuration.RestClientConfigurationProperties;
import org.infinispan.client.rest.impl.okhttp.StringRestEntityOkHttp;
import org.infinispan.commons.dataconversion.MediaType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.logging.Level;

/**
 * @author Ales Justin
 */
public class RestSearchClient extends InfinispanSearchClient {

    private static final BiFunction<RestResponse, RestResponse, RestResponse> RFN = (r1, r2) -> r2;

    protected final ObjectMapper mapper = new ObjectMapper();
    private RestClient client;

    public RestSearchClient(Properties properties) {
        super(properties);
    }

    @Override
    protected int defaultPort() {
        return RestClientConfigurationProperties.DEFAULT_REST_PORT;
    }

    @Override
    protected void initialize(Properties properties, String host, int port, String username, String password, String cacheName) {
        RestClientConfiguration configuration = new RestClientConfigurationBuilder()
            .addServer().host(host).port(port)
            .security().authentication().username(username).password(password)
            .build();
        client = RestClient.forConfiguration(configuration);
    }

    private RestCacheClient getCache() {
        return client.cache(cacheName);
    }

    private boolean cacheExists(String caches, String cache) {
        return caches.contains("\"" + cache + "\"");
    }

    private void reset(boolean exists, String cache, String key) throws Exception {
        // List<String> caches --> toString
        if (exists) {
            CompletionStage<RestResponse> reset;
            if (key != null) {
                reset = client.cache(cache).remove(key).thenCompose(r -> client.cache(cache).remove(key + ".errors"));
            } else {
                reset = client.cache(cache).clear();
            }
            reset.toCompletableFuture().get();
        }
    }

    private CompletionStage<RestResponse> registerProto(String protoKey) {
        String protoContent = IoUtil.toString(getClass().getResourceAsStream("/" + protoKey));
        log.info(String.format("Using proto schema: %s%n%s", protoKey, protoContent));
        return client.cache(PROTO_CACHE).post(protoKey, protoContent)
                     .whenComplete((r, t) -> {
                         if (t == null) {
                             client.cache(PROTO_CACHE).get(protoKey + ".errors")
                                   .thenApply(resp -> {
                                       log.info("Proto errors: " + resp.getBody());
                                       return resp;
                                   });
                         }
                     });
    }

    protected String toJson(Search.Artifact artifact) throws Exception {
        ObjectNode node = mapper.createObjectNode();
        node.put(TYPE, toFqn());
        String root = ProtoUtil.toJson(artifact);
        node.setAll((ObjectNode) mapper.readTree(root));
        return node.toString();
    }

    @Override
    public CompletionStage<Boolean> initialize(boolean reset) throws Exception {
        CompletionStage<RestResponse> caches = client.caches();
        RestResponse result = caches.toCompletableFuture().get();
        String body = result.getBody();
        if (result.getStatus() != 200) {
            log.log(Level.SEVERE, body);
            return CompletableFuture.completedFuture(Boolean.FALSE);
        }
        boolean hasProto = cacheExists(body, PROTO_CACHE);
        boolean hasSearch = cacheExists(body, cacheName);
        if (reset) {
            reset(hasProto, PROTO_CACHE, SEARCH_PROTO_KEY);
            reset(hasProto, PROTO_CACHE, COMMON_PROTO_KEY);
            reset(hasSearch, cacheName, null);
        }

        CompletionStage<RestResponse> cs = null;

        if (reset || !hasProto) {
            cs = registerProto(COMMON_PROTO_KEY).thenCompose(r -> registerProto(SEARCH_PROTO_KEY));
        }

        if (reset || !hasSearch) {
            RestEntity configEntity = new StringRestEntityOkHttp(MediaType.APPLICATION_JSON, JSON_CACHE_CONFIG);
            CompletionStage<RestResponse> searchCs = getCache().createWithConfiguration(configEntity);
            cs = (cs != null) ? cs.thenCombine(searchCs, RFN) : searchCs;
        }
        return (cs != null) ? cs.handle((r, t) -> (t == null)) : CompletableFuture.completedFuture(Boolean.TRUE);
    }

    @Override
    public CompletionStage<SearchResponse> index(Search.Artifact artifact) throws Exception {
        String json = toJson(artifact);
        RestEntity entity = RestEntity.create(MediaType.APPLICATION_JSON, json);
        String key = toKey(artifact);
        return getCache().post(key, entity).thenApply(r -> new RestSearchResponse(r.getStatus()));
    }

    @Override
    public CompletionStage<SearchResults> search(String query) {
        query = query.replace("$Artifact", toFqn()); // simplify usage
        CompletionStage<RestResponse> results = getCache().query(query);
        return results.thenApply(r -> {
            try {
                int status = r.getStatus();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                IoBufferedInputStream rbis = new IoBufferedInputStream(r.getBodyAsStream(), (bytes, count) -> {
                    baos.write(bytes, 0, count);
                });
                // Infinispan Search impl details!!
                JsonNode tree = mapper.readTree(rbis);
                int totalResults = tree.get("total_results").asInt();
                ArrayNode hits = (ArrayNode) tree.get("hits");
                List<Search.Artifact> artifacts = new ArrayList<>();
                for (int i = 0; i < hits.size(); i++) {
                    JsonNode jsonNode = hits.get(i);
                    Search.Artifact.Builder builder = Search.Artifact.newBuilder();
                    String json = jsonNode.get("hit").toString();
                    Search.Artifact artifact = ProtoUtil.fromJson(builder, json, true);
                    artifacts.add(artifact);
                }
                return new RestSearchResults(status, totalResults, artifacts);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    @Override
    public void close() {
        IoUtil.closeIgnore(client);
    }
}
