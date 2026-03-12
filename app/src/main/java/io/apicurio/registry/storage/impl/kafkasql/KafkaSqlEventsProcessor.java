package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.storage.dto.OutboxEvent;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_STORAGE;
import io.apicurio.registry.storage.impl.util.ProducerActions;
import io.quarkus.arc.lookup.LookupIfProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Collections;

import static io.apicurio.registry.utils.ConcurrentUtil.blockOnResult;

@ApplicationScoped
@LookupIfProperty(name = "apicurio.storage.kind", stringValue = "kafkasql")
public class KafkaSqlEventsProcessor {

    @ConfigProperty(name = "apicurio.storage.kind", defaultValue = "sql")
    @Info(category = CATEGORY_STORAGE, description = "The type of storage to use for the registry", registryAvailableSince = "3.0.0")
    String storageType;

    @Inject
    Instance<KafkaSqlConfiguration> configuration;

    @Inject
    @Named("KafkaSqlEventsProducer")
    Instance<ProducerActions<String, String>> eventsProducer;

    private boolean isKafkaSqlStorage() {
        return "kafkasql".equals(storageType);
    }

    public void processEvent(@Observes KafkaSqlOutboxEvent event) {
        if (!isKafkaSqlStorage()) {
            return;
        }
        OutboxEvent outboxEvent = event.getOutboxEvent();
        // Explicitly send to partition 0 to guarantee total ordering of all events.
        // See KafkaSqlConfiguration.getEventsTopicProperties() for details on this design decision.
        ProducerRecord<String, String> record = new ProducerRecord<>(configuration.get().getEventsTopic(), 0,
                outboxEvent.getAggregateId(), outboxEvent.getPayload().toString(), Collections.emptyList());
        blockOnResult(eventsProducer.get().apply(record));
    }
}
