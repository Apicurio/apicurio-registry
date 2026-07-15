package io.apicurio.registry.consoleplugin;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.InputStream;

@Path("/locales")
public class LocalesResource {

    @GET
    @Path("/resource.json")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getLocale(@QueryParam("lng") String language,
                              @QueryParam("ns") String namespace) {
        if (language == null || namespace == null) {
            return Response.status(400).build();
        }

        String resourcePath = "META-INF/resources/locales/" + language + "/" + namespace + ".json";
        InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath);

        if (stream == null) {
            return Response.status(404).build();
        }

        return Response.ok(stream).build();
    }
}
