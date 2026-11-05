package io.apicurio.registry.agents.mcpimport;

import io.apicurio.registry.agents.mcpimport.McpToolsClient.ServerTools;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedResource;
import io.apicurio.registry.auth.ResourceAccessGuard;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.extensions.McpToolImportHandler;
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
import io.apicurio.registry.storage.error.ReadOnlyStorageException;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
 */
@ApplicationScoped
public class McpToolImportService implements McpToolImportHandler {

    static final String METRIC_TOOLS = "apicurio.mcp.import.tools";
    static final String METRIC_FAILURES = "apicurio.mcp.import.failures";

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
    MeterRegistry meterRegistry;

    private final JacksonMcpJsonMapper jsonMapper = new JacksonMcpJsonMapper();

    /**
     * Everything is checked before the MCP server is contacted: the feature, the storage, the
     * server artifact, write access to the tool group, the headers and the URL.
     */
    @Override
    public McpToolImportResult importTools(String groupId, String serverArtifactId, McpToolImportRequest request) {
        requireImportable(groupId, serverArtifactId, request);
        String toolGroupId = request.getToolGroupId() == null || request.getToolGroupId().isBlank()
                ? groupId : request.getToolGroupId();
        accessGuard.requireAccess(AuthorizedLevel.Write, AuthorizedResource.group(toolGroupId));
        Map<String, String> headers = validHeaders(request.getHeaders());
        boolean dryRun = Boolean.TRUE.equals(request.getDryRun());

        ServerTools listed = listTools(request.getUrl(), headers);
        Set<String> listedNames = checkBeforeWriting(listed.tools(), toolGroupId, serverArtifactId);

        List<McpToolImportToolResult> results = new ArrayList<>();
        List<ArtifactReference> references = new ArrayList<>();
        for (McpSchema.Tool tool : listed.tools()) {
            McpToolImportToolResult result = importTool(toolGroupId, serverArtifactId + "." + tool.name(), tool,
                    dryRun);
            results.add(result);
            count(result.getStatus(), dryRun);
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

    private ServerTools listTools(String url, Map<String, String> headers) {
        try {
            return toolsClient.listTools(targetPolicy.check(url), headers);
        } catch (McpImportException e) {
            failure(e.getReason());
            throw e;
        }
    }

    /**
     * Checked before anything is written, so neither leaves some tools imported and others not:
     * two tools with one name would map to one artifact, and an existing tool artifact may be one
     * the caller cannot write (owner-only or per-resource authorization).
     *
     * @return the names of the listed tools
     */
    private Set<String> checkBeforeWriting(List<McpSchema.Tool> tools, String toolGroupId, String serverArtifactId) {
        Set<String> listedNames = new HashSet<>();
        for (McpSchema.Tool tool : tools) {
            if (!listedNames.add(tool.name())) {
                failure(McpImportException.Reason.PROTOCOL);
                throw new McpImportException(McpImportException.Reason.PROTOCOL,
                        "The MCP server lists two tools with the same name.");
            }
            String toolArtifactId = serverArtifactId + "." + tool.name();
            if (storage.isArtifactExists(raw(toolGroupId), toolArtifactId)) {
                accessGuard.requireAccess(AuthorizedLevel.Write, AuthorizedResource.artifact(toolGroupId,
                        toolArtifactId));
            }
        }
        return listedNames;
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

    private McpToolImportToolResult importTool(String groupId, String artifactId, McpSchema.Tool tool,
            boolean dryRun) {
        McpToolImportToolResult result = new McpToolImportToolResult();
        result.setName(tool.name());
        result.setGroupId(groupId);
        result.setArtifactId(artifactId);

        boolean exists = storage.isArtifactExists(raw(groupId), artifactId);
        List<String> previousVersions = List.of();
        if (exists) {
            String type = storage.getArtifactMetaData(raw(groupId), artifactId).getArtifactType();
            if (!ArtifactType.MCP_TOOL.equals(type)) {
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
            CreateArtifactResponse response = groups.createArtifact(groupId,
                    IfArtifactExists.FIND_OR_CREATE_VERSION, false, dryRun, create);
            String stored = response.getVersion().getVersion();
            if (!exists) {
                result.setStatus(McpToolImportStatus.CREATED);
                result.setVersion(dryRun ? null : stored);
            } else if (previousVersions.contains(stored)) {
                result.setStatus(McpToolImportStatus.UNCHANGED);
                result.setVersion(stored);
            } else {
                result.setStatus(McpToolImportStatus.NEW_VERSION);
                result.setVersion(dryRun ? null : stored);
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
        if (requested == null || requested.getAdditionalProperties().isEmpty()) {
            return Map.of();
        }
        Map<String, String> headers = new LinkedHashMap<>();
        for (var header : requested.getAdditionalProperties().entrySet()) {
            String name = header.getKey();
            if (name == null || name.isEmpty() || !name.chars().allMatch(McpToolImportService::isTokenChar)
                    || RESERVED_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                // The name is safe to repeat; the value is never repeated.
                throw new BadRequestException("Header '" + sanitize(name) + "' cannot be sent to the MCP server.");
            }
            if (!(header.getValue() instanceof String value)
                    || value.chars().anyMatch(c -> c == '\r' || c == '\n')) {
                throw new BadRequestException("Header '" + sanitize(name) + "' has an invalid value.");
            }
            headers.put(name, value);
        }
        return Map.copyOf(headers);
    }

    /** A character allowed in an HTTP field name: RFC 9110 {@code tchar}. */
    private static boolean isTokenChar(int c) {
        return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9'
                || "!#$%&'*+-.^_`|~".indexOf(c) >= 0;
    }

    private static String sanitize(String name) {
        return name == null ? "" : name.replaceAll("[^A-Za-z0-9-]", "?");
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
