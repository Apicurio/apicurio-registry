/*
 * Copyright 2026 The Apicurio Authors
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.apicurio.registry.webhook;

import io.apicurio.registry.events.dto.CloudEventConverter;
import io.apicurio.registry.events.dto.CloudEventDto;
import io.apicurio.registry.storage.dto.OutboxEvent;
import io.apicurio.registry.storage.impl.sql.SqlOutboxEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consumes registry outbox events and converts them to CloudEvents for
 * webhook delivery. Actual delivery to configured webhook subscriptions
 * will be wired once the subscription storage layer is available.
 */
@ApplicationScoped
public class WebhookDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryService.class);

    private static final String EVENT_SOURCE = "/apicurio-registry";

    public void onOutboxEvent(@Observes SqlOutboxEvent sqlOutboxEvent) {
        OutboxEvent outboxEvent = sqlOutboxEvent.getOutboxEvent();
        CloudEventDto cloudEvent = CloudEventConverter.toCloudEvent(outboxEvent, EVENT_SOURCE);
        if (cloudEvent == null) {
            log.debug("No CloudEvent mapping for event type: {}", outboxEvent.getType());
            return;
        }
        log.debug("Converted outbox event {} to CloudEvent type {}", cloudEvent.getId(), cloudEvent.getType());
    }
}
