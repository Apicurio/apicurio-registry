package io.apicurio.registry.rest;

import io.apicurio.registry.util.Priorities;
import jakarta.annotation.Priority;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * On MySQL storage, answers requests whose path names a group, artifact, version, branch,
 * subject or MCP server with a non-ASCII ID before they reach the database. MySQL stores these IDs
 * as ASCII and fails a comparison with a non-ASCII value instead of finding nothing, so such a
 * request would otherwise end in a database error. No stored ID can contain a non-ASCII character
 * there, so a read answers 404 and a write 400.
 */
@Provider
@Priority(Priorities.RequestResponseFilters.APPLICATION)
public class MysqlAsciiIdFilter implements ContainerRequestFilter {

    static final List<String> ID_PARAMETERS = List.of("groupId", "artifactId", "version", "versionExpression",
            "branchId", "subject", "serverName");

    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "DELETE");

    @ConfigProperty(name = "apicurio.storage.kind", defaultValue = "sql")
    String storageKind;

    @ConfigProperty(name = "apicurio.storage.sql.kind", defaultValue = "h2")
    String sqlKind;

    @Override
    public void filter(ContainerRequestContext request) {
        if (!"sql".equals(storageKind) || !"mysql".equals(sqlKind)) {
            return;
        }
        MultivaluedMap<String, String> parameters = request.getUriInfo().getPathParameters();
        for (String name : ID_PARAMETERS) {
            String value = parameters.getFirst(name);
            if (value != null && !StandardCharsets.US_ASCII.newEncoder().canEncode(value)) {
                if (READ_METHODS.contains(request.getMethod().toUpperCase(Locale.ROOT))) {
                    throw new NotFoundException("No resource with this " + name + " exists.");
                }
                throw new BadRequestException("Non-ASCII characters are not allowed in " + name + ".");
            }
        }
    }
}
