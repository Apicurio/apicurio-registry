/*
 * Copyright 2026 The Apicurio Authors
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.apicurio.registry.webhook;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Test profile that enables webhook delivery for testing.
 * The WebhookDeliveryService bean is only instantiated when
 * apicurio.events.webhook-delivery-enabled is true.
 */
public class WebhookEnabledTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("apicurio.events.webhook-delivery-enabled", "true");
    }
}
