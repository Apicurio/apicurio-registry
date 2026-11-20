package io.apicurio.registry.agents.mcpimport;

import io.apicurio.registry.extensions.FeatureLabelOwner;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Set;

/**
 * Labels an MCP import stores on the {@code MCP_SERVER} artifact to report the status of the last
 * import, so that any registry instance can read it. They report progress only; they grant
 * nothing. Any caller with write access can change them, so a status read from them is parsed
 * defensively and its reason is always one of the fixed sentences of {@link McpImportStatusStore}.
 */
@ApplicationScoped
public class McpImportLabels implements FeatureLabelOwner {

    static final String PREFIX = "apicurio.mcp-import.";

    /** A random ID of the import the status belongs to. */
    static final String ID = PREFIX + "id";

    /** {@code RUNNING}, {@code COMPLETED} or {@code FAILED}. */
    static final String STATE = PREFIX + "state";

    /** When the import started, as an ISO-8601 instant. */
    static final String STARTED_ON = PREFIX + "started-on";

    /** When a running import last reported progress, as an ISO-8601 instant. */
    static final String UPDATED_ON = PREFIX + "updated-on";

    /** When the import finished, as an ISO-8601 instant. */
    static final String FINISHED_ON = PREFIX + "finished-on";

    /** The server version a completed import references. */
    static final String VERSION = PREFIX + "version";

    /** Why the import failed, as a reason tag, never as a message. */
    static final String REASON = PREFIX + "reason";

    /** The number of tools with an outcome, followed by the outcome in lower case. */
    static final String COUNT_PREFIX = PREFIX + "count.";

    @Override
    public Set<String> ownedLabelPrefixes() {
        return Set.of(PREFIX);
    }
}
