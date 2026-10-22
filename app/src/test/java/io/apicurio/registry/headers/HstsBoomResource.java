package io.apicurio.registry.headers;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

// Test-only endpoint that always throws, so HstsError500Test can assert the HSTS header on a real 500.
// @Alternative keeps it inactive unless a test enables it (see HstsError500Test.BoomProfile), so it does
// not register a stray 500 endpoint in every other app @QuarkusTest.
@Alternative
@Path("/apis/test/hsts-500-boom")
@ApplicationScoped
public class HstsBoomResource {

    @GET
    public String boom() {
        throw new RuntimeException("Intentional failure to verify HSTS headers on 500 responses.");
    }
}
