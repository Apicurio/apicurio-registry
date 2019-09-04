package io.apicurio.registry.kafka.utils;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;

import javax.enterprise.event.Observes;

/**
 * @author Ales Justin
 */
public interface AppLifecycle {
    void init(@Observes StartupEvent event);
    void destroy(@Observes ShutdownEvent event);
}
