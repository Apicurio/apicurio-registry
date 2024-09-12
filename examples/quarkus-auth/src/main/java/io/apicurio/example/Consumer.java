package io.apicurio.example;

import io.apicurio.example.schema.avro.Event;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class Consumer {

    Logger log = LoggerFactory.getLogger(this.getClass());

    @Incoming("events-sink")
    public void consume(Event message) {
        log.info("Consumer consumed message {} from topic {}", message, "events");
    }
}
