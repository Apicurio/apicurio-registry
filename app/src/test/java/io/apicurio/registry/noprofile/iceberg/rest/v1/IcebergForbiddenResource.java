package io.apicurio.registry.noprofile.iceberg.rest.v1;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

// Test-only; @Alternative keeps it out of every other app @QuarkusTest (same approach as HstsBoomResource).
@Alternative
@Path("/apis/iceberg/test/forbidden")
@ApplicationScoped
public class IcebergForbiddenResource {

    @GET
    public String forbidden() {
        throw new ForbiddenException("Intentional 403 from an Iceberg resource method.");
    }
}
