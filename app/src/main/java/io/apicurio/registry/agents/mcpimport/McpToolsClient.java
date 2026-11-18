package io.apicurio.registry.agents.mcpimport;

import io.apicurio.registry.agents.mcpimport.McpImportException.Reason;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpTransportException;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.tls.TlsConfiguration;
import io.quarkus.tls.TlsConfigurationRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;

/**
 * Lists the tools of a live MCP server, over the Streamable HTTP transport of the official MCP
 * Java SDK.
 * <p>
 * Redirects are never followed, every request has a timeout and the whole listing a deadline,
 * every message and the listed tools as a whole have a size bound, and the number of tools and
 * pages is bounded. HTTPS connections trust the JVM's default truststore, or the TLS configuration
 * named by {@code apicurio.mcp-import.tls-configuration.name}. Headers from the request are added to every call
 * to the server and nowhere else.
 */
@ApplicationScoped
public class McpToolsClient {

    private static final Logger log = LoggerFactory.getLogger(McpToolsClient.class);

    private static final McpSchema.Implementation CLIENT_INFO = new McpSchema.Implementation(
            "apicurio-registry-mcp-import", "1.0.0");

    // The import lists tools and never calls them, so tool results are never validated.
    private static final JsonSchemaValidator NO_RESULT_VALIDATION = (schema, content) -> JsonSchemaValidator.ValidationResponse
            .asValid(null);

    // The HTTP transport reports a message over maxResponseSize (whole body, SSE line or SSE
    // event) as an McpTransportException with this phrase and no more specific type.
    // McpToolsClientTest pins it: an SDK change fails that test instead of turning oversize
    // responses into protocol errors.
    static final String SDK_MAX_SIZE_PHRASE = "exceeds the maximum allowed size";

    private final JacksonMcpJsonMapper jsonMapper = new JacksonMcpJsonMapper();

    @Inject
    McpImportConfig config;

    @Inject
    TlsConfigurationRegistry tlsRegistry;

    private volatile SSLContext sslContext;

    /**
     * The handshake result and every tool the server lists, in order.
     */
    public record ServerTools(McpSchema.Implementation serverInfo, List<McpSchema.Tool> tools) {
    }

    /**
     * @param uri a URL already allowed by {@link McpImportTargetPolicy}
     * @throws McpImportException if the server cannot be reached or its answer cannot be used
     */
    public ServerTools listTools(URI uri, Map<String, String> headers) {
        return listTools(uri, headers, new Limits(Duration.ofSeconds(config.getTimeoutSeconds()),
                Duration.ofSeconds(config.getTotalTimeoutSeconds()), config.getResponseMaxSize(),
                config.getToolsMaxSize(), config.getToolsMaxTotalSize()), sslContext);
    }

    /**
     * Resolves the TLS configuration at startup, so a name that does not exist stops the registry
     * instead of failing each import.
     */
    void resolveTls(@Observes StartupEvent event) {
        sslContext = sslContext(tlsRegistry, config.getTlsConfigurationName().orElse(null));
    }

    /**
     * @return the SSL context of the named TLS configuration, or null for the JVM default
     * @throws IllegalStateException if no TLS configuration has this name
     */
    static SSLContext sslContext(TlsConfigurationRegistry registry, String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        TlsConfiguration tls = registry.get(name).orElseThrow(() -> new IllegalStateException(
                "apicurio.mcp-import.tls-configuration.name is '" + name
                        + "', but no TLS configuration has that name (quarkus.tls." + name + ".*)"));
        try {
            return tls.createSSLContext();
        } catch (Exception e) {
            throw new IllegalStateException("The TLS configuration '" + name + "' cannot be used for MCP imports", e);
        }
    }

    /**
     * @param timeout for connecting and for each request
     * @param totalTimeout for the whole listing, checked between requests
     * @param maxMessageSize of each message from the server
     * @param maxTools listed in total
     * @param maxTotalSize of the listed tool definitions, serialized as JSON
     */
    record Limits(Duration timeout, Duration totalTimeout, int maxMessageSize, int maxTools, long maxTotalSize) {
    }

    ServerTools listTools(URI uri, Map<String, String> headers, Limits limits) {
        return listTools(uri, headers, limits, null);
    }

    /**
     * @param ssl for HTTPS connections, or null for the JVM default
     */
    ServerTools listTools(URI uri, Map<String, String> headers, Limits limits, SSLContext ssl) {
        Duration timeout = limits.timeout();
        int maxMessageSize = limits.maxMessageSize();
        int maxTools = limits.maxTools();
        long deadline = System.nanoTime() + limits.totalTimeout().toNanos();
        Map<String, String> outbound = headers == null ? Map.of() : Map.copyOf(headers);
        HttpClient.Builder httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER);
        if (ssl != null) {
            httpClient.sslContext(ssl);
        }
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
                .builder(baseUri(uri))
                .endpoint(endpoint(uri))
                .clientBuilder(httpClient)
                .connectTimeout(timeout)
                .maxResponseSize(maxMessageSize)
                .jsonMapper(jsonMapper)
                .httpRequestCustomizer((builder, method, endpoint, body, context) -> outbound.forEach(builder::header))
                .build();

        McpSyncClient client = McpClient.sync(transport)
                .clientInfo(CLIENT_INFO)
                .requestTimeout(timeout)
                .initializationTimeout(timeout)
                .jsonSchemaValidator(NO_RESULT_VALIDATION)
                .build();
        try {
            McpSchema.InitializeResult init = client.initialize();
            List<McpSchema.Tool> tools = new ArrayList<>();
            Set<String> seenCursors = new HashSet<>();
            String cursor = null;
            long totalSize = 0;
            do {
                requireTimeLeft(deadline);
                McpSchema.ListToolsResult page = client.listTools(cursor);
                if (page.tools() != null) {
                    totalSize += serializedSize(page.tools());
                    if (totalSize > limits.maxTotalSize()) {
                        throw new McpImportException(Reason.OVERSIZE,
                                "The tools listed by the MCP server are larger in total than this registry accepts.");
                    }
                    tools.addAll(page.tools());
                }
                if (tools.size() > maxTools) {
                    throw new McpImportException(Reason.TOO_MANY_TOOLS,
                            "The MCP server lists more tools than this registry imports (" + maxTools + ").");
                }
                cursor = page.nextCursor();
                // A server repeating a cursor would page forever; one that pages without tools too.
                if (cursor != null && (!seenCursors.add(cursor) || page.tools() == null || page.tools().isEmpty())) {
                    throw new McpImportException(Reason.PROTOCOL,
                            "The MCP server's tool list does not end.");
                }
            } while (cursor != null);
            return new ServerTools(init.serverInfo(), tools);
        } catch (McpImportException e) {
            throw e;
        } catch (RuntimeException e) {
            throw translate(e);
        } finally {
            closeQuietly(client);
        }
    }

    private static void requireTimeLeft(long deadline) {
        if (System.nanoTime() - deadline > 0) {
            throw new McpImportException(Reason.TIMEOUT, "The MCP server did not list its tools in time.");
        }
    }

    private long serializedSize(List<McpSchema.Tool> tools) {
        try {
            return jsonMapper.writeValueAsString(tools).getBytes(StandardCharsets.UTF_8).length;
        } catch (Exception e) {
            throw new McpImportException(Reason.PROTOCOL, "A tool listed by the MCP server could not be read.");
        }
    }

    private static McpImportException translate(Throwable e) {
        if (log.isDebugEnabled()) {
            log.debug("MCP import could not list tools: {}", causeTypes(e));
        }
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof McpTransportException && t.getMessage() != null
                    && t.getMessage().contains(SDK_MAX_SIZE_PHRASE)) {
                return new McpImportException(Reason.OVERSIZE,
                        "A message from the MCP server is larger than this registry accepts.");
            }
            if (t instanceof TimeoutException || t instanceof HttpTimeoutException) {
                return new McpImportException(Reason.TIMEOUT, "The MCP server did not respond in time.");
            }
        }
        return new McpImportException(Reason.PROTOCOL,
                "The MCP server could not be reached, or did not answer as an MCP server over Streamable HTTP.");
    }

    /**
     * The exception types along the cause chain. Messages are left out: an exception raised while
     * sending a request can repeat a header value, and one raised while reading can repeat what
     * the server sent.
     */
    static String causeTypes(Throwable e) {
        StringBuilder types = new StringBuilder();
        Set<Throwable> seen = new HashSet<>();
        for (Throwable t = e; t != null && seen.add(t); t = t.getCause()) {
            if (!types.isEmpty()) {
                types.append(" <- ");
            }
            types.append(t.getClass().getName());
        }
        return types.toString();
    }

    private static void closeQuietly(McpSyncClient client) {
        try {
            client.closeGracefully();
        } catch (RuntimeException e) {
            if (log.isDebugEnabled()) {
                log.debug("Closing the MCP import client failed: {}", causeTypes(e));
            }
        }
    }

    private static String baseUri(URI uri) {
        return uri.getScheme() + "://" + uri.getRawAuthority();
    }

    private static String endpoint(URI uri) {
        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
    }
}
