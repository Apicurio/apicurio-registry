package io.apicurio.example;

import io.apicurio.example.schema.avro.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.POST;
import javax.ws.rs.Path;

@Path("/kafka")
public class Resource {

    Logger log = LoggerFactory.getLogger(this.getClass());

    @Inject
    Producer producer;

    @POST
    @Path("/publish")
    public void publish(InputEvent event) {
        log.info("REST Controller has received entity: {}", event);
        Event avroEvent = new Event();
        avroEvent.setName(event.getName());
        avroEvent.setDescription(event.getDescription());
        avroEvent.setSource("quarkus");
        this.producer.send(avroEvent);
    }
}