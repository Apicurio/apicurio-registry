package io.apicurio.registry.agents.mcpimport;

import jakarta.ws.rs.WebApplicationException;

import java.util.Locale;

/**
 * An MCP import that could not reach or use the MCP server. Messages are fixed sentences: they
 * never include the URL, its host, the addresses it resolved to, headers, or text sent by the
 * server, so a refused import does not reveal what the registry can reach or what it received.
 * <p>
 * It never carries a cause: the REST error mapper reports the root cause's message to the client,
 * and the cause of a connection failure can name the address or repeat the server's response.
 * <p>
 * It is a {@link WebApplicationException}, whose status the core error mapper uses as is, so core
 * code needs no knowledge of this agent type: 400 when the target was refused, 429 when too many
 * imports are running, 409 when one into the same artifact is, 502 otherwise.
 */
public class McpImportException extends WebApplicationException {

    private static final long serialVersionUID = -3108560431906442914L;

    /**
     * Why the import failed. The lower-case names are used as metric tag values.
     */
    public enum Reason {
        /** The URL or its host is not allowed. Answered with HTTP 400. */
        HOST_DENIED,
        /** As many imports as allowed are already running. Answered with HTTP 429. */
        BUSY,
        /** An import into the same MCP server artifact is already running. Answered with HTTP 409. */
        ALREADY_RUNNING,
        /** The server did not answer in time. Answered with HTTP 502, as are the reasons below. */
        TIMEOUT,
        /** A message from the server was larger than allowed. */
        OVERSIZE,
        /** The server listed more tools than allowed. */
        TOO_MANY_TOOLS,
        /** The server could not be reached, or did not speak MCP over Streamable HTTP. */
        PROTOCOL;

        public String tag() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }

    private final Reason reason;

    public McpImportException(Reason reason, String message) {
        super(message, status(reason));
        this.reason = reason;
    }

    private static int status(Reason reason) {
        return switch (reason) {
            case HOST_DENIED -> 400;
            case BUSY -> 429;
            case ALREADY_RUNNING -> 409;
            default -> 502;
        };
    }

    public Reason getReason() {
        return reason;
    }
}
