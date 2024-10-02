package io.apicurio.registry.events;

import io.apicurio.common.apps.config.Info;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

public class RegistryEventsConfigurationProducer {

    @Inject
    @ConfigProperty(defaultValue = "false", name = "apicurio.events.enabled")
    @Info(category = "events", description = "Sending events is enabled", availableSince = "3.0.1")
    Boolean eventsEnabled;

    @Produces
    @ApplicationScoped
    public RegistryEventsConfiguration postConstruct() {

        RegistryEventsConfiguration c = new RegistryEventsConfiguration();

        c.setEventsEnabled(eventsEnabled);

        return c;
    }
}
