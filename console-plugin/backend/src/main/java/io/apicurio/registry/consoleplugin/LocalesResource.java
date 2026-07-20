package io.apicurio.registry.consoleplugin;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.InputStream;
import java.util.regex.Pattern;

@Path("/locales")
public class LocalesResource {

    private static final Pattern SAFE_PARAM = Pattern.compile("[a-zA-Z0-9_-]+");

    @GET
    @Path("/resource.json")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getLocale(@QueryParam("lng") String language,
                              @QueryParam("ns") String namespace) {
        if (language == null || namespace == null) {
            return Response.status(400).build();
        }

        if (!SAFE_PARAM.matcher(language).matches() || !SAFE_PARAM.matcher(namespace).matches()) {
            return Response.status(400).entity("Invalid language or namespace parameter").build();
        }

        String resourcePath = "META-INF/resources/locales/" + language + "/" + namespace + ".json";
        InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath);

        if (stream == null) {
            return Response.status(404).build();
        }

        return Response.ok(stream).build();
    }
}
