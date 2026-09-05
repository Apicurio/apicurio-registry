package io.apicurio.registry.mcpregistry;

import io.apicurio.registry.rules.validity.McpServerContentValidator;
import jakarta.ws.rs.BadRequestException;

/**
 * An MCP server name: a reverse-DNS namespace and a server id separated by a single slash, for example
 * <code>io.github.user/weather</code>.
 *
 * The two halves map onto an Apicurio group id and artifact id respectively. Because the value reaches us as
 * path parameters, every instance is validated against
 * {@link McpServerContentValidator#SERVER_NAME_PATTERN}, which admits neither slashes nor the dot segments
 * used for path traversal.
 *
 * @param namespace the reverse-DNS namespace, which is the Apicurio group id
 * @param serverId the server id, which is the Apicurio artifact id
 */
public record McpServerName(String namespace, String serverId) {

    /**
     * Parses and validates a name supplied as two path segments.
     *
     * @throws BadRequestException if the two halves do not form a legal server name
     */
    public static McpServerName of(String namespace, String serverId) {
        String full = namespace + "/" + serverId;
        if (namespace == null || serverId == null
                || !McpServerContentValidator.SERVER_NAME_PATTERN.matcher(full).matches()) {
            throw new BadRequestException("Invalid MCP server name: expected a reverse-DNS namespace and a"
                    + " server id separated by a single slash, for example 'io.github.user/weather'");
        }
        return new McpServerName(namespace, serverId);
    }

    /**
     * Parses and validates a whole name, as it appears in the 'name' field of a server.json document.
     *
     * @throws BadRequestException if the value is not a legal server name
     */
    public static McpServerName parse(String name) {
        if (name == null || !McpServerContentValidator.SERVER_NAME_PATTERN.matcher(name).matches()) {
            throw new BadRequestException("Invalid MCP server name: expected a reverse-DNS namespace and a"
                    + " server id separated by a single slash, for example 'io.github.user/weather'");
        }
        int slash = name.indexOf('/');
        return new McpServerName(name.substring(0, slash), name.substring(slash + 1));
    }

    /**
     * @return the full server name, as it appears on the wire
     */
    public String full() {
        return namespace + "/" + serverId;
    }
}
