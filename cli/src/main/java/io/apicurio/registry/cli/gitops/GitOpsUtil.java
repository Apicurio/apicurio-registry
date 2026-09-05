package io.apicurio.registry.cli.gitops;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.apicurio.registry.cli.common.OutputTypeMixin;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.cli.utils.TableBuilder;
import io.apicurio.registry.rest.client.models.GitOpsError;
import io.apicurio.registry.rest.client.models.GitOpsStatus;
import io.apicurio.registry.rest.client.models.GitOpsStatusSources;
import io.apicurio.registry.rest.client.models.GitOpsValidateTask;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import static io.apicurio.registry.cli.utils.Columns.ARTIFACT_COUNT;
import static io.apicurio.registry.cli.utils.Columns.COMPLETED_AT;
import static io.apicurio.registry.cli.utils.Columns.CREATED_AT;
import static io.apicurio.registry.cli.utils.Columns.ERRORS;
import static io.apicurio.registry.cli.utils.Columns.FIELD;
import static io.apicurio.registry.cli.utils.Columns.GROUP_COUNT;
import static io.apicurio.registry.cli.utils.Columns.LAST_SUCCESSFUL_SYNC;
import static io.apicurio.registry.cli.utils.Columns.LAST_SYNC_ATTEMPT;
import static io.apicurio.registry.cli.utils.Columns.REF;
import static io.apicurio.registry.cli.utils.Columns.REPO_ID;
import static io.apicurio.registry.cli.utils.Columns.RESULT;
import static io.apicurio.registry.cli.utils.Columns.SOURCES;
import static io.apicurio.registry.cli.utils.Columns.SYNC_STATE;
import static io.apicurio.registry.cli.utils.Columns.TASK_ID;
import static io.apicurio.registry.cli.utils.Columns.VALUE;
import static io.apicurio.registry.cli.utils.Columns.VERSION_COUNT;
import static io.apicurio.registry.cli.utils.Mapper.MAPPER;

final class GitOpsUtil {

    // Sync state values from the GitOps status API. Defined here because the SDK has no enum for them.
    static final String SYNC_STATE_IDLE = "IDLE";
    static final String SYNC_STATE_ERROR = "ERROR";

    static final long POLL_INITIAL_DELAY_MS = 1000L;
    static final long POLL_INTERVAL_MS = 2000L;

    private GitOpsUtil() {
    }

    static void printStatus(final OutputBuffer output, final GitOpsStatus status,
            final OutputTypeMixin outputType) throws JsonProcessingException {
        output.writeStdOutChunkWithException(out -> {
            switch (outputType.getOutputType()) {
                case json -> {
                    out.append(MAPPER.writeValueAsString(statusToMap(status)));
                    out.append('\n');
                }
                case table -> {
                    final var table = new TableBuilder();
                    table.addColumns(FIELD, VALUE);
                    table.addRow(SYNC_STATE, nullSafe(status.getSyncState()));
                    table.addRow(LAST_SUCCESSFUL_SYNC, formatTimestamp(status.getLastSuccessfulSync()));
                    table.addRow(LAST_SYNC_ATTEMPT, formatTimestamp(status.getLastSyncAttempt()));
                    table.addRow(GROUP_COUNT, String.valueOf(status.getGroupCount()));
                    table.addRow(ARTIFACT_COUNT, String.valueOf(status.getArtifactCount()));
                    table.addRow(VERSION_COUNT, String.valueOf(status.getVersionCount()));
                    table.addRow(SOURCES, formatSources(status.getSources()));
                    table.addRow(ERRORS, formatErrors(status.getErrors()));
                    table.print(out);
                }
            }
        });
    }

    static void printValidateTask(final OutputBuffer output, final GitOpsValidateTask task,
            final OutputTypeMixin outputType) throws JsonProcessingException {
        output.writeStdOutChunkWithException(out -> {
            switch (outputType.getOutputType()) {
                case json -> {
                    out.append(MAPPER.writeValueAsString(taskToMap(task)));
                    out.append('\n');
                }
                case table -> {
                    final var table = new TableBuilder();
                    table.addColumns(FIELD, VALUE);
                    table.addRow(TASK_ID, nullSafe(task.getTaskId()));
                    table.addRow(REPO_ID, nullSafe(task.getRepoId()));
                    table.addRow(REF, nullSafe(task.getRef()));
                    table.addRow(RESULT, task.getResult() != null ? task.getResult().getValue() : "");
                    table.addRow(GROUP_COUNT, String.valueOf(task.getGroupCount()));
                    table.addRow(ARTIFACT_COUNT, String.valueOf(task.getArtifactCount()));
                    table.addRow(VERSION_COUNT, String.valueOf(task.getVersionCount()));
                    table.addRow(CREATED_AT, formatTimestamp(task.getCreatedAt()));
                    table.addRow(COMPLETED_AT, formatTimestamp(task.getCompletedAt()));
                    table.addRow(ERRORS, formatErrors(task.getErrors()));
                    table.print(out);
                }
            }
        });
    }

    private static Map<String, Object> statusToMap(final GitOpsStatus status) {
        final var map = new LinkedHashMap<String, Object>();
        map.put("syncState", status.getSyncState());
        map.put("lastSuccessfulSync", formatTimestamp(status.getLastSuccessfulSync()));
        map.put("lastSyncAttempt", formatTimestamp(status.getLastSyncAttempt()));
        map.put("groupCount", status.getGroupCount());
        map.put("artifactCount", status.getArtifactCount());
        map.put("versionCount", status.getVersionCount());
        map.put("sources", sourcesToMap(status.getSources()));
        map.put("errors", errorsToList(status.getErrors()));
        return map;
    }

    private static Map<String, Object> taskToMap(final GitOpsValidateTask task) {
        final var map = new LinkedHashMap<String, Object>();
        map.put("taskId", task.getTaskId());
        map.put("type", task.getType());
        map.put("repoId", task.getRepoId());
        map.put("ref", task.getRef());
        map.put("state", task.getState() != null ? task.getState().getValue() : null);
        map.put("result", task.getResult() != null ? task.getResult().getValue() : null);
        map.put("groupCount", task.getGroupCount());
        map.put("artifactCount", task.getArtifactCount());
        map.put("versionCount", task.getVersionCount());
        map.put("createdAt", formatTimestamp(task.getCreatedAt()));
        map.put("completedAt", formatTimestamp(task.getCompletedAt()));
        map.put("errors", errorsToList(task.getErrors()));
        return map;
    }

    private static Map<String, Object> sourcesToMap(final GitOpsStatusSources sources) {
        if (sources == null || sources.getAdditionalData() == null) {
            return Map.of();
        }
        return new LinkedHashMap<>(sources.getAdditionalData());
    }

    private static List<Map<String, String>> errorsToList(final List<GitOpsError> errors) {
        if (errors == null || errors.isEmpty()) {
            return List.of();
        }
        final var list = new ArrayList<Map<String, String>>();
        for (final var error : errors) {
            final var errorMap = new LinkedHashMap<String, String>();
            errorMap.put("detail", error.getDetail());
            if (error.getSource() != null) {
                errorMap.put("source", error.getSource());
            }
            if (error.getContext() != null) {
                errorMap.put("context", error.getContext());
            }
            list.add(errorMap);
        }
        return list;
    }

    static String formatSources(final GitOpsStatusSources sources) {
        if (sources == null || sources.getAdditionalData() == null || sources.getAdditionalData().isEmpty()) {
            return "(none)";
        }
        final var joiner = new StringJoiner(", ");
        for (final Map.Entry<String, Object> entry : sources.getAdditionalData().entrySet()) {
            joiner.add(entry.getKey() + "=" + entry.getValue());
        }
        return joiner.toString();
    }

    static String formatErrors(final List<GitOpsError> errors) {
        if (errors == null || errors.isEmpty()) {
            return "(none)";
        }
        final var sb = new StringBuilder();
        for (int i = 0; i < errors.size(); i++) {
            if (i > 0) {
                sb.append("\n");
            }
            final var error = errors.get(i);
            if (error.getSource() != null) {
                sb.append("[").append(error.getSource()).append("] ");
            }
            if (error.getContext() != null) {
                sb.append(error.getContext()).append(": ");
            }
            sb.append(error.getDetail());
        }
        return sb.toString();
    }

    private static String formatTimestamp(final OffsetDateTime timestamp) {
        return timestamp != null ? timestamp.toString() : null;
    }

    private static String nullSafe(final String value) {
        return value != null ? value : "";
    }
}
