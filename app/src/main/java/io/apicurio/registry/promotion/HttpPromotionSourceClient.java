package io.apicurio.registry.promotion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.model.GroupId;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.storage.error.VersionNotFoundException;
import io.apicurio.registry.types.ContentTypes;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * Fetches artifact versions from another Apicurio Registry over the v3 REST API.
 */
public class HttpPromotionSourceClient implements PromotionSourceClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final String AUTHORIZATION = "Authorization";

    private final PromotionSourceDefinition source;
    private final HttpClient httpClient;

    public HttpPromotionSourceClient(PromotionSourceDefinition source, HttpClient httpClient) {
        this.source = source;
        this.httpClient = httpClient;
    }

    @Override
    public RemoteArtifactVersion fetch(String groupId, String artifactId, String versionExpression) {
        String base = normalizeBaseUrl(source.url());
        String group = new GroupId(groupId).getRawGroupIdWithDefaultString();
        String versionPath = encode(versionExpression);
        String metaUrl = base + "/groups/" + encode(group) + "/artifacts/" + encode(artifactId) + "/versions/"
                + versionPath;
        HttpResponse<String> metaResponse = send(authorized(HttpRequest.newBuilder(URI.create(metaUrl)).GET()));
        if (metaResponse.statusCode() == 404) {
            throw new VersionNotFoundException(group, artifactId, versionExpression);
        }
        if (metaResponse.statusCode() >= 400) {
            throw new PromotionRemoteException("Source registry '" + source.name() + "' returned HTTP "
                    + metaResponse.statusCode() + " for " + metaUrl);
        }
        JsonNode meta;
        try {
            meta = MAPPER.readTree(metaResponse.body());
        } catch (IOException e) {
            throw new PromotionRemoteException("Source registry returned invalid version metadata", e);
        }
        String contentUrl = metaUrl + "/content";
        HttpResponse<String> contentResponse = send(
                authorized(HttpRequest.newBuilder(URI.create(contentUrl)).GET()));
        if (contentResponse.statusCode() == 404) {
            throw new ArtifactNotFoundException(group, artifactId);
        }
        if (contentResponse.statusCode() >= 400) {
            throw new PromotionRemoteException("Source registry '" + source.name() + "' returned HTTP "
                    + contentResponse.statusCode() + " for " + contentUrl);
        }
        String contentType = contentResponse.headers().firstValue("Content-Type").orElse(ContentTypes.APPLICATION_JSON);
        int semicolon = contentType.indexOf(';');
        if (semicolon > 0) {
            contentType = contentType.substring(0, semicolon).trim();
        }
        return new RemoteArtifactVersion(text(meta, "groupId", group), text(meta, "artifactId", artifactId),
                text(meta, "version", versionExpression), text(meta, "artifactType", null), contentType,
                contentResponse.body(), text(meta, "name", null), text(meta, "description", null));
    }

    private HttpRequest authorized(HttpRequest.Builder builder) {
        builder.timeout(TIMEOUT);
        String mode = source.auth() == null ? "none" : source.auth().toLowerCase();
        switch (mode) {
            case "none", "" -> {
                // Anonymous source registry: do not attach an Authorization header.
            }
            case "bearer" -> {
                if (source.token() == null) {
                    throw new PromotionRemoteException(
                            "Promotion source '" + source.name() + "' auth=bearer requires a token");
                }
                builder.header(AUTHORIZATION, "Bearer " + source.token());
            }
            case "basic" -> {
                if (source.username() == null) {
                    throw new PromotionRemoteException(
                            "Promotion source '" + source.name() + "' auth=basic requires a username");
                }
                String raw = source.username() + ":" + (source.password() == null ? "" : source.password());
                builder.header(AUTHORIZATION,
                        "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8)));
            }
            case "oauth2" -> builder.header(AUTHORIZATION, "Bearer " + fetchOAuth2Token());
            default -> throw new PromotionRemoteException(
                    "Unsupported promotion auth mode '" + source.auth() + "' for source '" + source.name() + "'");
        }
        return builder.build();
    }

    private String fetchOAuth2Token() {
        if (source.tokenUrl() == null || source.clientId() == null) {
            throw new PromotionRemoteException("Promotion source '" + source.name()
                    + "' auth=oauth2 requires token-url and client-id");
        }
        String form = "grant_type=client_credentials&client_id=" + encode(source.clientId()) + "&client_secret="
                + encode(source.clientSecret() == null ? "" : source.clientSecret());
        HttpRequest request = HttpRequest.newBuilder(URI.create(source.tokenUrl())).timeout(TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() >= 400) {
            throw new PromotionRemoteException("OAuth2 token request failed with HTTP " + response.statusCode());
        }
        try {
            JsonNode json = MAPPER.readTree(response.body());
            if (json.hasNonNull("access_token")) {
                return json.get("access_token").asText();
            }
        } catch (IOException e) {
            throw new PromotionRemoteException("OAuth2 token response was not JSON", e);
        }
        throw new PromotionRemoteException("OAuth2 token response did not include access_token");
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new PromotionRemoteException("Failed to reach source registry '" + source.name() + "'", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PromotionRemoteException("Interrupted while calling source registry '" + source.name() + "'",
                    e);
        }
    }

    static String normalizeBaseUrl(String url) {
        String base = url.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (!base.contains("/apis/registry")) {
            base = base + "/apis/registry/v3";
        }
        return base;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String text(JsonNode node, String field, String fallback) {
        if (node != null && node.hasNonNull(field)) {
            return node.get(field).asText();
        }
        return fallback;
    }
}
