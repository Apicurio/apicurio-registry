package io.apicurio.example.debezium.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.POST;
import javax.ws.rs.Path;

/**
 * @author Jakub Senko <em>m@jsenko.net</em>
 */
@Path("/api")
public class Api {

    private static final Logger log = LoggerFactory.getLogger(Api.class);

    @Inject
    ExampleRunner runner;


    @POST
    @Path("/command")
    public String command(String command) {
        log.info("Command received: {}", command);
        switch (command) {
            case "start":
                runner.setEnabled(true);
                return "OK";
            case "stop":
                runner.setEnabled(false);
                return "OK";
            default:
                return "Unknown command: " + command;
        }
    }
}
