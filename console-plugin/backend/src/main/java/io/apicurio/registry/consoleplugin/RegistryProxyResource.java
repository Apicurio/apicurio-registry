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
import java.util.concurrent.CompletableFuture;
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
                                              @HeaderParam("Accept") String accept,
                                              @Context UriInfo uriInfo) {
        return proxy("GET", authorization, null, accept, uriInfo, null);
    }

    @POST
    @Path("/{path:.*}")
    public CompletionStage<Response> proxyPost(@HeaderParam("Authorization") String authorization,
                                               @HeaderParam("Content-Type") String contentType,
                                               @HeaderParam("Accept") String accept,
                                               @Context UriInfo uriInfo, byte[] body) {
        return proxy("POST", authorization, contentType, accept, uriInfo, body);
    }

    @PUT
    @Path("/{path:.*}")
    public CompletionStage<Response> proxyPut(@HeaderParam("Authorization") String authorization,
                                              @HeaderParam("Content-Type") String contentType,
                                              @HeaderParam("Accept") String accept,
                                              @Context UriInfo uriInfo, byte[] body) {
        return proxy("PUT", authorization, contentType, accept, uriInfo, body);
    }

    @DELETE
    @Path("/{path:.*}")
    public CompletionStage<Response> proxyDelete(@HeaderParam("Authorization") String authorization,
                                                  @HeaderParam("Accept") String accept,
                                                  @Context UriInfo uriInfo) {
        return proxy("DELETE", authorization, null, accept, uriInfo, null);
    }

    private CompletionStage<Response> proxy(String method, String authorization, String contentType,
                                            String accept, UriInfo uriInfo, byte[] body) {
        String path = uriInfo.getPathParameters().getFirst("path");
        String query = uriInfo.getRequestUri().getRawQuery();
        String targetUrl = registryApiUrl + "/" + path + (query != null ? "?" + query : "");

        // Validate that the resolved URL stays within the configured registry API
        if (!targetUrl.startsWith(registryApiUrl)) {
            return CompletableFuture.completedFuture(
                    Response.status(400).entity("Invalid proxy path").build());
        }

        log.debug("Proxying {} -> {}", method, targetUrl);

        // Check request body size (10 MB max)
        if (body != null && body.length > 10 * 1024 * 1024) {
            return CompletableFuture.completedFuture(
                    Response.status(413).entity("Request body too large").build());
        }

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .timeout(Duration.ofSeconds(30));

        if (authorization != null && !authorization.isEmpty()) {
            requestBuilder.header("Authorization", authorization);
        }
        if (contentType != null && !contentType.isEmpty()) {
            requestBuilder.header("Content-Type", contentType);
        }
        if (accept != null && !accept.isEmpty()) {
            requestBuilder.header("Accept", accept);
        }

        HttpRequest.BodyPublisher bodyPublisher = body != null && body.length > 0
                ? HttpRequest.BodyPublishers.ofByteArray(body)
                : HttpRequest.BodyPublishers.noBody();

        requestBuilder.method(method, bodyPublisher);

        return httpClient.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofByteArray())
                .thenApply(resp -> {
                    // Check response body size (50 MB max)
                    if (resp.body() != null && resp.body().length > 50 * 1024 * 1024) {
                        return Response.status(502)
                                .entity("Response body too large").build();
                    }
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
