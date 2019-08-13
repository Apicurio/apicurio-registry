package io.apicurio.registry.infinispan;

import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.configuration.global.TransportConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Disposes;
import javax.enterprise.inject.Produces;

/**
 * @author Ales Justin
 */
@ApplicationScoped
public class InfinispanConfiguration {

    @ApplicationScoped
    @Produces
    public EmbeddedCacheManager cacheManager() {
        GlobalConfigurationBuilder gConf = GlobalConfigurationBuilder.defaultClusteredBuilder();
        TransportConfigurationBuilder tConf = gConf.transport();
        tConf.clusterName(System.getProperty("cache-name", "apicurio-registry"));
        return new DefaultCacheManager(gConf.build(), true);
    }

    public void stop(@Disposes EmbeddedCacheManager manager) {
        manager.stop();
    }
}
