package io.apicurio.registry.ccompat.rest;

import javax.ws.rs.NotFoundException;

/**
 * @author Ales Justin
 */
public class Errors {
    public static void noSuchSubject(String subject) {
        throw new NotFoundException(String .format("No such subject: %s", subject));
    }

    public static void schemaNotFound(Integer id) {
        throw new NotFoundException(String .format("No content with id: %s", id));
    }
}
