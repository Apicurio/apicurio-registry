package io.apicurio.registry.agents.a2a.signature;

import io.apicurio.registry.http.OutboundAddressPolicy;
import io.apicurio.registry.http.OutboundAddressPolicy.AddressVerdict;
import io.apicurio.registry.http.OutboundConfig;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

/**
 * Fetches JSON Web Key Sets under the server-wide outbound rules: HTTPS unless
 * {@code apicurio.outbound.insecure-http.enabled}, no user information, no redirects, and no
 * connection to link-local, unspecified, multicast, reserved or cloud metadata addresses, nor to loopback
 * unless {@code apicurio.outbound.loopback.enabled} (see {@link OutboundAddressPolicy}). Connecting and
 * receiving the response headers are each bounded by
 * {@code apicurio.a2a.signature-verification.fetch.timeout.seconds}, and the response size by
 * {@code apicurio.a2a.signature-verification.fetch.max-size.bytes}.
 * <p>
 * Known limitations: the HTTP client resolves the host again when it connects, so a DNS answer that
 * changes between the check and the connection is not caught (as for the MCP import); and reading the
 * body after the headers arrived is bounded in size, not in time.
 * </p>
 */
@ApplicationScoped
public class JwksFetcher {

    @Inject
    AgentCardSignatureConfig config;

    @Inject
    OutboundConfig outboundConfig;

    private HttpClient httpClient;

    @PostConstruct
    void init() {
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.getFetchTimeoutSeconds()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * A key set could not be fetched. The message names the reason, never the response content.
     */
    public static class JwksFetchException extends Exception {

        private static final long serialVersionUID = 6390265612417735302L;

        public JwksFetchException(String message) {
            super(message);
        }
    }

    /**
     * @return the response body
     */
    public String fetch(String url) throws JwksFetchException {
        URI uri = checkTarget(url);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(config.getFetchTimeoutSeconds()))
                .header("Accept", "application/jwk-set+json, application/json")
                .GET()
                .build();
        HttpResponse<InputStream> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException e) {
            throw new JwksFetchException("request failed");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new JwksFetchException("interrupted");
        }
        try (InputStream body = response.body()) {
            if (response.statusCode() != 200) {
                throw new JwksFetchException("HTTP status " + response.statusCode());
            }
            int max = config.getFetchMaxSizeBytes();
            byte[] bytes = body.readNBytes(max + 1);
            if (bytes.length > max) {
                throw new JwksFetchException("response larger than " + max + " bytes");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new JwksFetchException("reading the response failed");
        }
    }

    private URI checkTarget(String url) throws JwksFetchException {
        URI uri;
        try {
            uri = new URI(url);
        } catch (Exception e) {
            throw new JwksFetchException("not a valid URI");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"https".equals(scheme) && !("http".equals(scheme) && outboundConfig.isInsecureHttpEnabled())) {
            throw new JwksFetchException("must use https");
        }
        if (uri.getRawUserInfo() != null) {
            throw new JwksFetchException("must not contain user information");
        }
        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            throw new JwksFetchException("must be an absolute URI with a host");
        }
        boolean loopback = outboundConfig.isLoopbackEnabled();
        try {
            AddressVerdict verdict = OutboundAddressPolicy.classifyHost(host, loopback);
            if (verdict != AddressVerdict.ALLOWED) {
                throw new JwksFetchException("host is " + verdict.getDescription());
            }
        } catch (OutboundAddressPolicy.InvalidHostException e) {
            throw new JwksFetchException("ambiguous host");
        }
        String name = host.startsWith("[") ? host.substring(1, host.length() - 1) : host;
        try {
            for (InetAddress address : InetAddress.getAllByName(name)) {
                AddressVerdict verdict = OutboundAddressPolicy.classify(address, loopback);
                if (verdict != AddressVerdict.ALLOWED) {
                    throw new JwksFetchException("host resolves to " + verdict.getDescription());
                }
            }
        } catch (UnknownHostException | SecurityException e) {
            throw new JwksFetchException("host does not resolve");
        }
        return uri;
    }
}
