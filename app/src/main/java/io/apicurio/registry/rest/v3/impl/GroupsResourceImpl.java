package io.apicurio.registry.rest.v3.impl;

import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.logging.audit.Audited;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.model.BranchId;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.model.GroupId;
import io.apicurio.registry.model.VersionExpressionParser;
import io.apicurio.registry.model.VersionId;
import io.apicurio.registry.rest.ConflictException;
import io.apicurio.registry.rest.HeadersHack;
import io.apicurio.registry.rest.MethodMetadata;
import io.apicurio.registry.rest.MissingRequiredParameterException;
import io.apicurio.registry.rest.ParameterValidationUtils;
import io.apicurio.registry.rest.RestConfig;
import io.apicurio.registry.rest.v3.GroupsResource;
import io.apicurio.registry.rest.v3.beans.*;
import io.apicurio.registry.rest.v3.impl.shared.ProtobufExporter;
import io.apicurio.registry.rules.RuleApplicationType;
import io.apicurio.registry.rules.RulesService;
import io.apicurio.registry.storage.RegistryStorage.RetrievalBehavior;
import io.apicurio.registry.storage.dto.*;
import io.apicurio.registry.storage.error.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.storage.error.ContentNotFoundException;
import io.apicurio.registry.storage.error.GroupNotFoundException;
import io.apicurio.registry.storage.error.InvalidArtifactIdException;
import io.apicurio.registry.storage.error.InvalidGroupIdException;
import io.apicurio.registry.storage.error.VersionNotFoundException;
import io.apicurio.registry.storage.impl.sql.RegistryContentUtils;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.types.ReferenceGraphDirection;
import io.apicurio.registry.types.ReferenceType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.types.VersionState;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProvider;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import io.apicurio.registry.util.ArtifactIdGenerator;
import io.apicurio.registry.util.ArtifactTypeUtil;
import io.apicurio.registry.utils.ArtifactIdValidator;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptors;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.NotAllowedException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.core.Response;
import org.apache.commons.lang3.tuple.Pair;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URI;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static io.apicurio.registry.rest.MethodParameterKeys.MPK_ARTIFACT_ID;
import static io.apicurio.registry.rest.MethodParameterKeys.MPK_CANONICAL;
import static io.apicurio.registry.rest.MethodParameterKeys.MPK_EDITABLE_METADATA;
import static io.apicurio.registry.rest.MethodParameterKeys.MPK_GROUP_ID;
import static io.apicurio.registry.rest.MethodParameterKeys.MPK_IF_EXISTS;
import static io.apicurio.registry.rest.MethodParameterKeys.MPK_RULE;
import static io.apicurio.registry.rest.MethodParameterKeys.MPK_RULE_TYPE;
import static io.apicurio.registry.rest.MethodParameterKeys.MPK_VERSION;
import static java.util.stream.Collectors.toList;

/**
 * Implements the {@link GroupsResource} JAX-RS interface.
 */
@ApplicationScoped
@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
@Logged
public class GroupsResourceImpl extends AbstractResourceImpl implements GroupsResource {

    private static final String EMPTY_CONTENT_ERROR_MESSAGE = "Empty content is not allowed.";
    @SuppressWarnings("unused")
    private static final Integer GET_GROUPS_LIMIT = 1000;

    @Inject
    RulesService rulesService;

    @Inject
    ArtifactTypeUtilProviderFactory factory;

    @Inject
    ArtifactIdGenerator idGenerator;

    @Inject
    RestConfig restConfig;

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    io.apicurio.registry.services.PromptRenderingService promptRenderingService;

    @Inject
    ProtobufExporter protobufExporter;

    public enum RegistryHashAlgorithm {
        SHA256, MD5
    }

    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#getArtifactVersionReferences(java.lang.String,
     *      java.lang.String, java.lang.String, io.apicurio.registry.types.ReferenceType)
     */
    @Override
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Read)
    public List<ArtifactReference> getArtifactVersionReferences(String groupId, String artifactId,
            String versionExpression, ReferenceType refType) {

        var gav = VersionExpressionParser.parse(new GA(groupId, artifactId), versionExpression,
                (ga, branchId) -> storage.getBranchTip(ga, branchId, RetrievalBehavior.ALL_STATES));

        if (refType == null || refType == ReferenceType.OUTBOUND) {
            return storage
                    .getArtifactVersionContent(gav.getRawGroupIdWithNull(), gav.getRawArtifactId(),
                            gav.getRawVersionId())
                    .getReferences().stream().map(V3ApiUtil::referenceDtoToReference).collect(toList());
        } else {
            return storage
                    .getInboundArtifactReferences(gav.getRawGroupIdWithNull(), gav.getRawArtifactId(),
                            gav.getRawVersionId())
                    .stream().map(V3ApiUtil::referenceDtoToReference).collect(toList());
        }
    }

    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#getArtifactVersionReferencesGraph(java.lang.String,
     *      java.lang.String, java.lang.String, io.apicurio.registry.types.ReferenceGraphDirection,
     *      java.math.BigInteger)
     */
    @Override
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Read)
    public ReferenceGraph getArtifactVersionReferencesGraph(String groupId, String artifactId,
            String versionExpression, ReferenceGraphDirection direction, BigInteger depth) {

        ParameterValidationUtils.requireParameter("groupId", groupId);
        ParameterValidationUtils.requireParameter("artifactId", artifactId);
        ParameterValidationUtils.requireParameter("versionExpression", versionExpression);

        // Check if the artifact exists first to provide the correct exception type
        if (!storage.isArtifactExists(new GroupId(groupId).getRawGroupIdWithNull(), artifactId)) {
            throw new ArtifactNotFoundException(groupId, artifactId);
        }

        var gav = VersionExpressionParser.parse(new GA(groupId, artifactId), versionExpression,
                (ga, branchId) -> storage.getBranchTip(ga, branchId, RetrievalBehavior.SKIP_DISABLED_LATEST));

        // Default direction to OUTBOUND if not specified
        if (direction == null) {
            direction = ReferenceGraphDirection.OUTBOUND;
        }

        // Default depth to 3 if not specified
        int maxDepth = (depth != null) ? depth.intValue() : 3;
        if (maxDepth == 0) {
            maxDepth = Integer.MAX_VALUE; // 0 means unlimited
        }

        // Get the root artifact metadata
        ArtifactVersionMetaDataDto rootMetadata = storage.getArtifactVersionMetaData(
                gav.getRawGroupIdWithNull(), gav.getRawArtifactId(), gav.getRawVersionId());

        // Build the graph
        return buildReferenceGraph(gav.getRawGroupIdWithNull(), gav.getRawArtifactId(),
                gav.getRawVersionId(), rootMetadata, direction, maxDepth);
    }

    /**
     * Builds a reference graph starting from the given artifact version.
     */
    private ReferenceGraph buildReferenceGraph(String groupId, String artifactId, String version,
            ArtifactVersionMetaDataDto rootMetadata, ReferenceGraphDirection direction, int maxDepth) {

        // Track visited nodes (version-level) to avoid processing the same node twice
        Set<String> visited = new HashSet<>();
        // Track current path of artifacts for cycle detection (artifact-level)
        // A cycle exists only if we encounter the same artifact in the current traversal path
        Set<String> currentPath = new HashSet<>();
        Set<String> cycleNodes = new HashSet<>();

        // Create the root node
        String rootNodeId = createNodeId(groupId, artifactId, version);
        String rootArtifactKey = createArtifactKey(groupId, artifactId);
        ReferenceGraphNode rootNode = ReferenceGraphNode.builder()
                .id(rootNodeId)
                .groupId(groupId != null ? groupId : "default")
                .artifactId(artifactId)
                .version(version)
                .artifactType(rootMetadata.getArtifactType())
                .name(rootMetadata.getName())
                .isRoot(true)
                .isCycleNode(false)
                .build();

        // Collect all nodes and edges
        List<ReferenceGraphNode> nodes = new java.util.ArrayList<>();
        List<ReferenceGraphEdge> edges = new java.util.ArrayList<>();
        nodes.add(rootNode);
        visited.add(rootNodeId);
        currentPath.add(rootArtifactKey);

        // Track the actual max depth reached
        int[] actualMaxDepth = {0};

        // Traverse the graph
        if (direction == ReferenceGraphDirection.OUTBOUND || direction == ReferenceGraphDirection.BOTH) {
            traverseOutboundReferences(groupId, artifactId, version, rootNodeId, nodes, edges,
                    visited, currentPath, cycleNodes, 1, maxDepth, actualMaxDepth);
        }
        if (direction == ReferenceGraphDirection.INBOUND || direction == ReferenceGraphDirection.BOTH) {
            traverseInboundReferences(groupId, artifactId, version, rootNodeId, nodes, edges,
                    visited, currentPath, cycleNodes, 1, maxDepth, actualMaxDepth);
        }

        // Mark cycle nodes
        for (ReferenceGraphNode node : nodes) {
            if (cycleNodes.contains(node.getId())) {
                node.setIsCycleNode(true);
            }
        }

        // Build metadata
        ReferenceGraphMetadata metadata = ReferenceGraphMetadata.builder()
                .totalNodes(nodes.size())
                .totalEdges(edges.size())
                .maxDepth(actualMaxDepth[0])
                .hasCycles(!cycleNodes.isEmpty())
                .build();

        return ReferenceGraph.builder()
                .root(rootNode)
                .nodes(nodes)
                .edges(edges)
                .metadata(metadata)
                .build();
    }

    /**
     * Traverses outbound references (artifacts that this version references).
     */
    private void traverseOutboundReferences(String groupId, String artifactId, String version,
            String sourceNodeId, List<ReferenceGraphNode> nodes, List<ReferenceGraphEdge> edges,
            Set<String> visited, Set<String> currentPath, Set<String> cycleNodes,
            int currentDepth, int maxDepth, int[] actualMaxDepth) {

        if (currentDepth > maxDepth) {
            return;
        }

        try {
            StoredArtifactVersionDto content = storage.getArtifactVersionContent(groupId, artifactId, version);
            List<ArtifactReferenceDto> references = content.getReferences();

            if (references == null || references.isEmpty()) {
                return;
            }

            actualMaxDepth[0] = Math.max(actualMaxDepth[0], currentDepth);

            for (ArtifactReferenceDto ref : references) {
                String refGroupId = ref.getGroupId();
                String refArtifactId = ref.getArtifactId();
                String refVersion = ref.getVersion();
                String targetNodeId = createNodeId(refGroupId, refArtifactId, refVersion);
                String targetArtifactKey = createArtifactKey(refGroupId, refArtifactId);

                // Add edge
                ReferenceGraphEdge edge = ReferenceGraphEdge.builder()
                        .sourceNodeId(sourceNodeId)
                        .targetNodeId(targetNodeId)
                        .name(ref.getName())
                        .build();
                edges.add(edge);

                // Check for cycle: same artifact appears in current path (not just visited globally)
                boolean isArtifactCycle = currentPath.contains(targetArtifactKey);
                if (isArtifactCycle) {
                    cycleNodes.add(targetNodeId);
                    cycleNodes.add(sourceNodeId);
                }

                // Skip processing if this exact version was already visited (avoid duplicate nodes)
                if (visited.contains(targetNodeId)) {
                    continue;
                }

                // Add node
                visited.add(targetNodeId);
                try {
                    ArtifactVersionMetaDataDto refMetadata = storage.getArtifactVersionMetaData(
                            refGroupId, refArtifactId, refVersion);

                    ReferenceGraphNode node = ReferenceGraphNode.builder()
                            .id(targetNodeId)
                            .groupId(refGroupId != null ? refGroupId : "default")
                            .artifactId(refArtifactId)
                            .version(refVersion)
                            .artifactType(refMetadata.getArtifactType())
                            .name(refMetadata.getName())
                            .isRoot(false)
                            .isCycleNode(isArtifactCycle)
                            .build();
                    nodes.add(node);

                    // Recursively traverse (but don't continue if this is a cycle node)
                    if (!isArtifactCycle) {
                        // Add to current path before recursing, remove after (backtracking)
                        currentPath.add(targetArtifactKey);
                        traverseOutboundReferences(refGroupId, refArtifactId, refVersion, targetNodeId,
                                nodes, edges, visited, currentPath, cycleNodes,
                                currentDepth + 1, maxDepth, actualMaxDepth);
                        currentPath.remove(targetArtifactKey);
                    }
                } catch (Exception e) {
                    // Reference might not exist, add a placeholder node
                    ReferenceGraphNode node = ReferenceGraphNode.builder()
                            .id(targetNodeId)
                            .groupId(refGroupId != null ? refGroupId : "default")
                            .artifactId(refArtifactId)
                            .version(refVersion)
                            .isRoot(false)
                            .isCycleNode(isArtifactCycle)
                            .build();
                    nodes.add(node);
                }
            }
        } catch (Exception e) {
            // Artifact content might not be accessible
        }
    }

    /**
     * Traverses inbound references (artifacts that reference this version).
     */
    private void traverseInboundReferences(String groupId, String artifactId, String version,
            String targetNodeId, List<ReferenceGraphNode> nodes, List<ReferenceGraphEdge> edges,
            Set<String> visited, Set<String> currentPath, Set<String> cycleNodes,
            int currentDepth, int maxDepth, int[] actualMaxDepth) {

        if (currentDepth > maxDepth) {
            return;
        }

        try {
            List<ArtifactReferenceDto> inboundRefs = storage.getInboundArtifactReferences(groupId, artifactId, version);

            if (inboundRefs == null || inboundRefs.isEmpty()) {
                return;
            }

            actualMaxDepth[0] = Math.max(actualMaxDepth[0], currentDepth);

            for (ArtifactReferenceDto ref : inboundRefs) {
                String refGroupId = ref.getGroupId();
                String refArtifactId = ref.getArtifactId();
                String refVersion = ref.getVersion();
                String sourceNodeId = createNodeId(refGroupId, refArtifactId, refVersion);
                String sourceArtifactKey = createArtifactKey(refGroupId, refArtifactId);

                // Add edge (inbound: source references target)
                ReferenceGraphEdge edge = ReferenceGraphEdge.builder()
                        .sourceNodeId(sourceNodeId)
                        .targetNodeId(targetNodeId)
                        .name(ref.getName())
                        .build();
                edges.add(edge);

                // Check for cycle: same artifact appears in current path (not just visited globally)
                boolean isArtifactCycle = currentPath.contains(sourceArtifactKey);
                if (isArtifactCycle) {
                    cycleNodes.add(sourceNodeId);
                    cycleNodes.add(targetNodeId);
                }

                // Skip processing if this exact version was already visited (avoid duplicate nodes)
                if (visited.contains(sourceNodeId)) {
                    continue;
                }

                // Add node
                visited.add(sourceNodeId);
                try {
                    ArtifactVersionMetaDataDto refMetadata = storage.getArtifactVersionMetaData(
                            refGroupId, refArtifactId, refVersion);

                    ReferenceGraphNode node = ReferenceGraphNode.builder()
                            .id(sourceNodeId)
                            .groupId(refGroupId != null ? refGroupId : "default")
                            .artifactId(refArtifactId)
                            .version(refVersion)
                            .artifactType(refMetadata.getArtifactType())
                            .name(refMetadata.getName())
                            .isRoot(false)
                            .isCycleNode(isArtifactCycle)
                            .build();
                    nodes.add(node);

                    // Recursively traverse (but don't continue if this is a cycle node)
                    if (!isArtifactCycle) {
                        // Add to current path before recursing, remove after (backtracking)
                        currentPath.add(sourceArtifactKey);
                        traverseInboundReferences(refGroupId, refArtifactId, refVersion, sourceNodeId,
                                nodes, edges, visited, currentPath, cycleNodes,
                                currentDepth + 1, maxDepth, actualMaxDepth);
                        currentPath.remove(sourceArtifactKey);
                    }
                } catch (Exception e) {
                    // Reference might not exist, add a placeholder node
                    ReferenceGraphNode node = ReferenceGraphNode.builder()
                            .id(sourceNodeId)
                            .groupId(refGroupId != null ? refGroupId : "default")
                            .artifactId(refArtifactId)
                            .version(refVersion)
                            .isRoot(false)
                            .isCycleNode(isArtifactCycle)
                            .build();
                    nodes.add(node);
                }
            }
        } catch (Exception e) {
            // Inbound references might not be accessible
        }
    }

    /**
     * Creates a unique node ID from group, artifact, and version.
     */
    private String createNodeId(String groupId, String artifactId, String version) {
        String group = (groupId != null) ? groupId : "default";
        return group + ":" + artifactId + ":" + version;
    }

    /**
     * Creates a unique artifact key from group and artifact (without version).
     * Used for detecting circular references at the artifact level.
     */
    private String createArtifactKey(String groupId, String artifactId) {
        String group = (groupId != null) ? groupId : "default";
        return group + ":" + artifactId;
    }

    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#deleteArtifact(java.lang.String, java.lang.String)
     */
    @Override
    @MethodMetadata(extractParameters = {"0", MPK_GROUP_ID, "1", MPK_ARTIFACT_ID})
    @Audited
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public void deleteArtifact(String groupId, String artifactId) {
        if (!restConfig.isArtifactDeletionEnabled()) {
            throw new NotAllowedException("Artifact deletion operation is not enabled.", HttpMethod.GET,
                    (String[]) null);
        }

        ParameterValidationUtils.requireParameter("groupId", groupId);
        ParameterValidationUtils.requireParameter("artifactId", artifactId);

        storage.deleteArtifact(new GroupId(groupId).getRawGroupIdWithNull(), artifactId);
    }

    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#getArtifactMetaData(java.lang.String,
     *      java.lang.String)
     */
    @Override
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Read)
    public ArtifactMetaData getArtifactMetaData(String groupId, String artifactId) {
        ParameterValidationUtils.requireParameter("groupId", groupId);
        ParameterValidationUtils.requireParameter("artifactId", artifactId);

        ArtifactMetaDataDto dto = storage.getArtifactMetaData(new GroupId(groupId).getRawGroupIdWithNull(),
                artifactId);
        return V3ApiUtil.dtoToArtifactMetaData(dto);
    }

    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#updateArtifactMetaData(java.lang.String,
     *      java.lang.String, io.apicurio.registry.rest.v3.beans.EditableArtifactMetaData)
     */
    @Override
    @MethodMetadata(extractParameters = {"0", MPK_GROUP_ID, "1", MPK_ARTIFACT_ID, "2", MPK_EDITABLE_METADATA})
    @Audited
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public void updateArtifactMetaData(String groupId, String artifactId, EditableArtifactMetaData data) {
        ParameterValidationUtils.requireParameter("groupId", groupId);
        ParameterValidationUtils.requireParameter("artifactId", artifactId);

        if (data.getOwner() != null) {
            if (data.getOwner().trim().isEmpty()) {
                throw new MissingRequiredParameterException("Owner cannot be empty");
            } else {
                // TODO extra security check - if the user is trying to change the owner, fail unless they are
                // an Admin or the current Owner
            }
        }

        EditableArtifactMetaDataDto dto = new EditableArtifactMetaDataDto();
        dto.setName(data.getName());
        dto.setDescription(data.getDescription());
        dto.setOwner(data.getOwner());
        dto.setLabels(data.getLabels());
        storage.updateArtifactMetaData(new GroupId(groupId).getRawGroupIdWithNull(), artifactId, dto);
    }

    @Override
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Read)
    public GroupMetaData getGroupById(String groupId) {
        GroupMetaDataDto group = storage.getGroupMetaData(groupId);
        return V3ApiUtil.groupDtoToGroup(group);
    }

    @Override
    @MethodMetadata(extractParameters = {"0", MPK_GROUP_ID})
    @Audited
    @Authorized(style = AuthorizedStyle.GroupOnly, level = AuthorizedLevel.Write)
    public void deleteGroupById(String groupId) {
        if (!restConfig.isGroupDeletionEnabled()) {
            throw new NotAllowedException("Group deletion operation is not enabled.", HttpMethod.GET,
                    (String[]) null);
        }

        storage.deleteGroup(groupId);
    }

    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#updateGroupById(java.lang.String,
     *      io.apicurio.registry.rest.v3.beans.EditableGroupMetaData)
     */
    @Override
    @MethodMetadata(extractParameters = {"0", MPK_GROUP_ID})
    @Audited
    @Authorized(style = AuthorizedStyle.GroupOnly, level = AuthorizedLevel.Write)
    public void updateGroupById(String groupId, EditableGroupMetaData data) {
        ParameterValidationUtils.requireParameter("groupId", groupId);

        EditableGroupMetaDataDto dto = new EditableGroupMetaDataDto();
        dto.setDescription(data.getDescription());
        dto.setLabels(data.getLabels());
        dto.setOwner(data.getOwner());
        storage.updateGroupMetaData(new GroupId(groupId).getRawGroupIdWithNull(), dto);
    }

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public GroupSearchResults listGroups(BigInteger limit, BigInteger offset, SortOrder order,
            GroupSortBy orderby) {
        if (orderby == null) {
            orderby = GroupSortBy.groupId;
        }
        if (offset == null) {
            offset = BigInteger.valueOf(0);
        }
        if (limit == null) {
            limit = BigInteger.valueOf(20);
        }

        final OrderBy oBy = OrderBy.valueOf(orderby.name());
        final OrderDirection oDir = order == null || order == SortOrder.asc ? OrderDirection.asc
            : OrderDirection.desc;

        Set<SearchFilter> filters = Collections.emptySet();

        GroupSearchResultsDto resultsDto = storage.searchGroups(filters, oBy, oDir, offset.intValue(),
                limit.intValue());
        return V3ApiUtil.dtoToSearchResults(resultsDto);
    }

    @Override
    @Audited
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Write)
    public GroupMetaData createGroup(CreateGroup data) {
        // Validate that the user is not trying to create the reserved "default" group
        if (new GroupId(data.getGroupId()).isDefaultGroup()) {
            throw new BadRequestException("The group name 'default' is reserved and cannot be used.");
        }

        GroupMetaDataDto.GroupMetaDataDtoBuilder group = GroupMetaDataDto.builder().groupId(data.getGroupId())
                .description(data.getDescription()).labels(data.getLabels());

        String user = securityIdentity.getPrincipal().getName();
        group.owner(user).createdOn(new Date().getTime());

        storage.createGroup(group.build());

        return V3ApiUtil.groupDtoToGroup(storage.getGroupMetaData(data.getGroupId()));
    }

    @Override
    @Authorized(style = AuthorizedStyle.GroupOnly, level = AuthorizedLevel.Read)
    public List<RuleType> listGroupRules(String groupId) {
        ParameterValidationUtils.requireParameter("groupId", groupId);

        return storage.getGroupRules(new GroupId(groupId).getRawGroupIdWithNull());
    }

    @Override
    @MethodMetadata(extractParameters = {"0", MPK_GROUP_ID, "1", MPK_RULE})
    @Audited
    @Authorized(style = AuthorizedStyle.GroupOnly, level = AuthorizedLevel.Write)
    public void createGroupRule(String groupId, CreateRule data) {
        ParameterValidationUtils.requireParameter("groupId", groupId);
        ParameterValidationUtils.requireParameter("ruleType", data.getRuleType());
        ParameterValidationUtils.requireParameter("config", data.getConfig());

        if (data.getConfig() == null || data.getConfig().trim().isEmpty()) {
            throw new MissingRequiredParameterException("config");
        }

        if (new GroupId(groupId).isDefaultGroup()) {
            throw new NotAllowedException("Default group is not allowed");
        }

        RuleConfigurationDto config = new RuleConfigurationDto();
        config.setConfiguration(data.getConfig());

        if (!storage.isGroupExists(new GroupId(groupId).getRawGroupIdWithNull())) {
            throw new GroupNotFoundException(groupId);
        }

        storage.createGroupRule(new GroupId(groupId).getRawGroupIdWithNull(), data.getRuleType(), config);
    }

    @Override
    @MethodMetadata(extractParameters = {"0", MPK_GROUP_ID, "1", MPK_RULE_TYPE, "2", MPK_RULE})
    @Audited
    @Authorized(style = AuthorizedStyle.GroupOnly, level = AuthorizedLevel.Write)
    public Rule updateGroupRuleConfig(String groupId, RuleType ruleType, Rule data) {
        ParameterValidationUtils.requireParameter("groupId", groupId);
        ParameterValidationUtils.requireParameter("ruleType", ruleType);
        ParameterValidationUtils.requireParameter("config", data.getConfig());

        if (data.getConfig() == null || data.getConfig().trim().isEmpty()) {
            throw new MissingRequiredParameterException("config");
        }

        RuleConfigurationDto dto = new RuleConfigurationDto(data.getConfig());
        storage.updateGroupRule(new GroupId(groupId).getRawGroupIdWithNull(), ruleType, dto);
        Rule rval = new Rule();
        rval.setRuleType(ruleType);
        rval.setConfig(data.getConfig());
        return rval;
    }

    @Override
    @MethodMetadata(extractParameters = {"0", MPK_GROUP_ID})
    @Audited
    @Authorized(style = AuthorizedStyle.GroupOnly, level = AuthorizedLevel.Write)
    public void deleteGroupRules(String groupId) {
        ParameterValidationUtils.requireParameter("groupId", groupId);

        storage.deleteGroupRules(new GroupId(groupId).getRawGroupIdWithNull());
    }

    @Override
    @Authorized(style = AuthorizedStyle.GroupOnly, level = AuthorizedLevel.Read)
    public Rule getGroupRuleConfig(String groupId, RuleType ruleType) {
        ParameterValidationUtils.requireParameter("groupId", groupId);
        ParameterValidationUtils.requireParameter("ruleType", ruleType);

        RuleConfigurationDto dto = storage.getGroupRule(new GroupId(groupId).getRawGroupIdWithNull(),
                ruleType);
        Rule rval = new Rule();
        rval.setConfig(dto.getConfiguration());
        rval.setRuleType(ruleType);
        return rval;
    }

    @Override
    @MethodMetadata(extractParameters = {"0", MPK_GROUP_ID, "1", MPK_RULE_TYPE})
    @Audited
    @Authorized(style = AuthorizedStyle.GroupOnly, level = AuthorizedLevel.Write)
    public void deleteGroupRule(String groupId, RuleType rule) {
        ParameterValidationUtils.requireParameter("groupId", groupId);
        ParameterValidationUtils.requireParameter("rule", rule);

        storage.deleteGroupRule(new GroupId(groupId).getRawGroupIdWithNull(), rule);
    }

    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#listArtifactRules(java.lang.String, java.lang.String)
     */
    @Override
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Read)
    public List<RuleType> listArtifactRules(String groupId, String artifactId) {
        ParameterValidationUtils.requireParameter("groupId", groupId);
        ParameterValidationUtils.requireParameter("artifactId", artifactId);

        return storage.getArtifactRules(new GroupId(groupId).getRawGroupIdWithNull(), artifactId);
    }

    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#createArtifactRule(String, String, CreateRule)
     */
    @Override
    @MethodMetadata(extractParameters = {"0", MPK_GROUP_ID, "1", MPK_ARTIFACT_ID, "2", MPK_RULE})
    @Audited
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public void createArtifactRule(String groupId, String artifactId, CreateRule data) {
        ParameterValidationUtils.requireParameter("groupId", groupId);
        ParameterValidationUtils.requireParameter("artifactId", artifactId);
        ParameterValidationUtils.requireParameter("ruleType", data.getRuleType());
        ParameterValidationUtils.requireParameter("config", data.getConfig());

        if (data.getConfig() == null || data.getConfig().trim().isEmpty()) {
            throw new MissingRequiredParameterException("config");
        }

        RuleConfigurationDto config = new RuleConfigurationDto();
        config.setConfiguration(data.getConfig());

        if (!storage.isArtifactExists(new GroupId(groupId).getRawGroupIdWithNull(), artifactId)) {
            throw new ArtifactNotFoundException(groupId, artifactId);
        }

        storage.createArtifactRule(new GroupId(groupId).getRawGroupIdWithNull(), artifactId,
                data.getRuleType(), config);
    }

    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#deleteArtifactRules(java.lang.String,
     *      java.lang.String)
     */
    @Override
    @MethodMetadata(extractParameters = {"0", MPK_GROUP_ID, "1", MPK_ARTIFACT_ID})
    @Audited
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public void deleteArtifactRules(String groupId, String artifactId) {
        ParameterValidationUtils.requireParameter("groupId", groupId);
        ParameterValidationUtils.requireParameter("artifactId", artifactId);

        storage.deleteArtifactRules(new GroupId(groupId).getRawGroupIdWithNull(), artifactId);
    }

    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#getArtifactRuleConfig(java.lang.String,
     *      java.lang.String, io.apicurio.registry.types.RuleType)
     */
    @Override
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Read)
    public Rule getArtifactRuleConfig(String groupId, String artifactId, RuleType ruleType) {
        ParameterValidationUtils.requireParameter("groupId", groupId);
        ParameterValidationUtils.requireParameter("artifactId", artifactId);
        ParameterValidationUtils.requireParameter("ruleType", ruleType);

        RuleConfigurationDto dto = storage.getArtifactRule(new GroupId(groupId).getRawGroupIdWithNull(),
                artifactId, ruleType);
        Rule rval = new Rule();
        rval.setConfig(dto.getConfiguration());
        rval.setRuleType(ruleType);
        return rval;
    }

    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#updateArtifactRuleConfig(String, String, RuleType,
     *      Rule)
     */
    @Override
    @MethodMetadata(extractParameters = {"0", MPK_GROUP_ID, "1", MPK_ARTIFACT_ID, "2", MPK_RULE_TYPE, "3",
            MPK_RULE})
    @Audited
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public Rule updateArtifactRuleConfig(String groupId, String artifactId, RuleType ruleType, Rule data) {
        ParameterValidationUtils.requireParameter("groupId", groupId);
        ParameterValidationUtils.requireParameter("artifactId", artifactId);
        ParameterValidationUtils.requireParameter("ruleType", ruleType);
        ParameterValidationUtils.requireParameter("config", data.getConfig());

        if (data.getConfig() == null || data.getConfig().trim().isEmpty()) {
            throw new MissingRequiredParameterException("config");
        }

        RuleConfigurationDto dto = new RuleConfigurationDto(data.getConfig());
        storage.updateArtifactRule(new GroupId(groupId).getRawGroupIdWithNull(), artifactId, ruleType, dto);
        Rule rval = new Rule();
        rval.setRuleType(ruleType);
        rval.setConfig(data.getConfig());
        return rval;
    }

    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#deleteArtifactRule(java.lang.String, java.lang.String,
     *      io.apicurio.registry.types.RuleType)
     */
    @Override
    @MethodMetadata(extractParameters = {"0", MPK_GROUP_ID, "1", MPK_ARTIFACT_ID, "2", MPK_RULE_TYPE})
    @Audited
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public void deleteArtifactRule(String groupId, String artifactId, RuleType rule) {
        ParameterValidationUtils.requireParameter("groupId", groupId);
        ParameterValidationUtils.requireParameter("artifactId", artifactId);
        ParameterValidationUtils.requireParameter("rule", rule);

        storage.deleteArtifactRule(new GroupId(groupId).getRawGroupIdWithNull(), artifactId, rule);
    }

    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#getArtifactVersionContent(java.lang.String,
     *      java.lang.String, java.lang.String, io.apicurio.registry.rest.v3.beans.HandleReferencesType)
     */
    @Override
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Read)
    public Response getArtifactVersionContent(String groupId, String artifactId, String versionExpression,
            HandleReferencesType references) {
        ParameterValidationUtils.requireParameter("groupId", groupId);
        ParameterValidationUtils.requireParameter("artifactId", artifactId);
        ParameterValidationUtils.requireParameter("versionExpression", versionExpression);

        var gav = VersionExpressionParser.parse(new GA(groupId, artifactId), versionExpression,
                (ga, branchId) -> storage.getBranchTip(ga, branchId, RetrievalBehavior.SKIP_DISABLED_LATEST));

        if (references == null) {
            // Check if admin has configured a default reference handling behavior
            java.util.Optional<String> configuredDefault = restConfig.getDefaultReferenceHandling();
            if (configuredDefault.isPresent() && !configuredDefault.get().trim().isEmpty()) {
                references = HandleReferencesType.fromValue(configuredDefault.get());
            } else {
                // No configuration - use existing default (no behavior change)
                references = HandleReferencesType.PRESERVE;
            }
        }

        ArtifactVersionMetaDataDto metaData = storage.getArtifactVersionMetaData(gav.getRawGroupIdWithNull(),
                gav.getRawArtifactId(), gav.getRawVersionId());
        if (VersionState.DISABLED.equals(metaData.getState())) {
            throw new VersionNotFoundException(groupId, artifactId, versionExpression);
        }
        StoredArtifactVersionDto artifact = storage.getArtifactVersionContent(gav.getRawGroupIdWithNull(),
                gav.getRawArtifactId(), gav.getRawVersionId());

        // Throw 404 if the version actually has "no content" based on the content-type
        if (ContentTypes.isEmptyContentType(artifact.getContentType())) {
            throw new ContentNotFoundException(artifact.getContentId());
        }

        TypedContent contentToReturn = TypedContent.create(artifact.getContent(), artifact.getContentType());
        contentToReturn = handleContentReferences(references, metaData.getArtifactType(), contentToReturn,
                artifact.getReferences());

        Response.ResponseBuilder builder = Response.ok(contentToReturn.getContent(),
                artifact.getContentType());
        checkIfDeprecated(metaData::getState, groupId, artifactId, versionExpression, builder);
        return builder.build();
    }

    @Override
    @MethodMetadata(extractParameters = {"0", MPK_GROUP_ID, "1", MPK_ARTIFACT_ID, "2", MPK_VERSION})
    @Audited
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public void updateArtifactVersionContent(String groupId, String artifactId, String versionExpression,
            VersionContent data) {
        ParameterValidationUtils.requireParameter("groupId", groupId);
        ParameterValidationUtils.requireParameter("artifactId", artifactId);
        ParameterValidationUtils.requireParameter("versionExpression", versionExpression);

        if (!restConfig.isArtifactVersionMutabilityEnabled()) {
            throw new NotAllowedException("Artifact version content update operation is not enabled.",
                    HttpMethod.GET, (String[]) null);
        }

        // Resolve the GAV info (only look for DRAFT versions)
        var gav = VersionExpressionParser.parse(new GA(groupId, artifactId), versionExpression,
                (ga, branchId) -> storage.getBranchTip(ga, branchId, RetrievalBehavior.ALL_STATES));

        // Ensure the artifact version is in DRAFT status
        ArtifactVersionMetaDataDto vmd = storage.getArtifactVersionMetaData(gav.getRawGroupIdWithNull(),
                gav.getRawArtifactId(), gav.getRawVersionId());
        if (vmd.getState() != VersionState.DRAFT) {
            throw new ConflictException(
                    "Requested artifact version is not in DRAFT state.  Update disallowed.");
        }

        // Check if the artifact type allows empty content
        String artifactType = vmd.getArtifactType();
        ArtifactTypeUtilProvider artifactTypeProvider = factory.getArtifactTypeProvider(artifactType);
        boolean isEmptyContent = artifactTypeProvider.getContentTypes().isEmpty();
        ContentHandle content = ContentHandle.create(data.getContent());

        if (isEmptyContent) {
            // TODO fail the request if content is sent to an artifact that requires empty content??
            data.setContent("");
            data.setContentType(ContentTypes.APPLICATION_EMPTY);
        } else {
            if (content.bytes().length == 0) {
                throw new BadRequestException(EMPTY_CONTENT_ERROR_MESSAGE);
            }
        }

        // Transform the given references into dtos
        final List<ArtifactReferenceDto> referencesAsDtos = toReferenceDtos(data.getReferences());

        // Create the content wrapper dto
        ContentWrapperDto contentDto = ContentWrapperDto.builder().contentType(data.getContentType())
                .content(content).references(referencesAsDtos).build();

        // Now ask the storage to update the content
        storage.updateArtifactVersionContent(gav.getRawGroupIdWithNull(), gav.getRawArtifactId(),
                gav.getRawVersionId(), vmd.getArtifactType(), contentDto);
    }

    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#deleteArtifactVersion(java.lang.String,
     *      java.lang.String, java.lang.String)
     */
    @Override
    @MethodMetadata(extractParameters = {"0", MPK_GROUP_ID, "1", MPK_ARTIFACT_ID, "2", MPK_VERSION})
    @Audited
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public void deleteArtifactVersion(String groupId, String artifactId, String version) {
        if (!restConfig.isArtifactVersionDeletionEnabled()) {
            throw new NotAllowedException("Artifact version deletion operation is not enabled.",
                    HttpMethod.GET, (String[]) null);
        }

        ParameterValidationUtils.requireParameter("groupId", groupId);
        ParameterValidationUtils.requireParameter("artifactId", artifactId);
        ParameterValidationUtils.requireParameter("version", version);

        var gav = VersionExpressionParser.parse(new GA(groupId, artifactId), version,
                (ga, branchId) -> storage.getBranchTip(ga, branchId, RetrievalBehavior.ALL_STATES));

        storage.deleteArtifactVersion(gav.getRawGroupIdWithNull(), gav.getRawArtifactId(),
                gav.getRawVersionId());
    }

    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#getArtifactVersionMetaData(java.lang.String,
     *      java.lang.String, java.lang.String)
     */
    @Override
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Read)
    public VersionMetaData getArtifactVersionMetaData(String groupId, String artifactId, String version) {
        ParameterValidationUtils.requireParameter("groupId", groupId);
        ParameterValidationUtils.requireParameter("artifactId", artifactId);
        ParameterValidationUtils.requireParameter("version", version);

        var gav = VersionExpressionParser.parse(new GA(groupId, artifactId), version,
                (ga, branchId) -> storage.getBranchTip(ga, branchId, RetrievalBehavior.SKIP_DISABLED_LATEST));

        ArtifactVersionMetaDataDto dto = storage.getArtifactVersionMetaData(gav.getRawGroupIdWithNull(),
                gav.getRawArtifactId(), gav.getRawVersionId());
        return V3ApiUtil.dtoToVersionMetaData(dto);
    }

    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#updateArtifactVersionMetaData(java.lang.String,
     *      java.lang.String, java.lang.String, io.apicurio.registry.rest.v3.beans.EditableVersionMetaData)
     */
    @Override
    @MethodMetadata(extractParameters = {"0", MPK_GROUP_ID, "1", MPK_ARTIFACT_ID, "2", MPK_VERSION, "3",
            MPK_EDITABLE_METADATA})
    @Audited
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public void updateArtifactVersionMetaData(String groupId, String artifactId, String versionExpression,
            EditableVersionMetaData data) {
        ParameterValidationUtils.requireParameter("groupId", groupId);
        ParameterValidationUtils.requireParameter("artifactId", artifactId);
        ParameterValidationUtils.requireParameter("versionExpression", versionExpression);

        var gav = VersionExpressionParser.parse(new GA(groupId, artifactId), versionExpression,
                (ga, branchId) -> storage.getBranchTip(ga, branchId, RetrievalBehavior.SKIP_DISABLED_LATEST));

        EditableVersionMetaDataDto dto = new EditableVersionMetaDataDto();
        dto.setName(data.getName());
        dto.setDescription(data.getDescription());
        dto.setLabels(data.getLabels());
        storage.updateArtifactVersionMetaData(gav.getRawGroupIdWithNull(), gav.getRawArtifactId(),
                gav.getRawVersionId(), dto);
    }

    @Override
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Read)
    public WrappedVersionState getArtifactVersionState(String groupId, String artifactId,
            String versionExpression) {
        ParameterValidationUtils.requireParameter("groupId", groupId);
        ParameterValidationUtils.requireParameter("artifactId", artifactId);
        ParameterValidationUtils.requireParameter("version", versionExpression);

        var gav = VersionExpressionParser.parse(new GA(groupId, artifactId), versionExpression,
                (ga, branchId) -> storage.getBranchTip(ga, branchId, RetrievalBehavior.ALL_STATES));

        VersionState state = storage.getArtifactVersionState(gav.getRawGroupIdWithNull(),
                gav.getRawArtifactId(), gav.getRawVersionId());
        return WrappedVersionState.builder().state(state).build();
    }

    @Override
    @MethodMetadata(extractParameters = {"0", MPK_GROUP_ID, "1", MPK_ARTIFACT_ID, "2", MPK_VERSION, "3", "dryRun"})
    @Audited
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write, dryRunParam = 3)
    public void updateArtifactVersionState(String groupId, String artifactId, String versionExpression,
            Boolean dryRun, WrappedVersionState data) {
        ParameterValidationUtils.requireParameter("groupId", groupId);
        ParameterValidationUtils.requireParameter("artifactId", artifactId);
        ParameterValidationUtils.requireParameter("versionExpression", versionExpression);
        ParameterValidationUtils.requireParameter("body.state", data.getState());

        if (data.getState() == VersionState.DRAFT) {
            throw new BadRequestException("Illegal state transition: cannot transition to DRAFT state.");
        }

        var gav = VersionExpressionParser.parse(new GA(groupId, artifactId), versionExpression,
                (ga, branchId) -> storage.getBranchTip(ga, branchId, RetrievalBehavior.ALL_STATES));

        // Get current state.
        VersionState currentState = storage.getArtifactVersionState(gav.getRawGroupIdWithNull(),
                gav.getRawArtifactId(), gav.getRawVersionId());

        // If the current state is the same as the new state, do nothing.
        if (currentState == data.getState()) {
            return;
        }

        // If the current state is DRAFT, apply rules.
        if (currentState == VersionState.DRAFT) {
            VersionMetaData vmd = getArtifactVersionMetaData(gav.getRawGroupIdWithDefaultString(),
                    gav.getRawArtifactId(), gav.getRawVersionId());
            StoredArtifactVersionDto artifact = storage.getArtifactVersionContent(gav.getRawGroupIdWithNull(),
                    gav.getRawArtifactId(), gav.getRawVersionId());
            final Map<String, TypedContent> resolvedReferences = RegistryContentUtils
                    .recursivelyResolveReferences(artifact.getReferences(), storage::getContentByReference);
            final List<ArtifactReference> references = V3ApiUtil
                    .referenceDtosToReferences(artifact.getReferences());

            TypedContent typedContent = TypedContent.create(artifact.getContent(), artifact.getContentType());
            rulesService.applyRules(gav.getRawGroupIdWithNull(), gav.getRawArtifactId(),
                    vmd.getArtifactType(), typedContent, RuleApplicationType.UPDATE, references,
                    resolvedReferences);
        }

        // Now update the state.
        storage.updateArtifactVersionState(gav.getRawGroupIdWithNull(), gav.getRawArtifactId(),
                gav.getRawVersionId(), data.getState(), dryRun != null && dryRun);
    }

    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#addArtifactVersionComment(java.lang.String,
     *      java.lang.String, java.lang.String, io.apicurio.registry.rest.v3.beans.NewComment)
     */
    @Override
    @MethodMetadata(extractParameters = {"0", MPK_GROUP_ID, "1", MPK_ARTIFACT_ID, "2", MPK_VERSION})
    @Audited
    @Authorized(style = AuthorizedStyle.GroupOnly, level = AuthorizedLevel.Write)
    public Comment addArtifactVersionComment(String groupId, String artifactId, String versionExpression,
            NewComment data) {
        ParameterValidationUtils.requireParameter("groupId", groupId);
        ParameterValidationUtils.requireParameter("artifactId", artifactId);
        ParameterValidationUtils.requireParameter("versionExpression", versionExpression);

        var gav = VersionExpressionParser.parse(new GA(groupId, artifactId), versionExpression,
                (ga, branchId) -> storage.getBranchTip(ga, branchId, RetrievalBehavior.ALL_STATES));

        CommentDto newComment = storage.createArtifactVersionComment(gav.getRawGroupIdWithNull(),
                gav.getRawArtifactId(), gav.getRawVersionId(), data.getValue());
        return V3ApiUtil.commentDtoToComment(newComment);
    }

    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#deleteArtifactVersionComment(java.lang.String,
     *      java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    @MethodMetadata(extractParameters = {"0", MPK_GROUP_ID, "1", MPK_ARTIFACT_ID, "2", MPK_VERSION, "3",
            "comment_id"})
    @Audited
    @Authorized(style = AuthorizedStyle.GroupOnly, level = AuthorizedLevel.Write)
    public void deleteArtifactVersionComment(String groupId, String artifactId, String versionExpression,
            String commentId) {
        ParameterValidationUtils.requireParameter("groupId", groupId);
        ParameterValidationUtils.requireParameter("artifactId", artifactId);
        ParameterValidationUtils.requireParameter("versionExpression", versionExpression);
        ParameterValidationUtils.requireParameter("commentId", commentId);

        var gav = VersionExpressionParser.parse(new GA(groupId, artifactId), versionExpression,
                (ga, branchId) -> storage.getBranchTip(ga, branchId, RetrievalBehavior.ALL_STATES));

        storage.deleteArtifactVersionComment(gav.getRawGroupIdWithNull(), gav.getRawArtifactId(),
                gav.getRawVersionId(), commentId);
    }

    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#getArtifactVersionComments(java.lang.String,
     *      java.lang.String, java.lang.String)
     */
    @Override
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Read)
    public List<Comment> getArtifactVersionComments(String groupId, String artifactId, String version) {
        ParameterValidationUtils.requireParameter("groupId", groupId);
        ParameterValidationUtils.requireParameter("artifactId", artifactId);
        ParameterValidationUtils.requireParameter("version", version);

        var gav = VersionExpressionParser.parse(new GA(groupId, artifactId), version,
                (ga, branchId) -> storage.getBranchTip(ga, branchId, RetrievalBehavior.ALL_STATES));

        return storage.getArtifactVersionComments(gav.getRawGroupIdWithNull(), gav.getRawArtifactId(),
                gav.getRawVersionId()).stream().map(V3ApiUtil::commentDtoToComment).collect(toList());
    }

    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#updateArtifactVersionComment(java.lang.String,
     *      java.lang.String, java.lang.String, java.lang.String,
     *      io.apicurio.registry.rest.v3.beans.NewComment)
     */
    @Override
    @MethodMetadata(extractParameters = {"0", MPK_GROUP_ID, "1", MPK_ARTIFACT_ID, "2", MPK_VERSION, "3",
            "comment_id"})
    @Audited
    @Authorized(style = AuthorizedStyle.GroupOnly, level = AuthorizedLevel.Write)
    public void updateArtifactVersionComment(String groupId, String artifactId, String versionExpression,
            String commentId, NewComment data) {
        ParameterValidationUtils.requireParameter("groupId", groupId);
        ParameterValidationUtils.requireParameter("artifactId", artifactId);
        ParameterValidationUtils.requireParameter("versionExpression", versionExpression);
        ParameterValidationUtils.requireParameter("commentId", commentId);
        ParameterValidationUtils.requireParameter("value", data.getValue());

        var gav = VersionExpressionParser.parse(new GA(groupId, artifactId), versionExpression,
                (ga, branchId) -> storage.getBranchTip(ga, branchId, RetrievalBehavior.ALL_STATES));

        storage.updateArtifactVersionComment(gav.getRawGroupIdWithNull(), gav.getRawArtifactId(),
                gav.getRawVersionId(), commentId, data.getValue());
    }

    @Override
    @Authorized(style = AuthorizedStyle.GroupOnly, level = AuthorizedLevel.Read)
    public ArtifactSearchResults listArtifactsInGroup(String groupId, BigInteger limit, BigInteger offset,
            SortOrder order, ArtifactSortBy orderby) {
        ParameterValidationUtils.requireParameter("groupId", groupId);

        if (orderby == null) {
            orderby = ArtifactSortBy.name;
        }
        if (offset == null) {
            offset = BigInteger.valueOf(0);
        }
        if (limit == null) {
            limit = BigInteger.valueOf(20);
        }

        final OrderBy oBy = OrderBy.valueOf(orderby.name());
        final OrderDirection oDir = order == null || order == SortOrder.asc ? OrderDirection.asc
            : OrderDirection.desc;

        Set<SearchFilter> filters = new HashSet<>();
        filters.add(SearchFilter.ofGroupId(new GroupId(groupId).getRawGroupIdWithNull()));

        ArtifactSearchResultsDto resultsDto = storage.searchArtifacts(filters, oBy, oDir, offset.intValue(),
                limit.intValue());
        return V3ApiUtil.dtoToSearchResults(resultsDto);
    }

    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#deleteArtifactsInGroup(java.lang.String)
     */
    @Override
    @MethodMetadata(extractParameters = {"0", MPK_GROUP_ID})
    @Audited
    @Authorized(style = AuthorizedStyle.GroupOnly, level = AuthorizedLevel.Write)
    public void deleteArtifactsInGroup(String groupId) {
        if (!restConfig.isArtifactDeletionEnabled()) {
            throw new NotAllowedException("Artifact deletion operation is not enabled.", HttpMethod.GET,
                    (String[]) null);
        }

        ParameterValidationUtils.requireParameter("groupId", groupId);

        storage.deleteArtifacts(new GroupId(groupId).getRawGroupIdWithNull());
    }

    @Override
    @MethodMetadata(extractParameters = {"0", MPK_GROUP_ID, "1", MPK_IF_EXISTS, "2", MPK_CANONICAL, "3", "dryRun"})
    @Audited
    @Authorized(style = AuthorizedStyle.GroupOnly, level = AuthorizedLevel.Write, dryRunParam = 3)
    public CreateArtifactResponse createArtifact(String groupId, IfArtifactExists ifExists, Boolean canonical,
            Boolean dryRun, CreateArtifact data) {
        ParameterValidationUtils.requireParameter("groupId", groupId);
        if (data.getFirstVersion() != null) {
            boolean contentRequired = true;
            if (data.getArtifactType() != null) {
                Set<String> contentTypes = factory.getArtifactTypeProvider(data.getArtifactType()).getContentTypes();
                contentRequired = !contentTypes.isEmpty();
            }
            if (contentRequired) {
                ParameterValidationUtils.requireParameter("body.firstVersion.content", data.getFirstVersion().getContent());
                ParameterValidationUtils.requireParameter("body.firstVersion.content.content",
                        data.getFirstVersion().getContent().getContent());
                ParameterValidationUtils.requireParameter("body.firstVersion.content.contentType",
                        data.getFirstVersion().getContent().getContentType());
            } else {
                if (data.getFirstVersion().getContent() == null) {
                    data.getFirstVersion().setContent(new VersionContent());
                }
                if (data.getFirstVersion().getContent().getContent() == null) {
                    data.getFirstVersion().getContent().setContent("");
                }
                if (data.getFirstVersion().getContent().getContentType() == null) {
                    data.getFirstVersion().getContent().setContentType(ContentTypes.APPLICATION_EMPTY);
                }
                ParameterValidationUtils.requireParameterValue("body.firstVersion.content.contentType", ContentTypes.APPLICATION_EMPTY,
                        data.getFirstVersion().getContent().getContentType());
            }
            if (data.getFirstVersion().getBranches() == null) {
                data.getFirstVersion().setBranches(Collections.emptyList());
            }
        } else {
            ParameterValidationUtils.requireParameter("body.artifactType", data.getArtifactType());
        }

        if (!ArtifactIdValidator.isGroupIdAllowed(groupId)) {
            throw new InvalidGroupIdException(ArtifactIdValidator.GROUP_ID_ERROR_MESSAGE);
        }

        // TODO Mitigation for MITM attacks, verify that the artifact is the expected one
        // if (xRegistryContentHash != null) {
        // String calculatedSha = null;
        // try {
        // RegistryHashAlgorithm algorithm = (xRegistryHashAlgorithm == null) ? RegistryHashAlgorithm.SHA256 :
        // RegistryHashAlgorithm.valueOf(xRegistryHashAlgorithm);
        // switch (algorithm) {
        // case MD5:
        // calculatedSha = Hashing.md5().hashString(content.content(), StandardCharsets.UTF_8).toString();
        // break;
        // case SHA256:
        // calculatedSha = Hashing.sha256().hashString(content.content(), StandardCharsets.UTF_8).toString();
        // break;
        // }
        // } catch (Exception e) {
        // throw new BadRequestException("Requested hash algorithm not supported");
        // }
        //
        // if (!calculatedSha.equals(xRegistryContentHash.trim())) {
        // throw new BadRequestException("Provided Artifact Hash doesn't match with the content");
        // }
        // }

        final boolean fcanonical = canonical == null ? Boolean.FALSE : canonical;
        String artifactId = data.getArtifactId();
        final String contentType = getContentType(data);
        final ContentHandle content = getContent(data);
        final List<ArtifactReference> references = getReferences(data);

        // If a first version is included, the content must not be empty (unless content is not
        // required for the artifact type).
        if (data.getFirstVersion() != null) {
            final boolean isEmptyContent = ContentTypes.isEmptyContentType(contentType);
            if (!isEmptyContent) {
                if (content == null || content.bytes().length == 0) {
                    throw new BadRequestException(EMPTY_CONTENT_ERROR_MESSAGE);
                }
            }
        }

        try {
            if (artifactId == null || artifactId.trim().isEmpty()) {
                artifactId = idGenerator.generate();
            } else if (!ArtifactIdValidator.isArtifactIdAllowed(artifactId)) {
                throw new InvalidArtifactIdException(ArtifactIdValidator.ARTIFACT_ID_ERROR_MESSAGE);
            }
            TypedContent typedContent = TypedContent.create(content, contentType);

            String artifactType = ArtifactTypeUtil.determineArtifactType(typedContent, data.getArtifactType(), factory);

            final String owner = securityIdentity.getPrincipal().getName();

            // Create the artifact (with optional first version)
            EditableArtifactMetaDataDto artifactMetaData = EditableArtifactMetaDataDto.builder()
                    .description(data.getDescription()).name(data.getName()).labels(data.getLabels()).build();
            String firstVersion = null;
            ContentWrapperDto firstVersionContent = null;
            EditableVersionMetaDataDto firstVersionMetaData = null;
            List<String> firstVersionBranches = null;
            boolean firstVersionIsDraft = false;
            if (data.getFirstVersion() != null) {
                // Convert references to DTOs
                final List<ArtifactReferenceDto> referencesAsDtos = toReferenceDtos(references);

                firstVersion = data.getFirstVersion().getVersion();
                firstVersionContent = ContentWrapperDto.builder().content(content).contentType(contentType)
                        .references(referencesAsDtos).build();
                firstVersionMetaData = EditableVersionMetaDataDto.builder()
                        .description(data.getFirstVersion().getDescription())
                        .name(data.getFirstVersion().getName()).labels(data.getFirstVersion().getLabels())
                        .build();
                firstVersionBranches = data.getFirstVersion().getBranches();
                firstVersionIsDraft = data.getFirstVersion().getIsDraft() != null
                        && data.getFirstVersion().getIsDraft();

                // Try to resolve the references
                final Map<String, TypedContent> resolvedReferences = RegistryContentUtils
                        .recursivelyResolveReferences(referencesAsDtos, storage::getContentByReference);

                // Apply any configured rules unless it is a DRAFT version (unless draft production mode is enabled)
                if (!firstVersionIsDraft || restConfig.isDraftProductionModeEnabled()) {
                    rulesService.applyRules(new GroupId(groupId).getRawGroupIdWithNull(), artifactId,
                            artifactType, typedContent, RuleApplicationType.CREATE, references,
                            resolvedReferences);
                }
            }

            Pair<ArtifactMetaDataDto, ArtifactVersionMetaDataDto> storageResult = storage.createArtifact(
                    new GroupId(groupId).getRawGroupIdWithNull(), artifactId, artifactType, artifactMetaData,
                    firstVersion, firstVersionContent, firstVersionMetaData, firstVersionBranches,
                    firstVersionIsDraft, dryRun != null && dryRun, owner);

            // Now return both the artifact metadata and (if available) the version metadata
            CreateArtifactResponse rval = CreateArtifactResponse.builder()
                    .artifact(V3ApiUtil.dtoToArtifactMetaData(storageResult.getLeft())).build();
            if (storageResult.getRight() != null) {
                rval.setVersion(V3ApiUtil.dtoToVersionMetaData(storageResult.getRight()));
            }

            return rval;
        } catch (ArtifactAlreadyExistsException ex) {
            return handleIfExists(groupId, artifactId, ifExists, data.getFirstVersion(), fcanonical, dryRun);
        }
    }

    @Override
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Read)
    public VersionSearchResults listArtifactVersions(String groupId, String artifactId, BigInteger offset,
            BigInteger limit, SortOrder order, VersionSortBy orderby) {
        ParameterValidationUtils.requireParameter("groupId", groupId);
        ParameterValidationUtils.requireParameter("artifactId", artifactId);
        if (orderby == null) {
            orderby = VersionSortBy.createdOn;
        }
        if (offset == null) {
            offset = BigInteger.valueOf(0);
        }
        if (limit == null) {
            limit = BigInteger.valueOf(20);
        }

        GroupId gid = new GroupId(groupId);

        // This will result in a 404 if the artifact does not exist.
        storage.getArtifactMetaData(gid.getRawGroupIdWithNull(), artifactId);

        final OrderBy oBy = OrderBy.valueOf(orderby.name());
        final OrderDirection oDir = order == null || order == SortOrder.asc ? OrderDirection.asc
            : OrderDirection.desc;

        Set<SearchFilter> filters = Set.of(
                SearchFilter.ofGroupId(new GroupId(groupId).getRawGroupIdWithNull()),
                SearchFilter.ofArtifactId(artifactId));
        VersionSearchResultsDto resultsDto = storage.searchVersions(filters, oBy, oDir, offset.intValue(),
                limit.intValue());
        return V3ApiUtil.dtoToSearchResults(resultsDto);
    }

    @Override
    @MethodMetadata(extractParameters = {"0", MPK_GROUP_ID, "1", MPK_ARTIFACT_ID, "2", "dryRun"})
    @Audited
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write, dryRunParam = 2)
    public VersionMetaData createArtifactVersion(String groupId, String artifactId, Boolean dryRun,
            CreateVersion data) {
        ParameterValidationUtils.requireParameter("groupId", groupId);
        ParameterValidationUtils.requireParameter("artifactId", artifactId);

        String artifactType = lookupArtifactType(groupId, artifactId);
        ArtifactTypeUtilProvider artifactTypeProvider = factory.getArtifactTypeProvider(artifactType);
        boolean isEmptyContent = artifactTypeProvider.getContentTypes().isEmpty();
        if (!isEmptyContent) {
            ParameterValidationUtils.requireParameter("body.content", data.getContent());
            ParameterValidationUtils.requireParameter("body.content.content", data.getContent().getContent());
            ParameterValidationUtils.requireParameter("body.content.contentType", data.getContent().getContentType());
        } else {
            if (data.getContent() == null) {
                data.setContent(new VersionContent());
            }
            data.getContent().setContent("");
            data.getContent().setContentType(ContentTypes.APPLICATION_EMPTY);
        }

        ContentHandle content = ContentHandle.create(data.getContent().getContent());
        if (!isEmptyContent && content.bytes().length == 0) {
            throw new BadRequestException(EMPTY_CONTENT_ERROR_MESSAGE);
        }
        String ct = data.getContent().getContentType();
        boolean isDraft = data.getIsDraft() != null && data.getIsDraft();

        // Transform the given references into dtos
        final List<ArtifactReferenceDto> referencesAsDtos = toReferenceDtos(
                data.getContent().getReferences());

        // Apply rules unless the version is DRAFT (unless draft production mode is enabled)
        if (!isDraft || restConfig.isDraftProductionModeEnabled()) {
            // Try to resolve the new artifact references and the nested ones (if any)
            final Map<String, TypedContent> resolvedReferences = RegistryContentUtils
                    .recursivelyResolveReferences(referencesAsDtos, storage::getContentByReference);

            TypedContent typedContent = TypedContent.create(content, ct);
            rulesService.applyRules(new GroupId(groupId).getRawGroupIdWithNull(), artifactId, artifactType,
                    typedContent, RuleApplicationType.UPDATE, data.getContent().getReferences(),
                    resolvedReferences);
        }

        final String owner = securityIdentity.getPrincipal().getName();

        EditableVersionMetaDataDto metaDataDto = EditableVersionMetaDataDto.builder()
                .description(data.getDescription()).name(data.getName()).labels(data.getLabels()).build();
        ContentWrapperDto contentDto = ContentWrapperDto.builder().contentType(ct).content(content)
                .references(referencesAsDtos).build();

        ArtifactVersionMetaDataDto vmd = storage.createArtifactVersion(
                new GroupId(groupId).getRawGroupIdWithNull(), artifactId, data.getVersion(), artifactType,
                contentDto, metaDataDto, data.getBranches(), isDraft, dryRun != null && dryRun, owner);

        return V3ApiUtil.dtoToVersionMetaData(vmd);
    }

    @Override
    @MethodMetadata(extractParameters = {"0", MPK_GROUP_ID, "1", MPK_ARTIFACT_ID})
    @Audited
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public BranchMetaData createBranch(String groupId, String artifactId, CreateBranch data) {
        ParameterValidationUtils.requireParameter("groupId", groupId);
        ParameterValidationUtils.requireParameter("artifactId", artifactId);
        ParameterValidationUtils.requireParameter("branchId", data.getBranchId());

        GA ga = new GA(groupId, artifactId);
        BranchId bid = new BranchId(data.getBranchId());
        BranchMetaDataDto branchDto = storage.createBranch(ga, bid, data.getDescription(),
                data.getVersions());
        return V3ApiUtil.dtoToBranchMetaData(branchDto);
    }

    @Override
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Read)
    public BranchSearchResults listBranches(String groupId, String artifactId, BigInteger offset,
            BigInteger limit) {
        ParameterValidationUtils.requireParameter("groupId", groupId);
        ParameterValidationUtils.requireParameter("artifactId", artifactId);

        if (offset == null) {
            offset = BigInteger.valueOf(0);
        }
        if (limit == null) {
            limit = BigInteger.valueOf(20);
        }

        BranchSearchResultsDto dto = storage.getBranches(new GA(groupId, artifactId), offset.intValue(),
                limit.intValue());
        return V3ApiUtil.dtoToSearchResults(dto);
    }

    @Override
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Read)
    public BranchMetaData getBranchMetaData(String groupId, String artifactId, String branchId) {
        ParameterValidationUtils.requireParameter("groupId", groupId);
        ParameterValidationUtils.requireParameter("artifactId", artifactId);

        BranchMetaDataDto branch = storage.getBranchMetaData(new GA(groupId, artifactId),
                new BranchId(branchId));
        return V3ApiUtil.dtoToBranchMetaData(branch);
    }

    @Override
    @MethodMetadata(extractParameters = {"0", MPK_GROUP_ID, "1", MPK_ARTIFACT_ID})
    @Audited
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public void updateBranchMetaData(String groupId, String artifactId, String branchId,
            EditableBranchMetaData data) {
        ParameterValidationUtils.requireParameter("groupId", groupId);
        ParameterValidationUtils.requireParameter("artifactId", artifactId);
        ParameterValidationUtils.requireParameter("branchId", branchId);

        EditableBranchMetaDataDto dto = EditableBranchMetaDataDto.builder().description(data.getDescription())
                .build();
        storage.updateBranchMetaData(new GA(groupId, artifactId), new BranchId(branchId), dto);
    }

    @Override
    @MethodMetadata(extractParameters = {"0", MPK_GROUP_ID, "1", MPK_ARTIFACT_ID})
    @Audited
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public void deleteBranch(String groupId, String artifactId, String branchId) {
        ParameterValidationUtils.requireParameter("groupId", groupId);
        ParameterValidationUtils.requireParameter("artifactId", artifactId);
        ParameterValidationUtils.requireParameter("branchId", branchId);

        storage.deleteBranch(new GA(groupId, artifactId), new BranchId(branchId));
    }

    @Override
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Read)
    public VersionSearchResults listBranchVersions(String groupId, String artifactId, String branchId,
            BigInteger offset, BigInteger limit) {
        ParameterValidationUtils.requireParameter("groupId", groupId);
        ParameterValidationUtils.requireParameter("artifactId", artifactId);
        ParameterValidationUtils.requireParameter("branchId", branchId);

        if (offset == null) {
            offset = BigInteger.valueOf(0);
        }
        if (limit == null) {
            limit = BigInteger.valueOf(20);
        }

        GA ga = new GA(groupId, artifactId);
        BranchId bid = new BranchId(branchId);

        // Throw 404 if the artifact or branch does not exist.
        storage.getBranchMetaData(ga, bid);

        VersionSearchResultsDto results = storage.getBranchVersions(ga, bid, offset.intValue(),
                limit.intValue());
        return V3ApiUtil.dtoToSearchResults(results);
    }

    @Override
    @MethodMetadata(extractParameters = {"0", MPK_GROUP_ID, "1", MPK_ARTIFACT_ID})
    @Audited
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public void replaceBranchVersions(String groupId, String artifactId, String branchId,
            ReplaceBranchVersions data) {
        ParameterValidationUtils.requireParameter("groupId", groupId);
        ParameterValidationUtils.requireParameter("artifactId", artifactId);
        ParameterValidationUtils.requireParameter("branchId", branchId);
        ParameterValidationUtils.requireParameter("versions", data.getVersions());

        GA ga = new GA(groupId, artifactId);
        BranchId bid = new BranchId(branchId);

        // Throw 404 if the artifact or branch does not exist.
        storage.getBranchMetaData(ga, bid);

        storage.replaceBranchVersions(ga, bid, data.getVersions().stream().map(VersionId::new).toList());
    }

    @Override
    @MethodMetadata(extractParameters = {"0", MPK_GROUP_ID, "1", MPK_ARTIFACT_ID})
    @Audited
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public void addVersionToBranch(String groupId, String artifactId, String branchId,
            AddVersionToBranch data) {
        ParameterValidationUtils.requireParameter("groupId", groupId);
        ParameterValidationUtils.requireParameter("artifactId", artifactId);
        ParameterValidationUtils.requireParameter("branchId", branchId);

        GA ga = new GA(groupId, artifactId);
        BranchId bid = new BranchId(branchId);

        // Throw 404 if the artifact or branch does not exist.
        storage.getBranchMetaData(ga, bid);

        storage.appendVersionToBranch(ga, bid, new VersionId(data.getVersion()));
    }

    // ========== Not endpoints: ==========

    /**
     * Check to see if the artifact version is deprecated.
     *
     * @param stateSupplier
     * @param groupId
     * @param artifactId
     * @param version
     * @param builder
     */
    private void checkIfDeprecated(Supplier<VersionState> stateSupplier, String groupId, String artifactId,
            String version, Response.ResponseBuilder builder) {
        HeadersHack.checkIfDeprecated(stateSupplier, groupId, artifactId, version, builder);
    }

    /**
     * Looks up the artifact type for the given artifact.
     *
     * @param groupId
     * @param artifactId
     */
    private String lookupArtifactType(String groupId, String artifactId) {
        return storage.getArtifactMetaData(new GroupId(groupId).getRawGroupIdWithNull(), artifactId)
                .getArtifactType();
    }

    private String getContentType(CreateArtifact data) {
        if (data.getFirstVersion() != null && data.getFirstVersion().getContent() != null) {
            return data.getFirstVersion().getContent().getContentType();
        }
        return null;
    }

    private ContentHandle getContent(CreateArtifact data) {
        if (data.getFirstVersion() != null && data.getFirstVersion().getContent() != null) {
            return ContentHandle.create(data.getFirstVersion().getContent().getContent());
        }
        return null;
    }

    private List<ArtifactReference> getReferences(CreateArtifact data) {
        if (data.getFirstVersion() != null && data.getFirstVersion().getContent() != null) {
            return data.getFirstVersion().getContent().getReferences();
        }
        return null;
    }

    private CreateArtifactResponse handleIfExists(String groupId, String artifactId,
            IfArtifactExists ifExists, CreateVersion theVersion, boolean canonical, Boolean dryRun) {
        if (ifExists == null || theVersion == null) {
            ifExists = IfArtifactExists.FAIL;
        }

        switch (ifExists) {
            case CREATE_VERSION:
                return updateArtifactInternal(groupId, artifactId, theVersion, dryRun);
            case FIND_OR_CREATE_VERSION:
                return handleIfExistsReturnOrUpdate(groupId, artifactId, theVersion, canonical, dryRun);
            default:
                throw new ArtifactAlreadyExistsException(groupId, artifactId);
        }
    }

    private CreateArtifactResponse handleIfExistsReturnOrUpdate(String groupId, String artifactId,
            CreateVersion theVersion, boolean canonical, Boolean dryRun) {
        try {
            // Find the version
            TypedContent content = TypedContent.create(
                    ContentHandle.create(theVersion.getContent().getContent()),
                    theVersion.getContent().getContentType());
            List<ArtifactReferenceDto> referenceDtos = toReferenceDtos(
                    theVersion.getContent().getReferences());
            ArtifactVersionMetaDataDto vmdDto = this.storage.getArtifactVersionMetaDataByContent(
                    new GroupId(groupId).getRawGroupIdWithNull(), artifactId, canonical, content,
                    referenceDtos);
            VersionMetaData vmd = V3ApiUtil.dtoToVersionMetaData(vmdDto);

            // Need to also return the artifact metadata
            ArtifactMetaDataDto amdDto = this.storage.getArtifactMetaData(groupId, artifactId);
            ArtifactMetaData amd = V3ApiUtil.dtoToArtifactMetaData(amdDto);

            return CreateArtifactResponse.builder().artifact(amd).version(vmd).build();
        } catch (ArtifactNotFoundException nfe) {
            // This is OK - we'll update the artifact if there is no matching content already there.
        }
        return updateArtifactInternal(groupId, artifactId, theVersion, dryRun);
    }

    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    protected CreateArtifactResponse updateArtifactInternal(String groupId, String artifactId,
            CreateVersion theVersion, Boolean dryRun) {
        String version = theVersion.getVersion();
        String name = theVersion.getName();
        String description = theVersion.getDescription();
        List<String> branches = theVersion.getBranches();
        Map<String, String> labels = theVersion.getLabels();
        List<ArtifactReference> references = theVersion.getContent().getReferences();
        String contentType = theVersion.getContent().getContentType();
        ContentHandle content = ContentHandle.create(theVersion.getContent().getContent());
        boolean isDraftVersion = theVersion.getIsDraft() != null && theVersion.getIsDraft();

        String artifactType = lookupArtifactType(groupId, artifactId);

        final String owner = securityIdentity.getPrincipal().getName();

        // Transform the given references into dtos and set the contentId, this will also detect if any of the
        // passed references does not exist.
        final List<ArtifactReferenceDto> referencesAsDtos = toReferenceDtos(references);

        // Apply rules only if not a draft version (unless draft production mode is enabled)
        if (!isDraftVersion || restConfig.isDraftProductionModeEnabled()) {
            final Map<String, TypedContent> resolvedReferences = RegistryContentUtils
                    .recursivelyResolveReferences(referencesAsDtos, storage::getContentByReference);
            final TypedContent typedContent = TypedContent.create(content, contentType);
            rulesService.applyRules(new GroupId(groupId).getRawGroupIdWithNull(), artifactId, artifactType,
                    typedContent, RuleApplicationType.UPDATE, references, resolvedReferences);
        }

        EditableVersionMetaDataDto metaData = EditableVersionMetaDataDto.builder().name(name)
                .description(description).labels(labels).build();
        ContentWrapperDto contentDto = ContentWrapperDto.builder().contentType(contentType).content(content)
                .references(referencesAsDtos).build();
        ArtifactVersionMetaDataDto vmdDto = storage.createArtifactVersion(groupId, artifactId, version,
                artifactType, contentDto, metaData, branches, isDraftVersion, dryRun != null && dryRun, owner);
        VersionMetaData vmd = V3ApiUtil.dtoToVersionMetaData(vmdDto);

        // Need to also return the artifact metadata
        ArtifactMetaDataDto amdDto = this.storage.getArtifactMetaData(groupId, artifactId);
        ArtifactMetaData amd = V3ApiUtil.dtoToArtifactMetaData(amdDto);

        return CreateArtifactResponse.builder().artifact(amd).version(vmd).build();
    }

    private List<ArtifactReferenceDto> toReferenceDtos(List<ArtifactReference> references) {
        if (references == null) {
            references = Collections.emptyList();
        }
        return references.stream()
                .peek(r -> r.setGroupId(new GroupId(r.getGroupId()).getRawGroupIdWithNull()))
                .map(V3ApiUtil::referenceToDto).collect(toList());
    }

    /**
     * Return an InputStream for the resource to be downloaded
     *
     * @param url
     */
    private InputStream fetchContentFromURL(Client client, URI url) {
        try {
            // 1. Registry issues HTTP HEAD request to the target URL.
            List<Object> contentLengthHeaders = client.target(url).request().head().getHeaders()
                    .get("Content-Length");

            if (contentLengthHeaders == null || contentLengthHeaders.size() < 1) {
                throw new BadRequestException(
                        "Requested resource URL does not provide 'Content-Length' in the headers");
            }

            // 2. According to HTTP specification, target server must return Content-Length header.
            int contentLength = Integer.parseInt(contentLengthHeaders.get(0).toString());

            // 3. Registry analyzes value of Content-Length to check if file with declared size could be
            // processed securely.
            if (contentLength > restConfig.getDownloadMaxSize()) {
                throw new BadRequestException("Requested resource is bigger than "
                        + restConfig.getDownloadMaxSize() + " and cannot be downloaded.");
            }

            if (contentLength <= 0) {
                throw new BadRequestException("Requested resource URL is providing 'Content-Length' <= 0.");
            }

            // 4. Finally, registry issues HTTP GET to the target URL and fetches only amount of bytes
            // specified by HTTP HEAD from step 1.
            return new BufferedInputStream(client.target(url).request().get().readEntity(InputStream.class),
                    contentLength);
        } catch (BadRequestException bre) {
            throw bre;
        } catch (Exception e) {
            throw new BadRequestException("Errors downloading the artifact content.", e);
        }
    }

    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#renderPromptTemplate(java.lang.String,
     *      java.lang.String, java.lang.String, io.apicurio.registry.rest.v3.beans.RenderPromptRequest)
     */
    @Override
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Read)
    public io.apicurio.registry.rest.v3.beans.RenderPromptResponse renderPromptTemplate(
            String groupId, String artifactId, String versionExpression,
            io.apicurio.registry.rest.v3.beans.RenderPromptRequest data) {

        ParameterValidationUtils.requireParameter("groupId", groupId);
        ParameterValidationUtils.requireParameter("artifactId", artifactId);
        ParameterValidationUtils.requireParameter("versionExpression", versionExpression);
        ParameterValidationUtils.requireParameter("data", data);
        ParameterValidationUtils.requireParameter("variables", data.getVariables());

        var gav = VersionExpressionParser.parse(new GA(groupId, artifactId), versionExpression,
                (ga, branchId) -> storage.getBranchTip(ga, branchId, RetrievalBehavior.ALL_STATES));

        // Verify the artifact exists and is of type PROMPT_TEMPLATE
        ArtifactVersionMetaDataDto versionMetaData = storage.getArtifactVersionMetaData(
                gav.getRawGroupIdWithNull(), gav.getRawArtifactId(), gav.getRawVersionId());

        String artifactType = versionMetaData.getArtifactType();
        if (!"PROMPT_TEMPLATE".equals(artifactType)) {
            throw new BadRequestException(
                    "Artifact type must be PROMPT_TEMPLATE, but was: " + artifactType);
        }

        // Get the content
        StoredArtifactVersionDto storedArtifact = storage.getArtifactVersionContent(
                gav.getRawGroupIdWithNull(), gav.getRawArtifactId(), gav.getRawVersionId());

        // Convert variables map - Variables bean uses additionalProperties for dynamic keys
        Map<String, Object> variables = data.getVariables().getAdditionalProperties();

        // Render the template
        return promptRenderingService.render(
                storedArtifact.getContent(),
                variables,
                gav.getRawGroupIdWithNull() != null ? gav.getRawGroupIdWithNull() : "default",
                gav.getRawArtifactId(),
                gav.getRawVersionId());
    }

    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#exportArtifactVersion(java.lang.String,
     *      java.lang.String, java.lang.String)
     */
    @Override
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Read)
    public Response exportArtifactVersion(String groupId, String artifactId, String versionExpression) {
        ParameterValidationUtils.requireParameter("groupId", groupId);
        ParameterValidationUtils.requireParameter("artifactId", artifactId);
        ParameterValidationUtils.requireParameter("versionExpression", versionExpression);

        var gav = VersionExpressionParser.parse(new GA(groupId, artifactId), versionExpression,
                (ga, branchId) -> storage.getBranchTip(ga, branchId, RetrievalBehavior.SKIP_DISABLED_LATEST));

        // Verify the artifact exists and is of type PROTOBUF
        ArtifactVersionMetaDataDto versionMetaData = storage.getArtifactVersionMetaData(
                gav.getRawGroupIdWithNull(), gav.getRawArtifactId(), gav.getRawVersionId());

        String artifactType = versionMetaData.getArtifactType();
        if (!"PROTOBUF".equals(artifactType)) {
            throw new BadRequestException(
                    "Export as ZIP is only supported for PROTOBUF artifacts, but artifact type was: " + artifactType);
        }

        return protobufExporter.exportVersionAsZip(
                gav.getRawGroupIdWithNull(), gav.getRawArtifactId(), gav.getRawVersionId());
    }

}
