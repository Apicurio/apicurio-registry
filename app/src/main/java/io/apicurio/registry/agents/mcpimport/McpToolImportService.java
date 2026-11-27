package io.apicurio.registry.agents.mcpimport;

import io.apicurio.registry.agents.OutboundHeaders;
import io.apicurio.registry.agents.mcpimport.McpToolsClient.ServerTools;
import io.apicurio.registry.auth.AuthorizationLookups;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedResource;
import io.apicurio.registry.auth.ResourceAccessGuard;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.extensions.McpToolImportHandler;
import io.apicurio.registry.logging.audit.AuditHttpRequestContext;
import io.apicurio.registry.logging.audit.AuditLogService;
import io.apicurio.registry.model.ArtifactId;
import io.apicurio.registry.model.BranchId;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.model.GAV;
import io.apicurio.registry.model.GroupId;
import io.apicurio.registry.rest.ConflictException;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rest.v3.beans.CreateArtifact;
import io.apicurio.registry.rest.v3.beans.CreateArtifactResponse;
import io.apicurio.registry.rest.v3.beans.CreateVersion;
import io.apicurio.registry.rest.v3.beans.Headers;
import io.apicurio.registry.rest.v3.beans.IfArtifactExists;
import io.apicurio.registry.rest.v3.beans.McpToolImportJobState;
import io.apicurio.registry.rest.v3.beans.McpToolImportJobStatus;
import io.apicurio.registry.rest.v3.beans.McpToolImportRequest;
import io.apicurio.registry.rest.v3.beans.McpToolImportResult;
import io.apicurio.registry.rest.v3.beans.McpToolImportStatus;
import io.apicurio.registry.rest.v3.beans.McpToolImportToolResult;
import io.apicurio.registry.rest.v3.beans.RuleViolationCause;
import io.apicurio.registry.rest.v3.beans.VersionContent;
import io.apicurio.registry.rest.v3.beans.VersionMetaData;
import io.apicurio.registry.rest.v3.impl.GroupsResourceImpl;
import io.apicurio.registry.rules.violation.RuleViolation;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.rules.violation.UnprocessableSchemaException;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.RegistryStorage.RetrievalBehavior;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.apicurio.registry.storage.error.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.storage.error.ReadOnlyStorageException;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.modelcontextprotocol.spec.McpSchema;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.security.ForbiddenException;
import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.eclipse.microprofile.context.ThreadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static io.apicurio.registry.rest.MethodParameterKeys.MPK_ARTIFACT_ID;
import static io.apicurio.registry.rest.MethodParameterKeys.MPK_GROUP_ID;

/**
 * Imports the tools of a live MCP server as {@code MCP_TOOL} artifacts, and links them from a new
 * version of an {@code MCP_SERVER} artifact.
 * <p>
 * Every tool is published through {@link GroupsResourceImpl#createArtifact} with
 * {@link IfArtifactExists#FIND_OR_CREATE_VERSION}, called on the bean, so each write gets the same
 * authorization, rules, write hooks, audit and dry-run handling as a direct API call. A tool a
 * rule rejects is reported, and nothing is written for it.
 * <p>
 * Nothing is checked or written atomically across tools: tools accepted before a later failure
 * stay stored, as they would after the same calls made one by one.
 * <p>
 * Every import that writes, synchronous or not, records its status in labels of the server
 * artifact through {@link McpImportStatusStore}. An asynchronous import runs on its own request
 * context, with the caller's identity and audit source, so its writes are authorized, owned and
 * audited as the caller's.
 */
@ApplicationScoped
public class McpToolImportService implements McpToolImportHandler {

    static final String METRIC_TOOLS = "apicurio.mcp.import.tools";
    static final String METRIC_FAILURES = "apicurio.mcp.import.failures";
    static final String AUDIT_INVOKER = "apicurio.audit";
    static final String AUDIT_ACTION = "mcpImportTarget";
    static final String AUDIT_TARGET = "target";

    private static final Logger log = LoggerFactory.getLogger(McpToolImportService.class);

    // The tool name becomes part of an artifact ID, so it is held to the MCP specification's
    // recommended form: 1 to 128 ASCII letters, digits, '_', '-' or '.'.
    static final int TOOL_NAME_MAX_LENGTH = 128;
    private static final Pattern TOOL_NAME = Pattern.compile("[A-Za-z0-9_.-]{1," + TOOL_NAME_MAX_LENGTH + "}");

    // Headers the transport sets itself, or that the HTTP client refuses to let callers set.
    private static final Set<String> RESERVED_HEADERS = Set.of("host", "content-length", "connection",
            "content-type", "accept", "expect", "upgrade", "transfer-encoding", "te", "trailer", "keep-alive",
            "mcp-session-id", "mcp-protocol-version", "last-event-id");

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    McpImportConfig config;

    @Inject
    McpImportTargetPolicy targetPolicy;

    @Inject
    McpToolsClient toolsClient;

    @Inject
    GroupsResourceImpl groups;

    @Inject
    ResourceAccessGuard accessGuard;

    @Inject
    AuthorizationLookups lookups;

    @Inject
    MeterRegistry meterRegistry;

    @Inject
    AuditLogService auditLog;

    @Inject
    McpImportStatusStore statusStore;

    @Inject
    CurrentIdentityAssociation identityAssociation;

    @Inject
    AuditHttpRequestContext auditContext;

    private final JacksonMcpJsonMapper jsonMapper = new JacksonMcpJsonMapper();

    private Semaphore running;

    // No context is propagated: the request context of the call that started an import ends when
    // the 202 is sent, so each background import activates its own.
    private ManagedExecutor executor;

    @PostConstruct
    void init() {
        running = new Semaphore(Math.max(0, config.getMaxConcurrent()));
        executor = ManagedExecutor.builder().maxAsync(Math.max(1, config.getMaxConcurrent()))
                .propagated(ThreadContext.NONE).cleared(ThreadContext.ALL_REMAINING).build();
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    /** The number of imports that could start now. */
    int availablePermits() {
        return running.availablePermits();
    }

    /**
     * Everything is checked before the MCP server is contacted: the feature, the storage, the
     * server artifact, write access to the tool group, the headers and the URL.
     */
    @Override
    public McpToolImportResult importTools(String groupId, String serverArtifactId, McpToolImportRequest request) {
        requireImportable(groupId, serverArtifactId, request);
        String toolGroupId = toolGroupId(groupId, request);
        accessGuard.requireAccess(AuthorizedLevel.Write, AuthorizedResource.group(toolGroupId));
        Map<String, String> headers = validHeaders(request.getHeaders());
        boolean dryRun = Boolean.TRUE.equals(request.getDryRun());
        if (!dryRun) {
            requireNotRunning(groupId, serverArtifactId);
        }

        acquire();
        try {
            if (dryRun) {
                return importTools(groupId, serverArtifactId, request, toolGroupId, headers, true, () -> { });
            }
            return recorded(groupId, serverArtifactId, request, toolGroupId, headers);
        } finally {
            running.release();
        }
    }

    /**
     * Checks everything {@link #importTools(String, String, McpToolImportRequest)} checks before
     * contacting the MCP server, including the URL, so a refused import is still answered with an
     * error. The permit taken here is released when the background import ends.
     */
    @Override
    public McpToolImportJobStatus startImport(String groupId, String serverArtifactId, McpToolImportRequest request) {
        requireImportable(groupId, serverArtifactId, request);
        if (Boolean.TRUE.equals(request.getDryRun())) {
            throw new BadRequestException("An asynchronous import cannot be a dry run.");
        }
        String toolGroupId = toolGroupId(groupId, request);
        accessGuard.requireAccess(AuthorizedLevel.Write, AuthorizedResource.group(toolGroupId));
        Map<String, String> headers = validHeaders(request.getHeaders());
        checkTarget(groupId, serverArtifactId, request.getUrl());
        requireNotRunning(groupId, serverArtifactId);

        acquire();
        boolean started = false;
        try {
            String importId = UUID.randomUUID().toString();
            Instant startedOn = Instant.now();
            startStatus(groupId, serverArtifactId, importId, startedOn);
            SecurityIdentity identity = identityAssociation.getIdentity();
            String sourceIp = auditContext.getSourceIp();
            String forwardedFor = auditContext.getForwardedFor();
            executor.execute(() -> runInBackground(identity, sourceIp, forwardedFor,
                    () -> recorded(groupId, serverArtifactId, request, toolGroupId, headers, importId, startedOn)));
            started = true;

            McpToolImportJobStatus status = new McpToolImportJobStatus();
            status.setState(McpToolImportJobState.RUNNING);
            status.setStartedOn(Date.from(startedOn));
            return status;
        } finally {
            if (!started) {
                running.release();
            }
        }
    }

    @Override
    public McpToolImportJobStatus getImportStatus(String groupId, String serverArtifactId) {
        if (!config.isEnabled()) {
            throw new ConflictException("Importing tools from MCP servers is not enabled.");
        }
        return statusStore.read(groupId, serverArtifactId);
    }

    /**
     * Runs an import on a request context of its own, as the caller who started it. Its outcome is
     * in the status labels; the error is only logged here.
     */
    private void runInBackground(SecurityIdentity identity, String sourceIp, String forwardedFor,
            Supplier<McpToolImportResult> work) {
        ManagedContext requestContext = Arc.container().requestContext();
        requestContext.activate();
        try {
            identityAssociation.setIdentity(identity);
            auditContext.setSourceIp(sourceIp);
            auditContext.setForwardedFor(forwardedFor);
            work.get();
        } catch (RuntimeException e) {
            log.warn("An asynchronous MCP import failed", e);
        } finally {
            requestContext.terminate();
            running.release();
        }
    }

    private McpToolImportResult recorded(String groupId, String serverArtifactId, McpToolImportRequest request,
            String toolGroupId, Map<String, String> headers) {
        String importId = UUID.randomUUID().toString();
        Instant startedOn = Instant.now();
        startStatus(groupId, serverArtifactId, importId, startedOn);
        return recorded(groupId, serverArtifactId, request, toolGroupId, headers, importId, startedOn);
    }

    private void startStatus(String groupId, String serverArtifactId, String importId, Instant startedOn) {
        try {
            statusStore.started(groupId, serverArtifactId, importId, startedOn);
        } catch (McpImportException e) {
            failure(e.getReason());
            throw e;
        }
    }

    private void requireNotRunning(String groupId, String serverArtifactId) {
        try {
            statusStore.requireNotRunning(groupId, serverArtifactId);
        } catch (McpImportException e) {
            failure(e.getReason());
            throw e;
        }
    }

    /** Imports and records the outcome of an import already recorded as running. */
    private McpToolImportResult recorded(String groupId, String serverArtifactId, McpToolImportRequest request,
            String toolGroupId, Map<String, String> headers, String importId, Instant startedOn) {
        Progress progress = new Progress(groupId, serverArtifactId, importId, startedOn);
        McpToolImportResult result;
        try {
            result = importTools(groupId, serverArtifactId, request, toolGroupId, headers, false, progress);
        } catch (RuntimeException e) {
            try {
                statusStore.failed(groupId, serverArtifactId, importId, startedOn, reason(e));
            } catch (RuntimeException recordFailure) {
                log.warn("The failure of an MCP import could not be recorded", recordFailure);
            }
            throw e;
        }
        statusStore.completed(groupId, serverArtifactId, importId, startedOn, result);
        return result;
    }

    /** A reason tag for the status: never the message, which may not be a fixed sentence. */
    private static String reason(RuntimeException e) {
        if (e instanceof McpImportException mcp) {
            return mcp.getReason().tag();
        }
        if (e instanceof ForbiddenException) {
            return McpImportStatusStore.REASON_FORBIDDEN;
        }
        return McpImportStatusStore.REASON_FAILED;
    }

    /** Records that a running import is still making progress, at most once per interval. */
    private final class Progress implements Runnable {

        private final String groupId;
        private final String artifactId;
        private final String importId;
        private final Instant startedOn;
        private Instant reportedOn;

        Progress(String groupId, String artifactId, String importId, Instant startedOn) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.importId = importId;
            this.startedOn = startedOn;
            this.reportedOn = startedOn;
        }

        @Override
        public void run() {
            Instant now = Instant.now();
            if (reportedOn.plus(statusStore.progressInterval()).isBefore(now)) {
                statusStore.progress(groupId, artifactId, importId, startedOn, now);
                reportedOn = now;
            }
        }
    }

    private void acquire() {
        if (!running.tryAcquire()) {
            failure(McpImportException.Reason.BUSY);
            throw new McpImportException(McpImportException.Reason.BUSY,
                    "Too many MCP imports are running. Try again later.");
        }
    }

    private static String toolGroupId(String groupId, McpToolImportRequest request) {
        return request.getToolGroupId() == null || request.getToolGroupId().isBlank()
                ? groupId : request.getToolGroupId();
    }

    /**
     * @param progress run between tools, so that a long import is not reported as stale
     */
    private McpToolImportResult importTools(String groupId, String serverArtifactId, McpToolImportRequest request,
            String toolGroupId, Map<String, String> headers, boolean dryRun, Runnable progress) {
        ServerTools listed = listTools(groupId, serverArtifactId, request.getUrl(), headers);
        progress.run();
        Map<String, String> existingTypes = new HashMap<>();
        Set<String> listedNames = checkBeforeWriting(listed.tools(), toolGroupId, serverArtifactId, existingTypes,
                progress);

        List<McpToolImportToolResult> results = new ArrayList<>();
        List<ArtifactReference> references = new ArrayList<>();
        for (McpSchema.Tool tool : listed.tools()) {
            String toolArtifactId = serverArtifactId + "." + tool.name();
            McpToolImportToolResult result = importTool(toolGroupId, toolArtifactId, tool,
                    existingTypes.get(toolArtifactId), dryRun);
            results.add(result);
            count(result.getStatus(), dryRun);
            progress.run();
            if (result.getStatus() != McpToolImportStatus.REJECTED) {
                references.add(reference(tool.name(), result));
            }
        }

        StoredArtifactVersionDto previous = latest(groupId, serverArtifactId);
        List<ArtifactReferenceDto> previousReferences = previous.getReferences() == null ? List.of()
                : previous.getReferences();
        results.addAll(removedTools(previousReferences, listedNames, dryRun));

        McpToolImportResult result = new McpToolImportResult();
        result.setGroupId(groupId);
        result.setArtifactId(serverArtifactId);
        result.setTools(results);
        publishServerVersion(result, previous, previousReferences, references, dryRun);
        return result;
    }

    @Override
    public boolean isEnabled() {
        return config.isEnabled();
    }

    /**
     * Everything checked before the MCP server is contacted, apart from authorization and headers:
     * the feature, the storage, the request and the server artifact.
     */
    private void requireImportable(String groupId, String serverArtifactId, McpToolImportRequest request) {
        if (!config.isEnabled()) {
            throw new ConflictException("Importing tools from MCP servers is not enabled.");
        }
        if (storage.isReadOnly()) {
            throw new ReadOnlyStorageException("Unsupported write operation. Storage is in read-only mode.");
        }
        if (request == null) {
            throw new BadRequestException("An import request is required.");
        }
        String serverType = storage.getArtifactMetaData(raw(groupId), serverArtifactId).getArtifactType();
        if (!ArtifactType.MCP_SERVER.equals(serverType)) {
            throw new BadRequestException("Tools can only be imported into an MCP_SERVER artifact.");
        }
    }

    /**
     * Audits the host the registry was asked to connect to, and whether listing its tools
     * succeeded. Only the host and port are recorded: the path, query and headers can carry
     * credentials.
     */
    private ServerTools listTools(String groupId, String serverArtifactId, String url, Map<String, String> headers) {
        return audited(groupId, serverArtifactId, url, true,
                () -> toolsClient.listTools(targetPolicy.check(url), headers));
    }

    /**
     * Checks the URL before an asynchronous import starts, so a refused one is answered with an
     * error. Only a refusal is audited: the import audits the connection when it lists the tools.
     */
    private void checkTarget(String groupId, String serverArtifactId, String url) {
        audited(groupId, serverArtifactId, url, false, () -> targetPolicy.check(url));
    }

    private <T> T audited(String groupId, String serverArtifactId, String url, boolean auditSuccess,
            Supplier<T> action) {
        Map<String, String> audit = new HashMap<>();
        audit.put(MPK_GROUP_ID, groupId);
        audit.put(MPK_ARTIFACT_ID, serverArtifactId);
        audit.put(AUDIT_TARGET, target(url));
        try {
            T value = action.get();
            if (auditSuccess) {
                auditLog.log(AUDIT_INVOKER, AUDIT_ACTION, AuditHttpRequestContext.SUCCESS, audit, null);
            }
            return value;
        } catch (McpImportException e) {
            failure(e.getReason());
            audit.put("reason", e.getReason().tag());
            auditLog.log(AUDIT_INVOKER, AUDIT_ACTION, AuditHttpRequestContext.FAILURE, audit, null);
            throw e;
        }
    }

    /** The host and port of the URL, or an empty string when it has none. */
    static String target(String url) {
        try {
            URI uri = new URI(url);
            if (uri.getHost() == null) {
                return "";
            }
            return uri.getPort() < 0 ? uri.getHost() : uri.getHost() + ":" + uri.getPort();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Checked before anything is written, so neither leaves some tools imported and others not:
     * two tools with one name would map to one artifact, and an existing tool artifact may be one
     * the caller cannot write (owner-only or per-resource authorization).
     *
     * @param existingTypes filled with the artifact type of each listed tool's existing artifact, so
     *                      the import does not look each one up again
     * @param progress      run after each tool, so that a long check is not reported as stale
     * @return the names of the listed tools
     */
    private Set<String> checkBeforeWriting(List<McpSchema.Tool> tools, String toolGroupId, String serverArtifactId,
            Map<String, String> existingTypes, Runnable progress) {
        Set<String> listedNames = new HashSet<>();
        for (McpSchema.Tool tool : tools) {
            if (!listedNames.add(tool.name())) {
                failure(McpImportException.Reason.PROTOCOL);
                throw new McpImportException(McpImportException.Reason.PROTOCOL,
                        "The MCP server lists two tools with the same name.");
            }
            String toolArtifactId = serverArtifactId + "." + tool.name();
            // A tool with an invalid name is rejected without a lookup: some databases cannot even
            // compare such an ID (MySQL fails on non-ASCII characters, #11180).
            if (!isValidTool(tool, toolArtifactId)) {
                continue;
            }
            String existingType = existingType(toolGroupId, toolArtifactId);
            if (existingType != null) {
                existingTypes.put(toolArtifactId, existingType);
                accessGuard.requireAccess(AuthorizedLevel.Write, AuthorizedResource.artifact(toolGroupId,
                        toolArtifactId));
            }
            progress.run();
        }
        return listedNames;
    }

    /**
     * The type of the artifact, or {@code null} when it does not exist. Checks existence first so a
     * new tool does not trigger {@link ArtifactNotFoundException} (which storage metrics and tracing
     * record as a failed storage call).
     */
    private String existingType(String groupId, String artifactId) {
        String rawGroupId = raw(groupId);
        if (!storage.isArtifactExists(rawGroupId, artifactId)) {
            return null;
        }
        try {
            return storage.getArtifactMetaData(rawGroupId, artifactId).getArtifactType();
        } catch (ArtifactNotFoundException e) {
            return null;
        }
    }

    /** The tools the previous server version referenced that the server no longer lists. */
    private List<McpToolImportToolResult> removedTools(List<ArtifactReferenceDto> previousReferences,
            Set<String> listedNames, boolean dryRun) {
        List<McpToolImportToolResult> removed = new ArrayList<>();
        for (ArtifactReferenceDto old : previousReferences) {
            if (!listedNames.contains(old.getName())) {
                McpToolImportToolResult result = new McpToolImportToolResult();
                result.setName(old.getName());
                result.setGroupId(new GroupId(old.getGroupId()).getRawGroupIdWithDefaultString());
                result.setArtifactId(old.getArtifactId());
                result.setStatus(McpToolImportStatus.REMOVED);
                removed.add(result);
                count(McpToolImportStatus.REMOVED, dryRun);
            }
        }
        return removed;
    }

    /** Adds a server version referencing the imported tools, unless the latest one already does. */
    private void publishServerVersion(McpToolImportResult result, StoredArtifactVersionDto previous,
            List<ArtifactReferenceDto> previousReferences, List<ArtifactReference> references, boolean dryRun) {
        if (sameReferences(previousReferences, references)) {
            result.setStatus(McpToolImportStatus.UNCHANGED);
            result.setVersion(previous.getVersion());
        } else if (dryRun) {
            // Tools created in a dry run do not exist, so a server version referencing them could
            // not be resolved, let alone checked. The report says a new version would be added.
            result.setStatus(McpToolImportStatus.NEW_VERSION);
        } else {
            CreateVersion version = new CreateVersion();
            version.setContent(content(previous.getContent().content(), references));
            VersionMetaData created = groups.createArtifactVersion(result.getGroupId(), result.getArtifactId(),
                    false, version);
            result.setStatus(McpToolImportStatus.NEW_VERSION);
            result.setVersion(created.getVersion());
        }
    }

    /**
     * @param existingType the type of the tool's existing artifact, as found before writing, or
     *                     {@code null} when there was none
     */
    private McpToolImportToolResult importTool(String groupId, String artifactId, McpSchema.Tool tool,
            String existingType, boolean dryRun) {
        McpToolImportToolResult result = new McpToolImportToolResult();
        result.setName(tool.name());
        result.setGroupId(groupId);
        result.setArtifactId(artifactId);

        if (!isValidTool(tool, artifactId)) {
            result.setStatus(McpToolImportStatus.REJECTED);
            result.setCauses(List.of(cause("The tool name must be 1 to " + TOOL_NAME_MAX_LENGTH
                    + " ASCII letters, digits, '_', '-' or '.', as the MCP specification recommends, and the"
                    + " artifact ID it forms must not exceed 512 characters.", "/name")));
            return result;
        }

        boolean exists = existingType != null;
        List<String> previousVersions = List.of();
        if (exists) {
            if (!ArtifactType.MCP_TOOL.equals(existingType)) {
                result.setStatus(McpToolImportStatus.REJECTED);
                result.setCauses(List.of(cause("An artifact with this ID exists and is not an MCP_TOOL.",
                        "/artifactType")));
                return result;
            }
            previousVersions = storage.getArtifactVersions(raw(groupId), artifactId);
        }

        CreateArtifact create = new CreateArtifact();
        create.setArtifactId(artifactId);
        create.setArtifactType(ArtifactType.MCP_TOOL);
        create.setName(tool.title() != null ? tool.title() : tool.name());
        create.setDescription(tool.description());
        CreateVersion version = new CreateVersion();
        version.setContent(content(toolDefinition(tool), List.of()));
        create.setFirstVersion(version);
        try {
            // Exact matching, not canonical: the MCP_TOOL canonicalizer reads numbers as doubles, so
            // two bounds that round to the same double would match and the change skip its rules.
            // The definition is serialized the same way every time, so equal tools match exactly.
            // A tool the preflight found missing uses FAIL: if another request created the artifact
            // since, it is type-checked and authorized below before retrying with FIND_OR_CREATE_VERSION.
            try {
                CreateArtifactResponse response = groups.createArtifact(groupId,
                        exists ? IfArtifactExists.FIND_OR_CREATE_VERSION : IfArtifactExists.FAIL, false, dryRun,
                        create);
                recordToolOutcome(result, response.getVersion().getVersion(), exists, previousVersions, dryRun);
            } catch (ArtifactAlreadyExistsException e) {
                String concurrentType = existingType(groupId, artifactId);
                if (concurrentType == null) {
                    result.setStatus(McpToolImportStatus.REJECTED);
                    result.setCauses(List.of(cause("An artifact with this ID was created while the tools were being"
                            + " imported.", "/artifactId")));
                } else if (!ArtifactType.MCP_TOOL.equals(concurrentType)) {
                    result.setStatus(McpToolImportStatus.REJECTED);
                    result.setCauses(List.of(cause("An artifact with this ID exists and is not an MCP_TOOL.",
                            "/artifactType")));
                } else {
                    lookups.forgetArtifact(groupId, artifactId);
                    accessGuard.requireAccess(AuthorizedLevel.Write, AuthorizedResource.artifact(groupId, artifactId));
                    List<String> concurrentVersions = storage.getArtifactVersions(raw(groupId), artifactId);
                    CreateArtifactResponse response = groups.createArtifact(groupId,
                            IfArtifactExists.FIND_OR_CREATE_VERSION, false, dryRun, create);
                    recordToolOutcome(result, response.getVersion().getVersion(), true, concurrentVersions, dryRun);
                }
            }
        } catch (RuleViolationException e) {
            result.setStatus(McpToolImportStatus.REJECTED);
            result.setCauses(e.getCauses().stream().map(McpToolImportService::cause).toList());
        } catch (UnprocessableSchemaException e) {
            // A rule could not evaluate this tool, for example a schema construct the compatibility
            // check does not support. That rejects this tool only, as a violation would.
            result.setStatus(McpToolImportStatus.REJECTED);
            result.setCauses(List.of(cause(e.getMessage(), "")));
        }
        return result;
    }

    private static void recordToolOutcome(McpToolImportToolResult result, String stored, boolean existed,
            List<String> previousVersions, boolean dryRun) {
        if (!existed) {
            result.setStatus(McpToolImportStatus.CREATED);
            result.setVersion(dryRun ? null : stored);
        } else if (previousVersions.contains(stored)) {
            result.setStatus(McpToolImportStatus.UNCHANGED);
            result.setVersion(stored);
        } else {
            result.setStatus(McpToolImportStatus.NEW_VERSION);
            result.setVersion(dryRun ? null : stored);
        }
    }

    /**
     * The MCP_TOOL document: the fields of the tool that describe its contract, in a fixed order.
     */
    private String toolDefinition(McpSchema.Tool tool) {
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("name", tool.name());
        putIfPresent(definition, "title", tool.title());
        putIfPresent(definition, "description", tool.description());
        definition.put("inputSchema", tool.inputSchema());
        putIfPresent(definition, "outputSchema", tool.outputSchema());
        putIfPresent(definition, "annotations", tool.annotations());
        try {
            return jsonMapper.writeValueAsString(definition);
        } catch (Exception e) {
            throw new McpImportException(McpImportException.Reason.PROTOCOL,
                    "A tool listed by the MCP server could not be read.");
        }
    }

    private static void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private StoredArtifactVersionDto latest(String groupId, String artifactId) {
        GAV tip = storage.getBranchTip(new GA(raw(groupId), artifactId), BranchId.LATEST,
                RetrievalBehavior.ALL_STATES);
        return storage.getArtifactVersionContent(raw(groupId), artifactId, tip.getRawVersionId());
    }

    private static ArtifactReference reference(String name, McpToolImportToolResult result) {
        ArtifactReference reference = new ArtifactReference();
        reference.setName(name);
        reference.setGroupId(result.getGroupId());
        reference.setArtifactId(result.getArtifactId());
        reference.setVersion(result.getVersion());
        return reference;
    }

    private static boolean sameReferences(List<ArtifactReferenceDto> previous, List<ArtifactReference> next) {
        // In a dry run a new tool version has no number yet, so it can never match.
        Set<List<String>> a = previous.stream()
                .map(r -> List.of(Objects.toString(r.getName()), Objects.toString(raw(r.getGroupId())),
                        r.getArtifactId(), Objects.toString(r.getVersion())))
                .collect(Collectors.toSet());
        Set<List<String>> b = next.stream()
                .map(r -> List.of(Objects.toString(r.getName()), Objects.toString(raw(r.getGroupId())),
                        r.getArtifactId(), Objects.toString(r.getVersion())))
                .collect(Collectors.toSet());
        return a.equals(b) && previous.size() == next.size();
    }

    /** Storage uses null for the default group; the REST API uses "default". */
    private static String raw(String groupId) {
        return new GroupId(groupId).getRawGroupIdWithNull();
    }

    private static VersionContent content(String json, List<ArtifactReference> references) {
        VersionContent content = new VersionContent();
        content.setContent(json);
        content.setContentType(ContentTypes.APPLICATION_JSON);
        content.setReferences(references);
        return content;
    }

    private static Map<String, String> validHeaders(Headers requested) {
        return OutboundHeaders.validate(requested, RESERVED_HEADERS, "the MCP server");
    }

    private static boolean isValidTool(McpSchema.Tool tool, String artifactId) {
        return isValidToolName(tool.name()) && ArtifactId.isValid(artifactId);
    }

    private static boolean isValidToolName(String name) {
        return name != null && TOOL_NAME.matcher(name).matches();
    }

    private static RuleViolationCause cause(RuleViolation violation) {
        RuleViolationCause cause = cause(violation.getDescription(), violation.getContext());
        cause.setCode(violation.getCode());
        cause.setDirection(violation.getDirection());
        cause.setProposed(violation.getProposed());
        cause.setExisting(violation.getExisting());
        return cause;
    }

    private static RuleViolationCause cause(String description, String context) {
        RuleViolationCause cause = new RuleViolationCause();
        cause.setDescription(description);
        cause.setContext(context);
        return cause;
    }

    /** Dry runs write nothing, so they are not counted as imported tools. */
    private void count(McpToolImportStatus status, boolean dryRun) {
        if (dryRun) {
            return;
        }
        Counter.builder(METRIC_TOOLS).description("Tools processed by MCP imports, by result")
                .tag("result", status.name().toLowerCase(Locale.ROOT).replace('_', '-'))
                .register(meterRegistry).increment();
    }

    private void failure(McpImportException.Reason reason) {
        Counter.builder(METRIC_FAILURES).description("MCP imports that could not reach or use the MCP server, by reason")
                .tag("reason", reason.tag())
                .register(meterRegistry).increment();
    }
}
