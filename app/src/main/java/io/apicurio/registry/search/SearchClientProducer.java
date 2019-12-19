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

package io.apicurio.registry.search;

import io.apicurio.registry.search.client.SearchClient;
import io.apicurio.registry.types.Current;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;

import java.util.Optional;
import java.util.Properties;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;

/**
 * @author Ales Justin
 */
@ApplicationScoped
public class SearchClientProducer {
    @Produces
    @ApplicationScoped
    @Current
    public SearchClient searchClient() {
        Properties properties = new Properties();

        Config config = ConfigProviderResolver.instance().getConfig();
        for (String key : config.getPropertyNames()) {
            int p = key.indexOf("search.");
            if (p >= 0) {
                // property can exist with key, but no value ...
                Optional<String> value = config.getOptionalValue(key, String.class);
                value.ifPresent(s -> properties.put(key.substring(p), s));
            }
        }

        return SearchClient.create(properties);
    }
}
