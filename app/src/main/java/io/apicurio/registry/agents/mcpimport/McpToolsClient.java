package io.apicurio.registry.agents.mcpimport;

import io.apicurio.registry.agents.mcpimport.McpImportException.Reason;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpTransportException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
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
 * Redirects are never followed, every request has a timeout, every message has a size bound,
 * and the number of tools and pages is bounded. Headers from the request are added to every call
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
        return listTools(uri, headers, Duration.ofSeconds(config.getTimeoutSeconds()), config.getResponseMaxSize(),
                config.getToolsMaxSize());
    }

    ServerTools listTools(URI uri, Map<String, String> headers, Duration timeout, int maxMessageSize, int maxTools) {
        Map<String, String> outbound = headers == null ? Map.of() : Map.copyOf(headers);
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
                .builder(baseUri(uri))
                .endpoint(endpoint(uri))
                .clientBuilder(HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_1_1)
                        .followRedirects(HttpClient.Redirect.NEVER))
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
            do {
                McpSchema.ListToolsResult page = client.listTools(cursor);
                if (page.tools() != null) {
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
