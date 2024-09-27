package io.apicurio.registry.operator.util;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class HostUtil {

    private static final Logger log = LoggerFactory.getLogger(HostUtil.class);

    void startup(@Observes StartupEvent event) {
        /*
         * This is needed because QOSDK does not support bean injection into dependent resource classes. We
         * work around this by retrieving `@ApplicationScoped` beans with `Arc.container()`, but for the bean
         * to be present, it has to be activated on startup.
         */
    }

    public String getHost(String component, ApicurioRegistry3 p) {
        var prefix = p.getMetadata().getName() + "-" + component + "." + p.getMetadata().getNamespace();
        String host = prefix + ".cluster.example";
        log.debug("Host for component {} is {}", component, host);
        return host;
    }
}
