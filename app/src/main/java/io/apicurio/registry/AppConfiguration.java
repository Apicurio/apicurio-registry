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

package io.apicurio.registry;

import io.apicurio.registry.search.client.SearchClient;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.utils.PropertiesUtil;
import io.apicurio.registry.utils.RegistryProperties;

import java.util.Properties;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.InjectionPoint;

/**
 * Generic configuration.
 *
 * @author Ales Justin
 */
@ApplicationScoped
public class AppConfiguration {

    @Produces
    public Properties properties(InjectionPoint ip) {
        RegistryProperties kp = ip.getAnnotated().getAnnotation(RegistryProperties.class);
        return PropertiesUtil.properties(kp);
    }

    @Produces
    @ApplicationScoped
    @Current
    public SearchClient searchClient(@RegistryProperties("registry.search-index.") Properties properties) {
        return SearchClient.create(properties);
    }

}
