/*
 * Copyright 2026 The Apicurio Authors
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.apicurio.registry.events;

import io.apicurio.common.apps.config.Info;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_EVENTS;

/**
 * Configuration for registry events.
 */
@Singleton
public class EventsConfig {

    @ConfigProperty(name = "apicurio.events.cloud-events-source", defaultValue = "/apicurio-registry")
    @Info(category = CATEGORY_EVENTS, description = "The source URI for CloudEvents emitted by the registry", availableSince = "3.6.0")
    String cloudEventsSource;

    public String getCloudEventsSource() {
        return cloudEventsSource;
    }
}
