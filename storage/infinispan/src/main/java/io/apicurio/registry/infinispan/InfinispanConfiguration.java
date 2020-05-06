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

package io.apicurio.registry.infinispan;

import io.apicurio.registry.utils.RegistryProperties;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.configuration.global.TransportConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;

import java.util.Properties;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Disposes;
import javax.enterprise.inject.Produces;

/**
 * @author Ales Justin
 */
@ApplicationScoped
public class InfinispanConfiguration {

    @ConfigProperty(name = "registry.infinispan.cluster.name", defaultValue = "apicurio-registry")
    String clusterName;

    @ApplicationScoped
    @Produces
    public EmbeddedCacheManager cacheManager(
            @RegistryProperties("registry.infinispan.transport.") Properties properties
    ) {
        GlobalConfigurationBuilder gConf = GlobalConfigurationBuilder.defaultClusteredBuilder();
        TransportConfigurationBuilder tConf = gConf.transport();
        tConf.clusterName(clusterName);
        if (properties.size() > 0) {
            tConf.withProperties(properties);
        }
        return new DefaultCacheManager(gConf.build(), true);
    }

    public void stop(@Disposes EmbeddedCacheManager manager) {
        manager.stop();
    }
}
