package io.apicurio.example;

import javax.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.apicurio.example.schema.avro.Event;

@ApplicationScoped
public class Consumer {

    Logger log = LoggerFactory.getLogger(this.getClass());

    @Incoming("events-sink")
    public void consume(Event message) {
        log.info("Consumer consumed message {} from topic {}", message, "events");
    }
}
