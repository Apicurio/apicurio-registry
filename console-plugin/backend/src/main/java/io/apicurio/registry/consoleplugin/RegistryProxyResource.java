package io.apicurio.registry.consoleplugin;

import jakarta.annotation.PostConstruct;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletionStage;

@Path("/proxy")
public class RegistryProxyResource {

    private static final Logger log = LoggerFactory.getLogger(RegistryProxyResource.class);

    @ConfigProperty(name = "registry.api.url", defaultValue = "http://localhost:8080")
    String registryApiUrl;

    private HttpClient httpClient;

    @PostConstruct
    void init() {
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        log.info("Registry proxy configured: {}", registryApiUrl);
    }

    @GET
    @Path("/{path:.*}")
    public CompletionStage<Response> proxyGet(@HeaderParam("Authorization") String authorization,
                                              @Context UriInfo uriInfo) {
        return proxy("GET", authorization, uriInfo, null);
    }

    @POST
    @Path("/{path:.*}")
    public CompletionStage<Response> proxyPost(@HeaderParam("Authorization") String authorization,
                                               @Context UriInfo uriInfo, byte[] body) {
        return proxy("POST", authorization, uriInfo, body);
    }

    @PUT
    @Path("/{path:.*}")
    public CompletionStage<Response> proxyPut(@HeaderParam("Authorization") String authorization,
                                              @Context UriInfo uriInfo, byte[] body) {
        return proxy("PUT", authorization, uriInfo, body);
    }

    @DELETE
    @Path("/{path:.*}")
    public CompletionStage<Response> proxyDelete(@HeaderParam("Authorization") String authorization,
                                                  @Context UriInfo uriInfo) {
        return proxy("DELETE", authorization, uriInfo, null);
    }

    private CompletionStage<Response> proxy(String method, String authorization,
                                            UriInfo uriInfo, byte[] body) {
        String path = uriInfo.getPathParameters().getFirst("path");
        String query = uriInfo.getRequestUri().getRawQuery();
        String targetUrl = registryApiUrl + "/" + path + (query != null ? "?" + query : "");

        log.debug("Proxying {} -> {}", method, targetUrl);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .timeout(Duration.ofSeconds(30));

        if (authorization != null && !authorization.isEmpty()) {
            requestBuilder.header("Authorization", authorization);
        }

        HttpRequest.BodyPublisher bodyPublisher = body != null && body.length > 0
                ? HttpRequest.BodyPublishers.ofByteArray(body)
                : HttpRequest.BodyPublishers.noBody();

        requestBuilder.method(method, bodyPublisher);

        return httpClient.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofByteArray())
                .thenApply(resp -> {
                    Response.ResponseBuilder builder = Response.status(resp.statusCode());
                    resp.headers().firstValue("Content-Type")
                            .ifPresent(ct -> builder.header("Content-Type", ct));
                    builder.entity(resp.body());
                    return builder.build();
                })
                .exceptionally(ex -> {
                    log.error("Proxy request failed", ex);
                    return Response.status(502).entity("Bad Gateway").build();
                });
    }
}
