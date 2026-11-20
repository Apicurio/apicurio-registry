package io.apicurio.registry.agents.mcpimport;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.model.GroupId;
import io.apicurio.registry.rest.v3.beans.McpToolImportCounts;
import io.apicurio.registry.rest.v3.beans.McpToolImportJobState;
import io.apicurio.registry.rest.v3.beans.McpToolImportJobStatus;
import io.apicurio.registry.rest.v3.beans.McpToolImportResult;
import io.apicurio.registry.rest.v3.beans.McpToolImportStatus;
import io.apicurio.registry.rest.v3.beans.McpToolImportToolResult;
import io.apicurio.registry.storage.RegistryStorage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Keeps the status of the last MCP import in {@link McpImportLabels labels} of the
 * {@code MCP_SERVER} artifact. Each write replaces every label under the prefix and leaves the
 * others, so user labels are kept.
 */
@ApplicationScoped
public class McpImportStatusStore {

    static final String REASON_STALE = "stale";
    static final String REASON_FORBIDDEN = "forbidden";
    static final String REASON_FAILED = "failed";

    private static final Map<String, String> REASONS = Map.of(
            McpImportException.Reason.HOST_DENIED.tag(), "The MCP server URL is not allowed by this registry.",
            McpImportException.Reason.TIMEOUT.tag(), "The MCP server did not respond in time.",
            McpImportException.Reason.OVERSIZE.tag(), "The MCP server sent more than this registry accepts.",
            McpImportException.Reason.TOO_MANY_TOOLS.tag(), "The MCP server lists more tools than this registry imports.",
            McpImportException.Reason.PROTOCOL.tag(), "The MCP server could not be reached, or did not answer as an MCP"
                    + " server over Streamable HTTP.",
            REASON_FORBIDDEN, "The caller may not write one of the tool artifacts.",
            REASON_STALE, "The import stopped reporting progress before it finished.",
            REASON_FAILED, "The import failed.");

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    McpImportConfig config;

    void running(String groupId, String artifactId, Instant startedOn, Instant updatedOn) {
        Map<String, String> labels = new HashMap<>();
        labels.put(McpImportLabels.STATE, McpToolImportJobState.RUNNING.name());
        labels.put(McpImportLabels.STARTED_ON, startedOn.toString());
        labels.put(McpImportLabels.UPDATED_ON, updatedOn.toString());
        write(groupId, artifactId, labels);
    }

    void completed(String groupId, String artifactId, Instant startedOn, McpToolImportResult result) {
        Map<String, String> labels = finished(McpToolImportJobState.COMPLETED, startedOn);
        if (result.getVersion() != null) {
            labels.put(McpImportLabels.VERSION, result.getVersion());
        }
        Map<McpToolImportStatus, Integer> counts = new EnumMap<>(McpToolImportStatus.class);
        for (McpToolImportToolResult tool : result.getTools()) {
            counts.merge(tool.getStatus(), 1, Integer::sum);
        }
        counts.forEach((status, count) -> labels.put(McpImportLabels.COUNT_PREFIX + tag(status),
                String.valueOf(count)));
        write(groupId, artifactId, labels);
    }

    /**
     * @param reason a reason tag: a {@link McpImportException.Reason#tag()} or one of the
     *               {@code REASON_} constants
     */
    void failed(String groupId, String artifactId, Instant startedOn, String reason) {
        Map<String, String> labels = finished(McpToolImportJobState.FAILED, startedOn);
        labels.put(McpImportLabels.REASON, reason);
        write(groupId, artifactId, labels);
    }

    /**
     * @throws NotFoundException when no import was recorded for the artifact
     */
    McpToolImportJobStatus read(String groupId, String artifactId) {
        Map<String, String> labels = storage.getArtifactMetaData(raw(groupId), artifactId).getLabels();
        McpToolImportJobState state = labels == null ? null : state(labels.get(McpImportLabels.STATE));
        if (state == null) {
            throw new NotFoundException("No MCP import was recorded for this artifact.");
        }
        McpToolImportJobStatus status = new McpToolImportJobStatus();
        status.setState(state);
        Instant startedOn = instant(labels.get(McpImportLabels.STARTED_ON));
        status.setStartedOn(startedOn == null ? null : Date.from(startedOn));
        if (state == McpToolImportJobState.RUNNING) {
            Instant updatedOn = instant(labels.get(McpImportLabels.UPDATED_ON));
            if (isStale(updatedOn != null ? updatedOn : startedOn)) {
                status.setState(McpToolImportJobState.FAILED);
                status.setReason(REASONS.get(REASON_STALE));
            }
            return status;
        }
        Instant finishedOn = instant(labels.get(McpImportLabels.FINISHED_ON));
        status.setFinishedOn(finishedOn == null ? null : Date.from(finishedOn));
        if (state == McpToolImportJobState.FAILED) {
            status.setReason(REASONS.getOrDefault(labels.get(McpImportLabels.REASON), REASONS.get(REASON_FAILED)));
            return status;
        }
        status.setVersion(labels.get(McpImportLabels.VERSION));
        McpToolImportCounts counts = new McpToolImportCounts();
        for (McpToolImportStatus outcome : McpToolImportStatus.values()) {
            Integer count = count(labels.get(McpImportLabels.COUNT_PREFIX + tag(outcome)));
            if (count != null) {
                counts.setAdditionalProperty(outcome.name(), count);
            }
        }
        status.setCounts(counts);
        return status;
    }

    /**
     * Listing the tools takes at most the total timeout plus one request timeout, and a running
     * import reports progress between tools every {@link #progressInterval()}. Longer without
     * progress means the instance running it stopped.
     */
    private boolean isStale(Instant updatedOn) {
        Duration limit = Duration.ofSeconds((long) config.getTotalTimeoutSeconds() + config.getTimeoutSeconds());
        return updatedOn == null || updatedOn.plus(limit).isBefore(Instant.now());
    }

    /** How often a running import reports progress: well within the time after which it is stale. */
    Duration progressInterval() {
        return Duration.ofSeconds(Math.max(1, config.getTotalTimeoutSeconds() / 2));
    }

    private Map<String, String> finished(McpToolImportJobState state, Instant startedOn) {
        Map<String, String> labels = new HashMap<>();
        labels.put(McpImportLabels.STATE, state.name());
        labels.put(McpImportLabels.STARTED_ON, startedOn.toString());
        labels.put(McpImportLabels.FINISHED_ON, Instant.now().toString());
        return labels;
    }

    private void write(String groupId, String artifactId, Map<String, String> labels) {
        storage.mergeArtifactLabels(raw(groupId), artifactId, McpImportLabels.PREFIX, labels);
    }

    private static McpToolImportJobState state(String value) {
        try {
            return value == null ? null : McpToolImportJobState.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Instant instant(String value) {
        try {
            return value == null ? null : Instant.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static Integer count(String value) {
        try {
            return value == null ? null : Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String tag(McpToolImportStatus status) {
        return status.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** Storage uses null for the default group; the REST API uses "default". */
    private static String raw(String groupId) {
        return new GroupId(groupId).getRawGroupIdWithNull();
    }
}
