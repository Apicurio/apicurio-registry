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

import io.apicurio.registry.search.client.noop.NoopSearchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.util.Properties;

/**
 * @author Ales Justin
 */
public class SearchClientFactory {
    private static final Logger log = LoggerFactory.getLogger(SearchClientFactory.class);

    public static final String SEARCH_CLIENT_CLASS = "search.client-class";
    public static final String SEARCH_CLIENT_CLASSES = "search.client-classes";

    public static final String KAFKA_CLIENT = "io.apicurio.registry.search.client.kafka.KafkaSearchClient";
    public static final String HOTROD_CLIENT = "io.apicurio.registry.search.client.hotrod.HotRodSearchClient";
    public static final String REST_CLIENT = "io.apicurio.registry.search.client.rest.RestSearchClient";

    private static final String[] CLASSES = new String[]{KAFKA_CLIENT, HOTROD_CLIENT, REST_CLIENT};

    static SearchClient create(Properties properties) {
        String explicitClass = SearchUtil.property(properties, SEARCH_CLIENT_CLASS, null);
        if (explicitClass != null) {
            return instantiateSearchClient(properties, explicitClass, false);
        }

        String clientClasses = SearchUtil.property(properties, SEARCH_CLIENT_CLASSES, null);
        String[] clazzes = CLASSES;
        if (clientClasses != null) {
            clazzes = clientClasses.split(",");
        }
        for (String clazz : clazzes) {
            SearchClient client = instantiateSearchClient(properties, clazz, true);
            if (client != null) {
                return client;
            }
        }

        log.info("Using '{}' search client", NoopSearchClient.class.getName());
        return new NoopSearchClient();
    }

    @SuppressWarnings("unchecked")
    private static SearchClient instantiateSearchClient(Properties properties, String clazz, boolean ignoreErrors) {
        try {
            Constructor<SearchClient> ctor = (Constructor<SearchClient>) SearchClient.class.getClassLoader()
                                                                                           .loadClass(clazz)
                                                                                           .getConstructor(Properties.class);
            SearchClient client = ctor.newInstance(properties);
            log.info("Using '{}' search client", clazz);
            return client;
        } catch (Throwable t) {
            if (!ignoreErrors) {
                throw new IllegalStateException(t);
            }
            log.debug("Ignoring search client '{}' -- cannot instantiate: {}", clazz, t);
            return null;
        }
    }
}
