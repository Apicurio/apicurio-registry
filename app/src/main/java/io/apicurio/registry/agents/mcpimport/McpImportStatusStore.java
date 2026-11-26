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
import io.apicurio.registry.storage.error.ContractStateConflictException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger log = LoggerFactory.getLogger(McpImportStatusStore.class);

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

    /**
     * Fast-fail check before acquiring a concurrency permit: refuses to start an import into an
     * artifact whose recorded import is still running and not stale. {@link #started} then enforces
     * this atomically with a compare-and-set on the status labels.
     *
     * @throws McpImportException with {@link McpImportException.Reason#ALREADY_RUNNING}
     */
    void requireNotRunning(String groupId, String artifactId) {
        Map<String, String> labels = labels(groupId, artifactId);
        if (isRunning(labels)) {
            throw alreadyRunning();
        }
    }

    /**
     * Atomically records a new running import if no non-stale import is already running, replacing
     * the status of any earlier finished or stale import.
     *
     * @throws McpImportException with {@link McpImportException.Reason#ALREADY_RUNNING} if another
     *                            import is already running or started concurrently
     */
    void started(String groupId, String artifactId, String importId, Instant startedOn) {
        Map<String, String> current = labels(groupId, artifactId);
        if (isRunning(current)) {
            throw alreadyRunning();
        }
        Map<String, String> expected = new HashMap<>();
        expected.put(McpImportLabels.ID, current.get(McpImportLabels.ID));
        expected.put(McpImportLabels.STATE, current.get(McpImportLabels.STATE));
        expected.put(McpImportLabels.UPDATED_ON, current.get(McpImportLabels.UPDATED_ON));
        expected.put(McpImportLabels.STARTED_ON, current.get(McpImportLabels.STARTED_ON));
        try {
            storage.mergeArtifactLabels(raw(groupId), artifactId, McpImportLabels.PREFIX,
                    runningLabels(importId, startedOn, startedOn), expected);
        } catch (ContractStateConflictException e) {
            throw alreadyRunning();
        }
    }

    private boolean isRunning(Map<String, String> labels) {
        return state(labels.get(McpImportLabels.STATE)) == McpToolImportJobState.RUNNING && !isStale(labels);
    }

    private static McpImportException alreadyRunning() {
        return new McpImportException(McpImportException.Reason.ALREADY_RUNNING,
                "An import into this MCP server is already running.");
    }

    /** Records that the import is still making progress, unless a newer import replaced it. */
    void progress(String groupId, String artifactId, String importId, Instant startedOn, Instant updatedOn) {
        writeIfOwned(groupId, artifactId, importId, runningLabels(importId, startedOn, updatedOn));
    }

    void completed(String groupId, String artifactId, String importId, Instant startedOn,
            McpToolImportResult result) {
        Map<String, String> labels = finished(McpToolImportJobState.COMPLETED, importId, startedOn);
        if (result.getVersion() != null) {
            labels.put(McpImportLabels.VERSION, result.getVersion());
        }
        Map<McpToolImportStatus, Integer> counts = new EnumMap<>(McpToolImportStatus.class);
        for (McpToolImportToolResult tool : result.getTools()) {
            counts.merge(tool.getStatus(), 1, Integer::sum);
        }
        counts.forEach((status, count) -> labels.put(McpImportLabels.COUNT_PREFIX + tag(status),
                String.valueOf(count)));
        writeIfOwned(groupId, artifactId, importId, labels);
    }

    /**
     * @param reason a reason tag: a {@link McpImportException.Reason#tag()} or one of the
     *               {@code REASON_} constants
     */
    void failed(String groupId, String artifactId, String importId, Instant startedOn, String reason) {
        Map<String, String> labels = finished(McpToolImportJobState.FAILED, importId, startedOn);
        labels.put(McpImportLabels.REASON, reason);
        writeIfOwned(groupId, artifactId, importId, labels);
    }

    private Map<String, String> runningLabels(String importId, Instant startedOn, Instant updatedOn) {
        Map<String, String> labels = new HashMap<>();
        labels.put(McpImportLabels.ID, importId);
        labels.put(McpImportLabels.STATE, McpToolImportJobState.RUNNING.name());
        labels.put(McpImportLabels.STARTED_ON, startedOn.toString());
        labels.put(McpImportLabels.UPDATED_ON, updatedOn.toString());
        return labels;
    }

    /**
     * Atomically writes only while the recorded status is still this import's, so an older import
     * never replaces the status of a newer one.
     */
    private void writeIfOwned(String groupId, String artifactId, String importId, Map<String, String> labels) {
        try {
            storage.mergeArtifactLabels(raw(groupId), artifactId, McpImportLabels.PREFIX, labels,
                    Map.of(McpImportLabels.ID, importId));
        } catch (ContractStateConflictException e) {
            log.debug("Not recording the status of MCP import {} into {}/{}: a newer import replaced it", importId,
                    groupId, artifactId);
        }
    }

    /**
     * The labels can be edited by hand, so each one is read defensively and never fails the
     * request: a missing or unknown state means no import is recorded (404); a date or count that
     * cannot be read is left out; a failure with a missing or unknown reason gets the generic one;
     * and a running import with no readable time is reported as stale.
     *
     * @throws NotFoundException when no import was recorded for the artifact
     */
    McpToolImportJobStatus read(String groupId, String artifactId) {
        Map<String, String> labels = labels(groupId, artifactId);
        McpToolImportJobState state = state(labels.get(McpImportLabels.STATE));
        if (state == null) {
            throw new NotFoundException("No MCP import was recorded for this artifact.");
        }
        McpToolImportJobStatus status = new McpToolImportJobStatus();
        status.setState(state);
        status.setStartedOn(date(labels.get(McpImportLabels.STARTED_ON)));
        if (state == McpToolImportJobState.RUNNING) {
            if (isStale(labels)) {
                status.setState(McpToolImportJobState.FAILED);
                status.setReason(reason(REASON_STALE));
            }
            return status;
        }
        status.setFinishedOn(date(labels.get(McpImportLabels.FINISHED_ON)));
        if (state == McpToolImportJobState.FAILED) {
            status.setReason(reason(labels.get(McpImportLabels.REASON)));
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

    /** The fixed sentence for a reason tag; the generic one for a missing or unknown tag. */
    private static String reason(String tag) {
        String reason = tag == null ? null : REASONS.get(tag);
        return reason != null ? reason : REASONS.get(REASON_FAILED);
    }

    private boolean isStale(Map<String, String> labels) {
        Instant updatedOn = instant(labels.get(McpImportLabels.UPDATED_ON));
        return isStale(updatedOn != null ? updatedOn : instant(labels.get(McpImportLabels.STARTED_ON)));
    }

    private Map<String, String> labels(String groupId, String artifactId) {
        Map<String, String> labels = storage.getArtifactMetaData(raw(groupId), artifactId).getLabels();
        return labels == null ? Map.of() : labels;
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

    private Map<String, String> finished(McpToolImportJobState state, String importId, Instant startedOn) {
        Map<String, String> labels = new HashMap<>();
        labels.put(McpImportLabels.ID, importId);
        labels.put(McpImportLabels.STATE, state.name());
        labels.put(McpImportLabels.STARTED_ON, startedOn.toString());
        labels.put(McpImportLabels.FINISHED_ON, Instant.now().toString());
        return labels;
    }

    private static McpToolImportJobState state(String value) {
        try {
            return value == null ? null : McpToolImportJobState.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Date date(String value) {
        Instant instant = instant(value);
        return instant == null ? null : Date.from(instant);
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
