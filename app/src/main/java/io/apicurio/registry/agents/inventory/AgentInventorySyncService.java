package io.apicurio.registry.agents.inventory;

import io.apicurio.registry.agents.OutboundHeaders;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.auth.ResourceAccessGuard;
import io.apicurio.registry.auth.AuthorizedResource;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.extensions.AgentInventorySyncHandler;
import io.apicurio.registry.extensions.McpToolImportHandler;
import io.apicurio.registry.model.BranchId;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.model.GAV;
import io.apicurio.registry.model.GroupId;
import io.apicurio.registry.rest.ConflictException;
import io.apicurio.registry.rest.v3.beans.AgentInventorySource;
import io.apicurio.registry.rest.v3.beans.AgentInventorySyncItemResult;
import io.apicurio.registry.rest.v3.beans.AgentInventorySyncRequest;
import io.apicurio.registry.rest.v3.beans.AgentInventorySyncResult;
import io.apicurio.registry.rest.v3.beans.AgentInventorySyncStatus;
import io.apicurio.registry.rest.v3.beans.CreateArtifact;
import io.apicurio.registry.rest.v3.beans.CreateArtifactResponse;
import io.apicurio.registry.rest.v3.beans.CreateVersion;
import io.apicurio.registry.rest.v3.beans.IfArtifactExists;
import io.apicurio.registry.rest.v3.beans.McpToolImportRequest;
import io.apicurio.registry.rest.v3.beans.RuleViolationCause;
import io.apicurio.registry.rest.v3.beans.VersionContent;
import io.apicurio.registry.rest.v3.beans.WrappedVersionState;
import io.apicurio.registry.rest.v3.impl.GroupsResourceImpl;
import io.apicurio.registry.rules.violation.RuleViolation;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.rules.violation.UnprocessableSchemaException;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.RegistryStorage.RetrievalBehavior;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.SearchedArtifactDto;
import io.apicurio.registry.storage.error.ReadOnlyStorageException;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.types.VersionState;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.WebApplicationException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Keeps a group in sync with an inventory source: one artifact per item the source lists, a new
 * version when an item changes, and {@code DEPRECATED} when the source no longer lists it.
 * <p>
 * Every write goes through the regular v3 create and state-change paths, so authorization, rules,
 * write hooks and auditing apply as for any caller. The sync only changes artifacts it created
 * itself, recognised by their {@link AgentInventoryLabels}; any other artifact with the same ID is
 * reported as {@code NOT_MANAGED} and left alone.
 */
@ApplicationScoped
public class AgentInventorySyncService implements AgentInventorySyncHandler {

    // Headers the HTTP client sets itself, or refuses to let callers set.
    private static final int MISSING_PAGE_SIZE = 200;

    private static final Set<String> RESERVED_HEADERS = Set.of("host", "content-length", "connection",
            "accept", "expect", "upgrade", "transfer-encoding", "te", "trailer", "keep-alive");

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    AgentInventoryConfig config;

    @Inject
    GroupsResourceImpl groups;

    @Inject
    @Any
    Instance<InventorySource> sources;

    @Inject
    Instance<McpToolImportHandler> toolImport;

    @Inject
    ResourceAccessGuard accessGuard;

    @Override
    public AgentInventorySyncResult sync(String groupId, AgentInventorySyncRequest request) {
        if (!config.isEnabled()) {
            throw new ConflictException("Syncing agent inventories is not enabled.");
        }
        if (storage.isReadOnly()) {
            throw new ReadOnlyStorageException("Unsupported write operation. Storage is in read-only mode.");
        }
        if (request == null || request.getSource() == null) {
            throw new BadRequestException("A sync request with a source is required.");
        }
        InventorySource source = source(request.getSource());
        String scope = source.scope(request);
        boolean importTools = Boolean.TRUE.equals(request.getImportTools());
        if (importTools && request.getSource() != AgentInventorySource.KUBERNETES_MCP_SERVERS) {
            throw new BadRequestException("importTools only applies to the KUBERNETES_MCP_SERVERS source.");
        }
        if (importTools && !(toolImport.isResolvable() && toolImport.get().isEnabled())) {
            throw new ConflictException("importTools needs importing tools from MCP servers to be enabled.");
        }
        Map<String, String> headers = OutboundHeaders.validate(request.getHeaders(), RESERVED_HEADERS,
                "the inventory source");
        boolean dryRun = Boolean.TRUE.equals(request.getDryRun());

        List<InventoryItem> items = source.list(scope, headers, config.getItemsMaxSize());
        Set<String> seen = new HashSet<>();
        for (InventoryItem item : items) {
            if (!seen.add(item.artifactId())) {
                throw new InventorySourceException("The inventory source listed the same item twice.");
            }
        }

        // Everything this sync could touch, decided before anything is read out or written: the
        // existing artifacts of listed items, and the managed artifacts the source no longer lists.
        List<Missing> missing = missingCandidates(groupId, source, scope, seen);
        requireWriteAccess(groupId, items, missing);

        List<AgentInventorySyncItemResult> results = new ArrayList<>();
        Set<String> disabled = new HashSet<>();
        for (InventoryItem item : items) {
            if (item.content() == null) {
                // Listed, so not missing, but there is nothing to publish yet.
                continue;
            }
            AgentInventorySyncItemResult result = syncItem(groupId, request.getSource(), scope, item, dryRun,
                    disabled);
            if (importTools && item.mcpUrl() != null && result.getStatus() != AgentInventorySyncStatus.REJECTED
                    && result.getStatus() != AgentInventorySyncStatus.NOT_MANAGED
                    && !disabled.contains(item.artifactId())
                    && !(dryRun && result.getStatus() == AgentInventorySyncStatus.CREATED)) {
                importTools(groupId, item, result, dryRun);
            }
            results.add(result);
        }
        results.addAll(markMissing(groupId, missing, dryRun));

        AgentInventorySyncResult result = new AgentInventorySyncResult();
        result.setGroupId(groupId);
        result.setSource(request.getSource());
        result.setItems(results);
        return result;
    }

    private InventorySource source(AgentInventorySource wanted) {
        for (InventorySource source : sources) {
            if (source.source() == wanted) {
                return source;
            }
        }
        throw new ConflictException("The inventory source " + wanted + " is not available.");
    }

    /**
     * @param disabled collects the artifacts left alone because someone disabled their latest
     *                 version, so that nothing else, such as a tool import, changes them either
     */
    private AgentInventorySyncItemResult syncItem(String groupId, AgentInventorySource source, String scope,
            InventoryItem item, boolean dryRun, Set<String> disabled) {
        AgentInventorySyncItemResult result = new AgentInventorySyncItemResult();
        result.setExternalId(item.externalId());
        result.setArtifactId(item.artifactId());
        result.setArtifactType(item.artifactType());

        boolean exists = storage.isArtifactExists(raw(groupId), item.artifactId());
        List<ArtifactReference> references = List.of();
        if (exists) {
            Map<String, String> labels = storage.getArtifactMetaData(raw(groupId), item.artifactId()).getLabels();
            if (!managedBy(labels, source, scope, item.externalId())) {
                result.setStatus(AgentInventorySyncStatus.NOT_MANAGED);
                return result;
            }
            // Compared with the latest version only, not the history: after A, B, then A again, the
            // artifact gets a new version for A, so the latest version is what the source lists.
            GAV tip = storage.getBranchTip(new GA(raw(groupId), item.artifactId()), BranchId.LATEST,
                    RetrievalBehavior.ALL_STATES);
            String tipVersion = tip.getRawVersionId();
            VersionState state = storage.getArtifactVersionMetaData(raw(groupId), item.artifactId(), tipVersion)
                    .getState();
            if (state == VersionState.DISABLED) {
                // Someone disabled it on purpose: no new version, and it stays disabled.
                disabled.add(item.artifactId());
                result.setStatus(AgentInventorySyncStatus.UNCHANGED);
                result.setVersion(tipVersion);
                return result;
            }
            StoredArtifactVersionDto tipContent = storage.getArtifactVersionContent(raw(groupId), item.artifactId(),
                    tipVersion);
            // Kept on a new version, so refreshing a server's descriptor does not disconnect its
            // tools; a later tool import replaces them with what the server lists.
            references = tipContent.getReferences() == null ? List.of()
                    : tipContent.getReferences().stream().map(AgentInventorySyncService::reference).toList();
            String stored = tipContent.getContent().content();
            if (item.content().equals(stored)) {
                result.setVersion(tipVersion);
                // Listed again after it was reported missing: enable it again.
                boolean reenabled = state == VersionState.DEPRECATED;
                if (reenabled) {
                    setLatestState(groupId, item.artifactId(), VersionState.ENABLED, dryRun);
                }
                result.setStatus(reenabled ? AgentInventorySyncStatus.UPDATED : AgentInventorySyncStatus.UNCHANGED);
                return result;
            }
        }

        Map<String, String> labels = labels(source, scope, item.externalId());
        CreateArtifact create = new CreateArtifact();
        create.setArtifactId(item.artifactId());
        create.setArtifactType(item.artifactType());
        create.setName(item.name());
        create.setDescription(item.description());
        create.setLabels(labels);
        CreateVersion version = new CreateVersion();
        VersionContent content = new VersionContent();
        content.setContent(item.content());
        content.setContentType(ContentTypes.APPLICATION_JSON);
        content.setReferences(references);
        version.setContent(content);
        create.setFirstVersion(version);
        try {
            // Through the regular create path: rules, write hooks, authorization and audit apply.
            CreateArtifactResponse response = groups.createArtifact(groupId,
                    exists ? IfArtifactExists.CREATE_VERSION : IfArtifactExists.FAIL, false, dryRun, create);
            result.setStatus(exists ? AgentInventorySyncStatus.UPDATED : AgentInventorySyncStatus.CREATED);
            result.setVersion(dryRun ? null : response.getVersion().getVersion());
        } catch (RuleViolationException e) {
            result.setStatus(AgentInventorySyncStatus.REJECTED);
            result.setCauses(e.getCauses().stream().map(AgentInventorySyncService::cause).toList());
        } catch (UnprocessableSchemaException e) {
            result.setStatus(AgentInventorySyncStatus.REJECTED);
            result.setCauses(List.of(cause(e.getMessage(), "")));
        }
        return result;
    }

    private void importTools(String groupId, InventoryItem item, AgentInventorySyncItemResult result,
            boolean dryRun) {
        McpToolImportRequest importRequest = new McpToolImportRequest();
        importRequest.setUrl(item.mcpUrl());
        importRequest.setDryRun(dryRun);
        try {
            result.setToolImport(toolImport.get().importTools(groupId, item.artifactId(), importRequest));
        } catch (WebApplicationException e) {
            // The MCP import's messages are fixed sentences that never repeat the URL or the
            // server's response, so they are safe to report.
            result.setToolImportError(e.getMessage());
        }
    }

    /** An artifact an earlier sync of this source and scope created, which the source no longer lists. */
    private record Missing(String artifactId, String artifactType, String externalId) {
    }

    /**
     * Owner-only and per-resource authorization apply to each artifact the sync would read out or
     * change, as they would to the caller's own requests; the endpoint only checked the group. They
     * are checked before anything is written, so a refusal leaves nothing half done, and before the
     * MCP import, which expects write access to the server artifact to be checked already.
     */
    private void requireWriteAccess(String groupId, List<InventoryItem> items, List<Missing> missing) {
        for (InventoryItem item : items) {
            // New artifacts too: per-resource grants can deny creating a name; owner-only
            // authorization allows resources that do not exist yet.
            if (item.content() != null) {
                accessGuard.requireAccess(AuthorizedLevel.Write, AuthorizedResource.artifact(groupId, item.artifactId()));
            }
        }
        for (Missing artifact : missing) {
            accessGuard.requireAccess(AuthorizedLevel.Write, AuthorizedResource.artifact(groupId, artifact.artifactId()));
        }
    }

    /**
     * The artifacts that an earlier sync of the same source and scope created, but that the source
     * no longer lists.
     */
    private List<Missing> missingCandidates(String groupId, InventorySource source, String scope,
            Set<String> listed) {
        Set<SearchFilter> filters = Set.of(SearchFilter.ofGroupId(raw(groupId)),
                SearchFilter.ofLabel(AgentInventoryLabels.SOURCE, source.source().name()),
                SearchFilter.ofLabel(AgentInventoryLabels.SCOPE, scope));
        // Every artifact an earlier sync of this source and scope left, however many: the item
        // limit bounds one listing, not what earlier syncs created.
        List<SearchedArtifactDto> candidates = new ArrayList<>();
        int offset = 0;
        List<SearchedArtifactDto> page;
        do {
            page = storage.searchArtifacts(filters, OrderBy.artifactId, OrderDirection.asc, offset, MISSING_PAGE_SIZE,
                    true).getArtifacts();
            candidates.addAll(page);
            offset += page.size();
        } while (page.size() == MISSING_PAGE_SIZE);

        List<Missing> missing = new ArrayList<>();
        for (SearchedArtifactDto artifact : candidates) {
            if (listed.contains(artifact.getArtifactId())) {
                continue;
            }
            // Search results may carry only some labels, so the artifact's own metadata decides.
            Map<String, String> labels = storage.getArtifactMetaData(raw(groupId), artifact.getArtifactId())
                    .getLabels();
            String externalId = labels == null ? null : labels.get(AgentInventoryLabels.EXTERNAL_ID);
            if (managedBy(labels, source.source(), scope, externalId)
                    && artifact.getArtifactId().equals(source.artifactId(scope, externalId))) {
                missing.add(new Missing(artifact.getArtifactId(), artifact.getArtifactType(), externalId));
            }
            // Otherwise its labels do not map back to this artifact, so the sync does not manage it.
        }
        return missing;
    }

    /** Marks the latest version of each missing artifact {@code DEPRECATED}; nothing is deleted. */
    private List<AgentInventorySyncItemResult> markMissing(String groupId, List<Missing> missing, boolean dryRun) {
        List<AgentInventorySyncItemResult> results = new ArrayList<>();
        for (Missing artifact : missing) {
            AgentInventorySyncItemResult result = new AgentInventorySyncItemResult();
            result.setExternalId(artifact.externalId());
            result.setArtifactId(artifact.artifactId());
            result.setArtifactType(artifact.artifactType());
            GAV tip = storage.getBranchTip(new GA(raw(groupId), artifact.artifactId()), BranchId.LATEST,
                    RetrievalBehavior.ALL_STATES);
            result.setVersion(tip.getRawVersionId());
            VersionState state = storage.getArtifactVersionMetaData(raw(groupId), artifact.artifactId(),
                    tip.getRawVersionId()).getState();
            if (state == VersionState.ENABLED) {
                setLatestState(groupId, artifact.artifactId(), VersionState.DEPRECATED, dryRun);
            }
            result.setStatus(AgentInventorySyncStatus.MISSING);
            results.add(result);
        }
        return results;
    }

    private void setLatestState(String groupId, String artifactId, VersionState state, boolean dryRun) {
        WrappedVersionState wrapped = new WrappedVersionState();
        wrapped.setState(state);
        groups.updateArtifactVersionState(groupId, artifactId, "branch=latest", dryRun, wrapped);
    }

    private static boolean managedBy(Map<String, String> labels, AgentInventorySource source, String scope,
            String externalId) {
        return labels != null && externalId != null && source.name().equals(labels.get(AgentInventoryLabels.SOURCE))
                && Objects.equals(scope, labels.get(AgentInventoryLabels.SCOPE))
                && Objects.equals(externalId, labels.get(AgentInventoryLabels.EXTERNAL_ID));
    }

    private static Map<String, String> labels(AgentInventorySource source, String scope, String externalId) {
        Map<String, String> labels = new HashMap<>();
        labels.put(AgentInventoryLabels.SOURCE, source.name());
        labels.put(AgentInventoryLabels.SCOPE, scope);
        labels.put(AgentInventoryLabels.EXTERNAL_ID, externalId);
        return labels;
    }

    private static ArtifactReference reference(ArtifactReferenceDto stored) {
        ArtifactReference reference = new ArtifactReference();
        reference.setGroupId(stored.getGroupId());
        reference.setArtifactId(stored.getArtifactId());
        reference.setVersion(stored.getVersion());
        reference.setName(stored.getName());
        return reference;
    }

    private static RuleViolationCause cause(RuleViolation violation) {
        return cause(violation.getDescription(), violation.getContext());
    }

    private static RuleViolationCause cause(String description, String context) {
        RuleViolationCause cause = new RuleViolationCause();
        cause.setDescription(description);
        cause.setContext(context);
        return cause;
    }

    /** Storage uses null for the default group; the REST API uses "default". */
    private static String raw(String groupId) {
        return new GroupId(groupId).getRawGroupIdWithNull();
    }
}
