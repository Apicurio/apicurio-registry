package io.apicurio.registry.agents.inventory;

import io.apicurio.registry.agents.OutboundHeaders;
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

        List<AgentInventorySyncItemResult> results = new ArrayList<>();
        for (InventoryItem item : items) {
            AgentInventorySyncItemResult result = syncItem(groupId, request.getSource(), scope, item, dryRun);
            if (importTools && item.mcpUrl() != null && result.getStatus() != AgentInventorySyncStatus.REJECTED
                    && result.getStatus() != AgentInventorySyncStatus.NOT_MANAGED
                    && !(dryRun && result.getStatus() == AgentInventorySyncStatus.CREATED)) {
                importTools(groupId, item, result, dryRun);
            }
            results.add(result);
        }
        results.addAll(missing(groupId, request.getSource(), scope, seen, dryRun));

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

    private AgentInventorySyncItemResult syncItem(String groupId, AgentInventorySource source, String scope,
            InventoryItem item, boolean dryRun) {
        AgentInventorySyncItemResult result = new AgentInventorySyncItemResult();
        result.setExternalId(item.externalId());
        result.setArtifactId(item.artifactId());
        result.setArtifactType(item.artifactType());

        boolean exists = storage.isArtifactExists(raw(groupId), item.artifactId());
        Set<String> previousVersions = Set.of();
        if (exists) {
            Map<String, String> labels = storage.getArtifactMetaData(raw(groupId), item.artifactId()).getLabels();
            if (!managedBy(labels, source, scope, item.externalId())) {
                result.setStatus(AgentInventorySyncStatus.NOT_MANAGED);
                return result;
            }
            GAV tip = storage.getBranchTip(new GA(raw(groupId), item.artifactId()), BranchId.LATEST,
                    RetrievalBehavior.ALL_STATES);
            if (storage.getArtifactVersionMetaData(raw(groupId), item.artifactId(), tip.getRawVersionId())
                    .getState() == VersionState.DISABLED) {
                // Someone disabled it on purpose: no new version, and it stays disabled.
                result.setStatus(AgentInventorySyncStatus.UNCHANGED);
                result.setVersion(tip.getRawVersionId());
                return result;
            }
            previousVersions = new HashSet<>(storage.getArtifactVersions(raw(groupId), item.artifactId()));
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
        version.setContent(content);
        create.setFirstVersion(version);
        try {
            CreateArtifactResponse response = groups.createArtifact(groupId, IfArtifactExists.FIND_OR_CREATE_VERSION,
                    false, dryRun, create);
            String stored = response.getVersion().getVersion();
            if (!exists) {
                result.setStatus(AgentInventorySyncStatus.CREATED);
                result.setVersion(dryRun ? null : stored);
            } else if (previousVersions.contains(stored)) {
                result.setVersion(stored);
                // Listed again after it was reported missing: enable it again.
                result.setStatus(reenable(groupId, item.artifactId(), dryRun) ? AgentInventorySyncStatus.UPDATED
                        : AgentInventorySyncStatus.UNCHANGED);
            } else {
                result.setStatus(AgentInventorySyncStatus.UPDATED);
                result.setVersion(dryRun ? null : stored);
            }
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

    /**
     * The artifacts that an earlier sync of the same source and scope created, but that the source
     * no longer lists. Their latest version is marked {@code DEPRECATED}; nothing is deleted.
     */
    private List<AgentInventorySyncItemResult> missing(String groupId, AgentInventorySource source, String scope,
            Set<String> listed, boolean dryRun) {
        Set<SearchFilter> filters = Set.of(SearchFilter.ofGroupId(raw(groupId)),
                SearchFilter.ofLabel(AgentInventoryLabels.SOURCE, source.name()),
                SearchFilter.ofLabel(AgentInventoryLabels.SCOPE, scope));
        List<AgentInventorySyncItemResult> results = new ArrayList<>();
        for (SearchedArtifactDto artifact : storage.searchArtifacts(filters, OrderBy.artifactId, OrderDirection.asc,
                0, config.getItemsMaxSize() + 1, true).getArtifacts()) {
            if (listed.contains(artifact.getArtifactId())) {
                continue;
            }
            Map<String, String> labels = artifact.getLabels() == null ? Map.of() : artifact.getLabels();
            if (labels.get(AgentInventoryLabels.EXTERNAL_ID) == null) {
                // Not created by a sync: a sync always records the item's external ID.
                continue;
            }
            AgentInventorySyncItemResult result = new AgentInventorySyncItemResult();
            result.setExternalId(labels.get(AgentInventoryLabels.EXTERNAL_ID));
            result.setArtifactId(artifact.getArtifactId());
            result.setArtifactType(artifact.getArtifactType());
            GAV tip = storage.getBranchTip(new GA(raw(groupId), artifact.getArtifactId()), BranchId.LATEST,
                    RetrievalBehavior.ALL_STATES);
            result.setVersion(tip.getRawVersionId());
            VersionState state = storage.getArtifactVersionMetaData(raw(groupId), artifact.getArtifactId(),
                    tip.getRawVersionId()).getState();
            if (state == VersionState.ENABLED) {
                setLatestState(groupId, artifact.getArtifactId(), VersionState.DEPRECATED, dryRun);
            }
            result.setStatus(AgentInventorySyncStatus.MISSING);
            results.add(result);
        }
        return results;
    }

    /** @return whether the latest version was deprecated by an earlier sync, and is now enabled again */
    private boolean reenable(String groupId, String artifactId, boolean dryRun) {
        GAV tip = storage.getBranchTip(new GA(raw(groupId), artifactId), BranchId.LATEST,
                RetrievalBehavior.ALL_STATES);
        VersionState state = storage.getArtifactVersionMetaData(raw(groupId), artifactId, tip.getRawVersionId())
                .getState();
        if (state != VersionState.DEPRECATED) {
            return false;
        }
        setLatestState(groupId, artifactId, VersionState.ENABLED, dryRun);
        return true;
    }

    private void setLatestState(String groupId, String artifactId, VersionState state, boolean dryRun) {
        WrappedVersionState wrapped = new WrappedVersionState();
        wrapped.setState(state);
        groups.updateArtifactVersionState(groupId, artifactId, "branch=latest", dryRun, wrapped);
    }

    private static boolean managedBy(Map<String, String> labels, AgentInventorySource source, String scope,
            String externalId) {
        return labels != null && source.name().equals(labels.get(AgentInventoryLabels.SOURCE))
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
