package io.apicurio.registry.storage.impl.gitops;

import io.apicurio.registry.rest.v3.beans.GitOpsError;
import io.apicurio.registry.util.JsonObjectMapper;
import io.quarkus.scheduler.Scheduled;
import io.apicurio.registry.rest.v3.beans.GitOpsValidateRequest;
import io.apicurio.registry.rest.v3.beans.GitOpsValidateTask;
import io.apicurio.registry.storage.impl.polling.PollingResult;
import jakarta.annotation.PostConstruct;
import io.quarkus.arc.lookup.LookupIfProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages dry-run validation tasks for GitOps storage. Each task corresponds to a
 * validation request file on the shared volume that the sidecar processes.
 *
 * <p>Tasks are ephemeral (in-memory) but the request files on the volume survive
 * restarts. On startup, any pending request files are loaded back into memory so
 * the validation can continue.
 *
 * <p>Lifecycle: create → sidecar fetches ref → registry validates → complete/fail → expire.
 */
@ApplicationScoped
@LookupIfProperty(name = "apicurio.storage.kind", stringValue = "gitops")
public class GitOpsValidationTaskManager {

    @Inject
    Logger log;

    @Inject
    Instance<GitOpsRegistryStorage> gitOpsStorage;

    @Inject
    GitOpsConfig gitOpsConfig;

    private final ConcurrentHashMap<ValidationTaskId, ValidationTaskState> tasks = new ConcurrentHashMap<>();

    /**
     * Whether dry-run validation is enabled.
     */
    public boolean isEnabled() {
        return gitOpsConfig.isValidateEnabled();
    }

    /**
     * On startup, scan the validate directory for any existing request files
     * and re-register them so validation can continue after a restart.
     */
    @PostConstruct
    void loadPendingTasks() {
        Path workspace = Path.of(gitOpsConfig.getWorkspace());
        if (Files.exists(workspace) && !Files.isWritable(workspace)) {
            log.warn("Workspace {} is not writable by the registry process. "
                    + "Dry-run validation will fail. Ensure the shared volume is writable "
                    + "by both the registry and sidecar containers.", workspace);
        }
        Path validateDir = getValidateDir();
        if (!Files.exists(validateDir)) {
            return;
        }
        try (var files = Files.list(validateDir)) {
            files.filter(f -> f.toString().endsWith(".json"))
                    .forEach(this::loadTaskFromFile);
        } catch (IOException e) {
            log.warn("Failed to scan validate directory for pending tasks: {}", e.getMessage());
        }
    }

    /**
     * Periodically polls sidecar status for all pending/fetching tasks and triggers
     * validation when the sidecar reports completion. Also cleans up expired tasks.
     */
    @Scheduled(concurrentExecution = Scheduled.ConcurrentExecution.SKIP,
            every = "${apicurio.gitops.validate.poll.every:5s}")
    void scheduledPoll() {
        if (!isEnabled()) {
            return;
        }
        for (var taskState : tasks.values()) {
            try {
                pollSidecarStatus(taskState);
                cleanDiskIfCompleted(taskState);
            } catch (Exception e) {
                log.warn("[validate:{}] Error during scheduled poll: {}",
                        taskState.getTaskId(), e.getMessage());
            }
        }
        trySubmitPendingTasks();
        expireAll();
    }

    /**
     * Creates a new validation task. The task is queued in memory and submitted to the
     * sidecar (request file written) when there is capacity. Clients always get a taskId
     * back and poll for results — no rejections due to capacity limits.
     *
     * @param request the validation request with repoId and ref
     * @return the created task with its assigned taskId in "pending" state
     */
    public GitOpsValidateTask createTask(GitOpsValidateRequest request) {
        var taskId = ValidationTaskId.random();
        Instant now = Instant.now();

        var taskState = new ValidationTaskState();
        taskState.setTaskId(taskId);
        taskState.setType(request.getType() != null ? request.getType().value() : "pull");
        taskState.setRepoId(request.getRepoId());
        taskState.setRef(request.getRef());
        taskState.setState(ValidationTaskStatus.PENDING);
        taskState.setCreatedAt(now);
        tasks.put(taskId, taskState);

        // Try to submit immediately if there's capacity, otherwise it stays queued
        // and the scheduled poll will submit it when capacity frees up.
        trySubmitPendingTasks();

        log.info("[validate:{}] Task created: type={}, repoId={}, ref={}",
                taskId, taskState.getType(), taskState.getRepoId(), taskState.getRef());

        return toDto(taskState);
    }

    /**
     * Gets a task by ID. Returns the last known state from memory.
     * State transitions (sidecar polling, validation execution, cleanup) happen
     * asynchronously in the scheduled poll — not in this method.
     *
     * @param taskId the task identifier
     * @return the task DTO, or null if not found or expired
     */
    public GitOpsValidateTask getTask(String taskId) {
        var taskState = tasks.get(new ValidationTaskId(taskId));
        if (taskState == null) {
            return null;
        }
        return toDto(taskState);
    }

    /**
     * Lists all active (non-expired) tasks.
     */
    public List<GitOpsValidateTask> listTasks() {
        return tasks.values().stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Deletes a task and cleans up its files from the shared volume.
     *
     * @param taskId the task identifier
     * @return true if the task existed and was deleted
     */
    public boolean deleteTask(String taskId) {
        var taskState = tasks.remove(new ValidationTaskId(taskId));
        if (taskState == null) {
            return false;
        }
        cleanupFiles(taskState);
        return true;
    }

    /**
     * Reads the sidecar's status from the request file and updates the task state.
     * If the sidecar reports the fetch is complete, triggers validation execution.
     */
    void pollSidecarStatus(ValidationTaskState taskState) {
        ValidationTaskStatus currentState = taskState.getState();
        if (currentState != ValidationTaskStatus.SUBMITTED && currentState != ValidationTaskStatus.FETCHING) {
            return;
        }

        Path requestFile = getRequestFilePath(taskState.getTaskId());
        if (!Files.exists(requestFile)) {
            return;
        }

        try {
            var request = JsonObjectMapper.MAPPER.readValue(requestFile.toFile(), ValidationRequest.class);

            if (request.getApiVersion() != null
                    && !ValidationRequest.CURRENT_API_VERSION.equals(request.getApiVersion())) {
                failTask(taskState, "Unsupported apiVersion: " + request.getApiVersion()
                        + ". Expected " + ValidationRequest.CURRENT_API_VERSION);
                return;
            }

            var status = request.getStatus();
            if (status == null || status.getState() == null) {
                return;
            }

            switch (status.getState()) {
                case FETCHING -> taskState.setState(ValidationTaskStatus.FETCHING);
                case FETCHED -> {
                    taskState.setCheckoutPath(status.getCheckoutPath());
                    if (status.getCompletedAt() != null) {
                        taskState.setSidecarCompletedAt(Instant.parse(status.getCompletedAt()));
                    }
                    executeValidation(taskState);
                }
                case FAILED -> {
                    taskState.setSidecarError(status.getError() != null
                            ? status.getError() : "Unknown sidecar error");
                    failTask(taskState, taskState.getSidecarError());
                }
            }
        } catch (IOException e) {
            log.warn("[validate:{}] Failed to read sidecar status: {}", taskState.getTaskId(), e.getMessage());
        }
    }

    /**
     * Executes the validation by loading data from the sidecar's checkout into the
     * inactive storage and running the full validation pipeline.
     */
    private void executeValidation(ValidationTaskState taskState) {
        taskState.setState(ValidationTaskStatus.VALIDATING);
        log.info("[validate:{}] Starting validation from checkout path", taskState.getTaskId());

        Path checkoutPath = getCheckoutPath(taskState);
        if (checkoutPath == null || !Files.exists(checkoutPath)) {
            failTask(taskState, "Checkout path does not exist: " + checkoutPath);
            return;
        }

        try {
            var repoConfig = new GitRepoConfig(taskState.getRepoId(), ".", null);
            var tempRepo = new GitRepo(log, repoConfig, checkoutPath.toString(), gitOpsConfig);

            if (!tempRepo.tryOpen()) {
                failTask(taskState, "Failed to open git repository at " + checkoutPath);
                return;
            }

            try {
                var head = tempRepo.resolveHead();
                if (head == null) {
                    failTask(taskState, "No commits found in checkout repository");
                    return;
                }
                var pollResult = tempRepo.collectFiles(head);
                if (pollResult == null || pollResult.files().isEmpty()) {
                    failTask(taskState, "No files found in checkout");
                    return;
                }

                var pollingResult = PollingResult.withChanges(
                        new GitOpsMarker(java.util.Map.of()),
                        pollResult.files(),
                        () -> { }
                );

                var storage = gitOpsStorage.get();
                var processingResult = storage.dryRunValidate(pollingResult);

                if (processingResult.isSuccessful()) {
                    completeTask(taskState, "success",
                            processingResult.getGroupCount(),
                            processingResult.getArtifactCount(),
                            processingResult.getVersionCount(),
                            List.of());
                } else {
                    var errors = processingResult.getErrors().stream()
                            .map(e -> {
                                var err = new GitOpsError();
                                err.setDetail(e.detail());
                                err.setSource(e.source());
                                err.setContext(e.context());
                                return err;
                            }).toList();
                    completeTask(taskState, "failure",
                            processingResult.getGroupCount(),
                            processingResult.getArtifactCount(),
                            processingResult.getVersionCount(),
                            errors);
                }
            } finally {
                tempRepo.close();
            }
        } catch (io.apicurio.registry.storage.error.StorageBusyException e) {
            log.info("[validate:{}] Validation deferred — normal sync in progress, will retry",
                    taskState.getTaskId());
            taskState.setState(ValidationTaskStatus.SUBMITTED);
        } catch (Exception e) {
            log.error("[validate:{}] Validation failed: {}", taskState.getTaskId(), e.getMessage(), e);
            failTask(taskState, "Validation error: " + e.getMessage());
        }
    }

    private void completeTask(ValidationTaskState taskState, String result, int groupCount,
            int artifactCount, int versionCount, List<GitOpsError> errors) {
        taskState.setState(ValidationTaskStatus.COMPLETED);
        taskState.setResult(result);
        taskState.setGroupCount(groupCount);
        taskState.setArtifactCount(artifactCount);
        taskState.setVersionCount(versionCount);
        taskState.setErrors(errors);
        taskState.setCompletedAt(Instant.now());
    }

    private void failTask(ValidationTaskState taskState, String error) {
        taskState.setState(ValidationTaskStatus.FAILED);
        taskState.setErrors(List.of(createError(error)));
        taskState.setCompletedAt(Instant.now());
    }

    /**
     * Submits queued (pending) tasks to the sidecar by writing request files,
     * up to the configured max active tasks limit.
     */
    private void trySubmitPendingTasks() {
        int maxTasks = gitOpsConfig.getValidateMaxTasks();
        long activeTasks = tasks.values().stream()
                .filter(ts -> !ts.isCleaned() && ts.getState() != ValidationTaskStatus.PENDING)
                .count();

        for (var taskState : tasks.values()) {
            if (activeTasks >= maxTasks) {
                break;
            }
            if (taskState.getState() == ValidationTaskStatus.PENDING) {
                writeRequestFile(taskState);
                if (taskState.getState() != ValidationTaskStatus.FAILED) {
                    taskState.setState(ValidationTaskStatus.SUBMITTED);
                    activeTasks++;
                    log.info("[validate:{}] Submitted to sidecar", taskState.getTaskId());
                }
            }
        }
    }

    /**
     * Cleans up disk resources (checkout directory + request file) for completed/failed tasks
     * while keeping the in-memory record so clients can still read the result.
     */
    private void cleanDiskIfCompleted(ValidationTaskState taskState) {
        if (taskState.isCleaned()) {
            return;
        }
        ValidationTaskStatus state = taskState.getState();
        if (state == ValidationTaskStatus.COMPLETED || state == ValidationTaskStatus.FAILED) {
            if (cleanupFiles(taskState)) {
                taskState.setCleaned(true);
                log.debug("[validate:{}] Disk cleaned, result retained in memory", taskState.getTaskId());
            }
        }
    }

    // ---- File I/O ----

    private void writeRequestFile(ValidationTaskState taskState) {
        Path validateDir = getValidateDir();
        try {
            Files.createDirectories(validateDir);

            var request = ValidationRequest.builder()
                    .apiVersion(ValidationRequest.CURRENT_API_VERSION)
                    .kind("ValidateRequest")
                    .spec(ValidationRequest.Spec.builder()
                            .type(taskState.getType())
                            .repoId(taskState.getRepoId())
                            .ref(taskState.getRef())
                            .requestedAt(taskState.getCreatedAt().toString())
                            .build())
                    .build();

            JsonObjectMapper.MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValue(getRequestFilePath(taskState.getTaskId()).toFile(), request);
        } catch (IOException e) {
            log.error("[validate:{}] Failed to write request file: {}. "
                    + "Workspace={}, exists={}, writable={}, uid={}",
                    taskState.getTaskId(), e.getMessage(),
                    validateDir.getParent(), Files.exists(validateDir.getParent()),
                    Files.isWritable(validateDir.getParent()),
                    ProcessHandle.current().pid());
            taskState.setState(ValidationTaskStatus.FAILED);
            taskState.setErrors(List.of(
                    createError("Failed to write validation request file: " + e.getMessage())));
        }
    }

    /**
     * Loads a task from an existing request file on the shared volume.
     * Called during startup to recover tasks that survived a restart.
     */
    private void loadTaskFromFile(Path requestFile) {
        try {
            var request = JsonObjectMapper.MAPPER.readValue(requestFile.toFile(), ValidationRequest.class);

            if (request.getApiVersion() != null
                    && !ValidationRequest.CURRENT_API_VERSION.equals(request.getApiVersion())) {
                log.warn("Skipping validation request with unsupported apiVersion: {}",
                        request.getApiVersion());
                return;
            }

            if (request.getSpec() == null) {
                return;
            }

            var taskId = new ValidationTaskId(
                    requestFile.getFileName().toString().replace(".json", ""));

            var taskState = new ValidationTaskState();
            taskState.setTaskId(taskId);
            taskState.setType(request.getSpec().getType() != null ? request.getSpec().getType() : "pull");
            taskState.setRepoId(request.getSpec().getRepoId());
            taskState.setRef(request.getSpec().getRef());
            taskState.setCreatedAt(request.getSpec().getRequestedAt() != null
                    ? Instant.parse(request.getSpec().getRequestedAt())
                    : Instant.now());

            // Determine state from the sidecar status
            if (request.getStatus() != null && request.getStatus().getState() != null) {
                SidecarState sidecarState = request.getStatus().getState();
                switch (sidecarState) {
                    case FETCHING -> taskState.setState(ValidationTaskStatus.FETCHING);
                    case FETCHED -> taskState.setState(ValidationTaskStatus.SUBMITTED);
                    case FAILED -> taskState.setState(ValidationTaskStatus.FAILED);
                }
                taskState.setCheckoutPath(request.getStatus().getCheckoutPath());
            } else {
                taskState.setState(ValidationTaskStatus.SUBMITTED);
            }

            if (!isExpired(taskState)) {
                tasks.put(taskId, taskState);
                log.info("[validate:{}] Loaded pending task from volume: state={}, repoId={}, ref={}",
                        taskId, taskState.getState(), taskState.getRepoId(), taskState.getRef());
            }
        } catch (Exception e) {
            log.warn("Failed to load validation request from {}: {}", requestFile, e.getMessage());
        }
    }

    // ---- Helpers ----

    private Path getValidateDir() {
        return Path.of(gitOpsConfig.getWorkspace()).resolve("validate");
    }

    private Path getRequestFilePath(ValidationTaskId taskId) {
        return getValidateDir().resolve(taskId.value() + ".json");
    }

    private Path getCheckoutPath(ValidationTaskState taskState) {
        if (taskState.getCheckoutPath() == null) {
            return null;
        }
        return getValidateDir().resolve(taskState.getTaskId().value()).resolve(taskState.getCheckoutPath());
    }

    private boolean cleanupFiles(ValidationTaskState taskState) {
        try {
            Path requestFile = getRequestFilePath(taskState.getTaskId());
            Files.deleteIfExists(requestFile);
            Path taskDir = getValidateDir().resolve(taskState.getTaskId().value());
            if (Files.exists(taskDir)) {
                deleteRecursive(taskDir);
            }
            return true;
        } catch (IOException e) {
            log.warn("[validate:{}] Failed to clean up files: {}", taskState.getTaskId(), e.getMessage());
            return false;
        }
    }

    private void deleteRecursive(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (var entries = Files.list(path)) {
                for (Path entry : entries.toList()) {
                    deleteRecursive(entry);
                }
            }
        }
        Files.deleteIfExists(path);
    }

    private boolean isExpired(ValidationTaskState taskState) {
        int ttl = gitOpsConfig.getValidateTaskTtlSeconds();
        return taskState.getCreatedAt().plusSeconds(ttl).isBefore(Instant.now());
    }

    private void expireAll() {
        tasks.values().removeIf(ts -> {
            if (isExpired(ts)) {
                cleanupFiles(ts);
                return true;
            }
            return false;
        });
    }

    private GitOpsValidateTask toDto(ValidationTaskState ts) {
        var dto = new GitOpsValidateTask();
        dto.setTaskId(ts.getTaskId().value());
        dto.setType(ts.getType());
        dto.setRepoId(ts.getRepoId());
        dto.setRef(ts.getRef());
        dto.setState(GitOpsValidateTask.State.fromValue(ts.getState().value()));
        dto.setCreatedAt(Date.from(ts.getCreatedAt()));
        if (ts.getResult() != null) {
            dto.setResult(GitOpsValidateTask.Result.fromValue(ts.getResult()));
        }
        if (ts.getCompletedAt() != null) {
            dto.setCompletedAt(Date.from(ts.getCompletedAt()));
        }
        dto.setGroupCount(ts.getGroupCount());
        dto.setArtifactCount(ts.getArtifactCount());
        dto.setVersionCount(ts.getVersionCount());
        dto.setErrors(ts.getErrors() != null ? ts.getErrors() : List.of());
        return dto;
    }

    private static GitOpsError createError(String detail) {
        var err = new GitOpsError();
        err.setDetail(detail);
        return err;
    }
}
