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

package io.apicurio.registry.search.client.common;

import io.apicurio.registry.search.client.SearchClient;
import io.apicurio.registry.search.common.Search;

import static io.apicurio.registry.search.client.SearchUtil.property;

import java.util.Objects;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * @author Ales Justin
 */
public abstract class InfinispanSearchClient implements SearchClient {
    protected final Logger log = Logger.getLogger(getClass().getName());

    protected static final String PROTO_CACHE = "___protobuf_metadata";
    public static final String SEARCH_CACHE_DEFAULT = "___search_cache";

    protected static final String COMMON_PROTO_KEY = "common.proto";
    protected static final String SEARCH_PROTO_KEY = "search.proto";

    protected static final String TYPE = "_type";

    protected static final String JSON_CACHE_CONFIG = "{\"distributed-cache\":{\"mode\":\"ASYNC\",\"indexing\":{\"auto-config\":true,\"index\":\"LOCAL\"}}}";

    protected static final String XML_CACHE_CONFIG = "<infinispan>\n" +
                                                     "    <cache-container>\n" +
                                                     "        <distributed-cache name=\"%s\" mode=\"ASYNC\">\n" +
                                                     "            <indexing index=\"LOCAL\" auto-config=\"true\">\n" +
                                                     "            </indexing>\n" +
                                                     "        </distributed-cache>\n" +
                                                     "    </cache-container>\n" +
                                                     "</infinispan>";

    protected final String cacheName;

    protected InfinispanSearchClient(Properties properties) {
        this(
            properties,
            property(properties, "search.host", "localhost"),
            Integer.parseInt(property(properties, "search.port", "-1")),
            property(properties, "search.user", "user"),
            property(properties, "search.password", "pass"),
            property(properties, "search.cache-name", SEARCH_CACHE_DEFAULT)
        );
    }

    protected InfinispanSearchClient(Properties properties, String host, int port, String username, String password, String cacheName) {
        this.cacheName = Objects.requireNonNull(cacheName);
        port = (port >= 0 ? port : defaultPort());
        initialize(properties, host, port, username, password, cacheName);
    }

    protected abstract int defaultPort();

    protected abstract void initialize(Properties properties, String host, int port, String username, String password, String cacheName);

    protected String toKey(Search.Artifact artifact) {
        return String.format("%s-%s", artifact.getArtifactId(), artifact.getVersion());
    }

    public static String toFqn() {
        return Search.Artifact.class.getName().replace("Search$", "");
    }
}
