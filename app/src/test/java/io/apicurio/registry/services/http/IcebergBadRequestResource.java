package io.apicurio.registry.services.http;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

// Test-only; @Alternative keeps it out of every other app @QuarkusTest (same approach as HstsBoomResource).
@Alternative
@Path("/apis/iceberg/test/bad-request")
@ApplicationScoped
public class IcebergBadRequestResource {

    @GET
    public String badRequest() {
        throw new BadRequestException("Intentional 400 from an Iceberg resource method.");
    }
}
