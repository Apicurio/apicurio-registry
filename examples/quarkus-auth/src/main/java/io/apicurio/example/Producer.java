package io.apicurio.example;

import io.apicurio.example.schema.avro.Event;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

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