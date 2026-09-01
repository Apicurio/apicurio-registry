package io.apicurio.registry.mcpregistry.rest.v0.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.auth.AuthConfig;
import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.auth.RoleBasedAccessController;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.logging.audit.Audited;
import io.apicurio.registry.mcpregistry.McpRegistryConfig;
import io.apicurio.registry.mcpregistry.McpRegistryCursor;
import io.apicurio.registry.mcpregistry.McpServerName;
import io.apicurio.registry.mcpregistry.rest.v0.ApisResource;
import io.apicurio.registry.mcpregistry.rest.v0.beans.ListMetadata;
import io.apicurio.registry.mcpregistry.rest.v0.beans.Meta;
import io.apicurio.registry.mcpregistry.rest.v0.beans.Server;
import io.apicurio.registry.mcpregistry.rest.v0.beans.ServerList;
import io.apicurio.registry.mcpregistry.rest.v0.beans.ServerStatus;
import io.apicurio.registry.mcpregistry.rest.v0.beans.StatusUpdate;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.model.BranchId;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.rules.violation.RuleViolation;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.RegistryStorage.RetrievalBehavior;
import io.apicurio.registry.storage.dto.ArtifactSearchResultsDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.EditableVersionMetaDataDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.SearchedArtifactDto;
import io.apicurio.registry.storage.dto.SearchedVersionDto;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.apicurio.registry.storage.dto.VersionSearchResultsDto;
import io.apicurio.registry.storage.error.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.storage.error.VersionAlreadyExistsException;
import io.apicurio.registry.storage.error.VersionNotFoundException;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.types.VersionState;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptors;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of the official MCP Registry API, backed by ordinary registry artifacts.
 *
 * <code>io.github.user/weather</code> is the artifact <code>weather</code> in group
 * <code>io.github.user</code>; a server version is an artifact version holding the
 * <code>server.json</code> verbatim; active/deprecated/deleted map to ENABLED/DEPRECATED/DISABLED.
 *
 * @see <a href="https://github.com/modelcontextprotocol/registry">MCP Registry</a>
 */
@ApplicationScoped
@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
@Logged
public class McpRegistryApiResourceImpl implements ApisResource {

    private static final Logger log = LoggerFactory.getLogger(McpRegistryApiResourceImpl.class);

    /** The registry owns this key in '_meta'; every other key belongs to the publisher. */
    private static final String REGISTRY_META_KEY = "io.modelcontextprotocol.registry/official";

    /**
     * Label holding a published version's UUID. {@code globalId} is unique only within one registry
     * instance, so the id is minted at publish time and persisted rather than recomputed.
     */
    private static final String SERVER_VERSION_ID_LABEL = "mcp-server-version-id";

    private static final String META_ID = "id";
    private static final String META_PUBLISHED_AT = "publishedAt";
    private static final String META_UPDATED_AT = "updatedAt";
    private static final String META_IS_LATEST = "isLatest";
    private static final String META_STATUS = "status";

    private static final String LATEST_VERSION = "latest";
    private static final int DEFAULT_PAGE_SIZE = 30;

    /**
     * Sentinel distinguishing "the caller did not resolve a latest version" from "the caller resolved it,
     * and it is genuinely null" (every version of the server is disabled). Compared by reference, not
     * {@code equals}, precisely so a real {@code null} can never be mistaken for it.
     */
    private static final String UNKNOWN_LATEST_VERSION = new String("unknown-latest-version"); // NOSONAR

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    McpRegistryConfig config;

    @Inject
    AuthConfig authConfig;

    @Inject
    RoleBasedAccessController rbac;

    @Inject
    ArtifactTypeUtilProviderFactory factory;

    private void requireEnabled() {
        if (!config.isEnabled()) {
            throw new NotFoundException("MCP Registry API is disabled");
        }
    }

    /**
     * Report a clean 403 before a read-only backend throws an internal error.
     */
    private void requireWritable() {
        if (storage.isReadOnly()) {
            throw new ForbiddenException(
                    "The MCP Registry API does not support publishing on a read-only storage backend.");
        }
    }

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public ServerList listServers(String cursor, BigInteger limit, String search, Date updatedSince,
            String version) {
        requireEnabled();

        int pageSize = pageSize(limit);
        String fingerprint = "servers|" + nullSafe(search) + "|"
                + (updatedSince == null ? "" : updatedSince.toInstant()) + "|" + nullSafe(version);
        int offset = McpRegistryCursor.decode(cursor, fingerprint);

        Set<SearchFilter> filters = new HashSet<>();
        filters.add(SearchFilter.ofArtifactType(ArtifactType.MCP_SERVER));
        if (search != null && !search.isBlank()) {
            filters.add(SearchFilter.ofPartialName(search));
        }

        // Order by modifiedOn for updated_since pagination; otherwise name-order paging can break
        // on namespace ties.
        boolean byModifiedOn = updatedSince != null;
        ArtifactSearchResultsDto results = storage.searchArtifacts(filters,
                byModifiedOn ? OrderBy.modifiedOn : OrderBy.name,
                byModifiedOn ? OrderDirection.desc : OrderDirection.asc, offset, pageSize, false);

        List<Server> servers = new ArrayList<>();
        boolean reachedCutoff = false;
        for (SearchedArtifactDto artifact : results.getArtifacts()) {
            // modifiedOn has no tiebreaker, so an exact cutoff can silently stop one page early.
            // null modifiedOn is kept, which should not happen for published MCP servers.
            if (byModifiedOn && artifact.getModifiedOn() != null
                    && artifact.getModifiedOn().before(updatedSince)) {
                reachedCutoff = true;
                break;
            }
            McpServerName name = new McpServerName(artifact.getGroupId(), artifact.getArtifactId());
            Server server = tryLoadServer(name, version);
            if (server != null) {
                servers.add(server);
            }
        }

        boolean hasMore = !reachedCutoff && offset + pageSize < results.getCount();
        return buildServerList(servers, hasMore ? offset + pageSize : -1, fingerprint);
    }

    @Override
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Read)
    public Server getServer(String namespace, String serverId) {
        requireEnabled();
        return loadServer(McpServerName.of(namespace, serverId), LATEST_VERSION);
    }

    @Override
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Read)
    public ServerList listServerVersions(String namespace, String serverId, String cursor,
            BigInteger limit) {
        requireEnabled();

        McpServerName name = McpServerName.of(namespace, serverId);
        // Surfaces a 404 for an unknown server, rather than an empty page.
        storage.getArtifactMetaData(name.namespace(), name.serverId());

        int pageSize = pageSize(limit);
        String fingerprint = "versions|" + name.full();
        int offset = McpRegistryCursor.decode(cursor, fingerprint);

        Set<SearchFilter> filters = new HashSet<>();
        filters.add(SearchFilter.ofGroupId(name.namespace()));
        filters.add(SearchFilter.ofArtifactId(name.serverId()));

        // globalId rather than createdOn: same-millisecond versions tie, and paging needs a total order.
        VersionSearchResultsDto results = storage.searchVersions(filters, OrderBy.globalId,
                OrderDirection.asc, offset, pageSize, false);

        // Resolved once: the server name is fixed for the whole page, so every version would otherwise
        // repeat an identical storage.getBranchTip() lookup inside loadServer/decorate.
        String latestVersion = latestVersionOrNull(name);
        List<Server> servers = new ArrayList<>();
        for (SearchedVersionDto searched : results.getVersions()) {
            Server server = tryLoadServer(name, searched.getVersion(), latestVersion);
            if (server != null) {
                servers.add(server);
            }
        }
        log.debug("Listed {} version(s) of MCP server {} (latest is {})", servers.size(), name.full(),
                latestVersion);

        boolean hasMore = offset + pageSize < results.getCount();
        return buildServerList(servers, hasMore ? offset + pageSize : -1, fingerprint);
    }

    @Override
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Read)
    public Server getServerVersion(String namespace, String serverId, String version) {
        requireEnabled();
        return loadServer(McpServerName.of(namespace, serverId), version);
    }

    @Override
    @Audited
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public void deleteServerVersion(String namespace, String serverId, String version) {
        requireEnabled();
        requireWritable();
        McpServerName name = McpServerName.of(namespace, serverId);
        storage.deleteArtifactVersion(name.namespace(), name.serverId(), resolveVersion(name, version));
    }

    @Override
    @Audited
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public Server updateServerVersionStatus(String namespace, String serverId, String version,
            StatusUpdate data) {
        requireEnabled();
        requireWritable();
        McpServerName name = McpServerName.of(namespace, serverId);
        String resolved = resolveVersion(name, version);
        storage.updateArtifactVersionState(name.namespace(), name.serverId(), resolved,
                toVersionState(requireStatus(data)), false);
        return loadServer(name, resolved);
    }

    @Override
    @Audited
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public Server updateServerStatus(String namespace, String serverId, StatusUpdate data) {
        requireEnabled();
        requireWritable();
        McpServerName name = McpServerName.of(namespace, serverId);
        VersionState newState = toVersionState(requireStatus(data));

        // No bulk state change exists, so versions are updated one at a time; a partial failure is safe to
        // retry because setting an already-set state is a no-op. ALL_STATES is required: the default
        // behavior hides DISABLED versions, which are exactly the 'deleted' ones needing restore.
        List<String> versions = storage.getArtifactVersions(name.namespace(), name.serverId(),
                RetrievalBehavior.ALL_STATES);

        // Resolved before mutating, and against every state: setting the server to 'deleted' leaves no
        // ENABLED version, so resolving 'latest' afterwards would 404 an operation that succeeded.
        String reportedVersion = latestVersionAnyState(name);

        for (String version : versions) {
            storage.updateArtifactVersionState(name.namespace(), name.serverId(), version, newState, false);
        }

        return loadServer(name, reportedVersion);
    }

    @Override
    @Audited
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Write)
    public Server publishServer(Server data) {
        requireEnabled();
        requireWritable();

        McpServerName name = McpServerName.parse(data.getName());
        verifyPublishOwnership(name);

        String version = data.getVersion();
        if (version == null || version.isBlank()) {
            throw new BadRequestException("The 'version' field is required when publishing a server");
        }
        if (LATEST_VERSION.equals(version)) {
            throw new BadRequestException("'latest' is not a publishable version: it names whichever"
                    + " version was published most recently");
        }

        // Recomputed on read, so a caller-supplied block is dropped; other extension metadata is kept.
        Meta submitted = data.getMeta();
        if (submitted != null) {
            submitted.getAdditionalProperties().remove(REGISTRY_META_KEY);
            if (submitted.getAdditionalProperties().isEmpty()) {
                data.setMeta(null);
            }
        }
        normalize(data);

        TypedContent typedContent = TypedContent.create(ContentHandle.create(serialize(data)),
                ContentTypes.APPLICATION_JSON);
        validateServerDefinition(typedContent);

        ContentWrapperDto content = ContentWrapperDto.builder()
                .content(typedContent.getContent())
                .contentType(typedContent.getContentType())
                .references(Collections.emptyList())
                .build();

        Map<String, String> labels = new HashMap<>();
        labels.put(SERVER_VERSION_ID_LABEL, UUID.randomUUID().toString());
        EditableVersionMetaDataDto versionMetaData = EditableVersionMetaDataDto.builder()
                .name(name.full())
                .description(data.getDescription())
                .labels(labels)
                .build();

        try {
            if (artifactExists(name)) {
                storage.createArtifactVersion(name.namespace(), name.serverId(), version,
                        ArtifactType.MCP_SERVER, content, versionMetaData, null, false, false,
                        currentUser());
            } else {
                EditableArtifactMetaDataDto artifactMetaData = EditableArtifactMetaDataDto.builder()
                        .name(name.full())
                        .description(data.getDescription())
                        .build();
                storage.createArtifact(name.namespace(), name.serverId(), ArtifactType.MCP_SERVER,
                        artifactMetaData, version, content, versionMetaData, null, false, false,
                        currentUser());
            }
        } catch (VersionAlreadyExistsException | ArtifactAlreadyExistsException e) {
            throw new ClientErrorException(
                    "Version '" + version + "' of MCP server '" + name.full() + "' already exists",
                    Response.Status.CONFLICT);
        }

        return loadServer(name, version);
    }

    /**
     * Owner-only authorization for publishing. The name arrives in the body, not the path, so
     * {@code AuthorizedStyle.GroupAndArtifact} cannot apply and {@code isOwner} would wave this through.
     * Any other body-addressed write endpoint needs the same treatment.
     */
    private void verifyPublishOwnership(McpServerName name) {
        if (!authConfig.isObacEnabled()) {
            return;
        }
        if (authConfig.isRbacEnabled() && rbac.isAdmin()) {
            return;
        }

        try {
            String owner = storage.getArtifactMetaData(name.namespace(), name.serverId()).getOwner();
            if (owner != null && !owner.equals(currentUser())) {
                throw new ForbiddenException(
                        "User is not authorized to perform the requested operation.");
            }
        } catch (ArtifactNotFoundException e) {
            // No such server yet, so there is no owner to conflict with.
        }
    }

    /**
     * Invoked directly rather than through the validity rule, which only runs when an operator has
     * configured one: a well-formed server.json is a precondition of publishing, not an opt-in.
     */
    private void validateServerDefinition(TypedContent content) {
        try {
            factory.getArtifactTypeProvider(ArtifactType.MCP_SERVER).getContentValidator()
                    .validate(ValidityLevel.FULL, content, Collections.emptyMap());
        } catch (RuleViolationException e) {
            String detail = e.getCauses().stream().map(RuleViolation::getDescription).sorted()
                    .collect(Collectors.joining("; "));
            throw new BadRequestException(
                    detail.isEmpty() ? e.getMessage() : e.getMessage() + ": " + detail);
        }
    }

    // === Loading and conversion ===

    /** @throws NotFoundException if no such server or version exists */
    private Server loadServer(McpServerName name, String version) {
        return loadServer(name, version, UNKNOWN_LATEST_VERSION);
    }

    /**
     * @param knownLatestVersion the server's latest version, if the caller already resolved it (e.g. once
     *                           per page, in a loop over the same server's versions) - pass
     *                           {@link #UNKNOWN_LATEST_VERSION} to have it resolved here instead. A caller
     *                           holding a genuinely absent latest version (every version disabled) may pass
     *                           {@code null} directly; resolving it again yields the same {@code null}.
     * @throws NotFoundException if no such server or version exists
     */
    private Server loadServer(McpServerName name, String version, String knownLatestVersion) {
        String resolved = resolveVersion(name, version);
        ArtifactVersionMetaDataDto meta = storage.getArtifactVersionMetaData(name.namespace(),
                name.serverId(), resolved);
        StoredArtifactVersionDto stored = storage.getArtifactVersionContent(name.namespace(),
                name.serverId(), resolved);

        Server server = deserialize(stored.getContent().content(), name, resolved);
        String latest = knownLatestVersion == UNKNOWN_LATEST_VERSION ? latestVersionOrNull(name)
                : knownLatestVersion;
        decorate(server, meta, resolved.equals(latest));
        normalize(server);
        return server;
    }

    /** Null instead of throwing, so one unresolvable server does not fail a whole page. */
    private Server tryLoadServer(McpServerName name, String version) {
        return tryLoadServer(name, version, UNKNOWN_LATEST_VERSION);
    }

    /** @see #loadServer(McpServerName, String, String) */
    private Server tryLoadServer(McpServerName name, String version, String knownLatestVersion) {
        try {
            return loadServer(name, version, knownLatestVersion);
        } catch (ArtifactNotFoundException | VersionNotFoundException | NotFoundException e) {
            return null;
        }
    }

    /** Null and 'latest' both mean the latest branch tip, skipping versions marked deleted. */
    private String resolveVersion(McpServerName name, String version) {
        if (version == null || version.isBlank() || LATEST_VERSION.equals(version)) {
            String latest = latestVersionOrNull(name);
            if (latest == null) {
                throw new NotFoundException("No active version of MCP server '" + name.full() + "' exists");
            }
            return latest;
        }
        return version;
    }

    /** Newest version regardless of state, for when no active version may remain. */
    private String latestVersionAnyState(McpServerName name) {
        return storage.getBranchTip(new GA(name.namespace(), name.serverId()), BranchId.LATEST,
                RetrievalBehavior.ALL_STATES).getRawVersionId();
    }

    private String latestVersionOrNull(McpServerName name) {
        try {
            return storage.getBranchTip(new GA(name.namespace(), name.serverId()), BranchId.LATEST,
                    RetrievalBehavior.SKIP_DISABLED_LATEST).getRawVersionId();
        } catch (ArtifactNotFoundException | VersionNotFoundException e) {
            return null;
        }
    }

    private boolean artifactExists(McpServerName name) {
        try {
            storage.getArtifactMetaData(name.namespace(), name.serverId());
            return true;
        } catch (ArtifactNotFoundException e) {
            return false;
        }
    }

    /** Writes the registry-managed block of '_meta' onto a server loaded from storage. */
    private void decorate(Server server, ArtifactVersionMetaDataDto meta, boolean isLatest) {
        Map<String, Object> registryMeta = new LinkedHashMap<>();
        registryMeta.put(META_ID, serverVersionId(meta));
        registryMeta.put(META_PUBLISHED_AT, Instant.ofEpochMilli(meta.getCreatedOn()).toString());
        registryMeta.put(META_UPDATED_AT, Instant.ofEpochMilli(meta.getModifiedOn()).toString());
        registryMeta.put(META_IS_LATEST, isLatest);
        registryMeta.put(META_STATUS, toStatus(meta.getState()).value());

        Meta serverMeta = server.getMeta();
        if (serverMeta == null) {
            serverMeta = new Meta();
            server.setMeta(serverMeta);
        }
        serverMeta.setAdditionalProperty(REGISTRY_META_KEY, registryMeta);
    }

    /** The persisted UUID, falling back to {@code globalId} for versions published before that label. */
    private String serverVersionId(ArtifactVersionMetaDataDto meta) {
        if (meta.getLabels() != null) {
            String id = meta.getLabels().get(SERVER_VERSION_ID_LABEL);
            if (id != null && !id.isBlank()) {
                return id;
            }
        }
        return Long.toString(meta.getGlobalId());
    }

    /**
     * The generated bean initialises collections to empty lists, which would emit
     * <code>"packages": []</code> for a server that declared none. Null them so only supplied fields ship.
     */
    private void normalize(Server server) {
        if (server.getPackages() != null && server.getPackages().isEmpty()) {
            server.setPackages(null);
        }
        if (server.getRemotes() != null && server.getRemotes().isEmpty()) {
            server.setRemotes(null);
        }
        if (server.getIcons() != null && server.getIcons().isEmpty()) {
            server.setIcons(null);
        }
    }

    private Server deserialize(String content, McpServerName name, String version) {
        try {
            return objectMapper.readValue(content, Server.class);
        } catch (Exception e) {
            // Validated on the way in, so the stored document was corrupted or written past the API.
            // Do not leak the parser message to the client.
            log.error("Stored MCP server definition {} version {} could not be parsed", name.full(),
                    version, e);
            throw new NotFoundException("MCP server '" + name.full() + "' version '" + version
                    + "' is not a readable server definition");
        }
    }

    private String serialize(Server server) {
        try {
            return objectMapper.writeValueAsString(server);
        } catch (Exception e) {
            throw new BadRequestException("The server definition could not be serialized");
        }
    }

    private ServerList buildServerList(List<Server> servers, int nextOffset, String fingerprint) {
        ListMetadata metadata = new ListMetadata();
        metadata.setCount(servers.size());
        if (nextOffset >= 0) {
            metadata.setNextCursor(McpRegistryCursor.encode(nextOffset, fingerprint));
        }

        ServerList list = new ServerList();
        list.setServers(servers);
        list.setMetadata(metadata);
        return list;
    }

    // === Small helpers ===

    private int pageSize(BigInteger limit) {
        if (limit == null) {
            return DEFAULT_PAGE_SIZE;
        }
        int value = limit.intValue();
        if (value < 1) {
            throw new BadRequestException("'limit' must be at least 1");
        }
        return Math.min(value, config.getMaxPageSize());
    }

    private ServerStatus requireStatus(StatusUpdate data) {
        if (data == null || data.getStatus() == null) {
            throw new BadRequestException("The 'status' field is required");
        }
        if (data.getStatus() == ServerStatus.active && data.getStatusMessage() != null
                && !data.getStatusMessage().isBlank()) {
            throw new BadRequestException("'statusMessage' is not allowed when status is 'active'");
        }
        return data.getStatus();
    }

    private VersionState toVersionState(ServerStatus status) {
        switch (status) {
            case deprecated:
                return VersionState.DEPRECATED;
            case deleted:
                return VersionState.DISABLED;
            case active:
            default:
                return VersionState.ENABLED;
        }
    }

    private ServerStatus toStatus(VersionState state) {
        if (state == null) {
            return ServerStatus.active;
        }
        switch (state) {
            case DEPRECATED:
                return ServerStatus.deprecated;
            case DISABLED:
                return ServerStatus.deleted;
            default:
                return ServerStatus.active;
        }
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private String currentUser() {
        return securityIdentity.getPrincipal().getName();
    }
}
