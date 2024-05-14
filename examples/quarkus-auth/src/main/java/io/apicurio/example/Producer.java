package io.apicurio.example;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.apicurio.example.schema.avro.Event;

@ApplicationScoped
public class Producer {

    Logger log = LoggerFactory.getLogger(this.getClass());

    @Inject
    @Channel("events")
    Emitter<Event> eventsEmitter;

    public void send(Event payload) {
        log.info("Producer sending message {} to events channel", payload);
        this.eventsEmitter.send(payload);
    }
}