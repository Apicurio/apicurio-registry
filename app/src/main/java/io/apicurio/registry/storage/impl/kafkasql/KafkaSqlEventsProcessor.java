package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.storage.dto.OutboxEvent;
import io.apicurio.registry.storage.impl.util.ProducerActions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.util.Collections;

import static io.apicurio.registry.utils.ConcurrentUtil.blockOnResult;

@ApplicationScoped
public class KafkaSqlEventsProcessor {

    @Inject
    KafkaSqlConfiguration configuration;

    @Inject
    @Named("KafkaSqlEventsProducer")
    ProducerActions<String, String> eventsProducer;

    public void processEvent(@Observes KafkaSqlOutboxEvent event) {
        OutboxEvent outboxEvent = event.getOutboxEvent();
        // TODO: Are we only allowing a single partition?
        ProducerRecord<String, String> record = new ProducerRecord<>(configuration.getEventsTopic(), 0,
                outboxEvent.getAggregateId(), outboxEvent.getPayload().toString(), Collections.emptyList());
        blockOnResult(eventsProducer.apply(record));
    }
}
