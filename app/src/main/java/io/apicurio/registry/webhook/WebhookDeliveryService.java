/*
 * Copyright 2026 The Apicurio Authors
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.apicurio.registry.webhook;

import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.events.dto.CloudEventConverter;
import io.apicurio.registry.events.dto.CloudEventDto;
import io.apicurio.registry.storage.dto.OutboxEvent;
import io.apicurio.registry.storage.impl.sql.SqlOutboxEvent;
import io.quarkus.arc.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consumes registry outbox events and converts them to CloudEvents for
 * webhook delivery. Actual delivery to configured webhook subscriptions
 * will be wired once the subscription storage layer is available.
 * <p>
 * The {@link SqlOutboxEvent} CDI event is fired by SQL storage repositories
 * (e.g., {@code SqlArtifactRepository}, {@code SqlVersionRepository},
 * {@code SqlRuleRepository}) when registry data changes (artifacts created,
 * deleted, rules configured, etc.). This service observes those events and
 * converts them to CloudEvents format.
 * <p>
 * This bean is only instantiated when {@code apicurio.events.webhook-delivery-enabled}
 * is {@code true} to avoid per-event overhead until delivery is implemented.
 */
@ApplicationScoped
@IfBuildProperty(name = "apicurio.events.webhook-delivery-enabled", stringValue = "true")
public class WebhookDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryService.class);

    private static final String CATEGORY_WEBHOOK = "Webhook";

    @ConfigProperty(name = "apicurio.events.cloud-events-source", defaultValue = "/apicurio-registry")
    @Info(category = CATEGORY_WEBHOOK, description = "CloudEvents source URI for webhook delivery", availableSince = "3.0.0")
    String eventSource;

    @ConfigProperty(name = "apicurio.events.webhook-delivery-enabled", defaultValue = "false")
    @Info(category = CATEGORY_WEBHOOK, description = "Enable webhook delivery service (disabled until delivery implementation is complete)", availableSince = "3.0.0")
    boolean webhookDeliveryEnabled;

    public void onOutboxEvent(@Observes SqlOutboxEvent sqlOutboxEvent) {
        OutboxEvent outboxEvent = sqlOutboxEvent.getOutboxEvent();
        CloudEventDto cloudEvent = CloudEventConverter.toCloudEvent(outboxEvent, eventSource);
        if (cloudEvent == null) {
            // Unsupported or unknown event type; already logged at WARN by the converter.
            return;
        }
        log.debug("Converted outbox event {} to CloudEvent type {}", cloudEvent.getId(), cloudEvent.getType());
    }
}
