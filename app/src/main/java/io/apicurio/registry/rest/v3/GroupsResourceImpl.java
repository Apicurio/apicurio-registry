package io.apicurio.registry.rest.v3;

import io.apicurio.common.apps.logging.Logged;
import io.apicurio.common.apps.logging.audit.Audited;
import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.model.BranchId;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.model.GroupId;
import io.apicurio.registry.model.VersionExpressionParser;
import io.apicurio.registry.model.VersionId;
import io.apicurio.registry.rest.HeadersHack;
import io.apicurio.registry.rest.MissingRequiredParameterException;
import io.apicurio.registry.rest.RestConfig;
import io.apicurio.registry.rest.v3.beans.AddVersionToBranch;
import io.apicurio.registry.rest.v3.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rest.v3.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.v3.beans.ArtifactSortBy;
import io.apicurio.registry.rest.v3.beans.BranchMetaData;
import io.apicurio.registry.rest.v3.beans.BranchSearchResults;
import io.apicurio.registry.rest.v3.beans.Comment;
import io.apicurio.registry.rest.v3.beans.CreateArtifact;
import io.apicurio.registry.rest.v3.beans.CreateArtifactResponse;
import io.apicurio.registry.rest.v3.beans.CreateBranch;
import io.apicurio.registry.rest.v3.beans.CreateGroup;
import io.apicurio.registry.rest.v3.beans.CreateRule;
import io.apicurio.registry.rest.v3.beans.CreateVersion;
import io.apicurio.registry.rest.v3.beans.EditableArtifactMetaData;
import io.apicurio.registry.rest.v3.beans.EditableBranchMetaData;
import io.apicurio.registry.rest.v3.beans.EditableGroupMetaData;
import io.apicurio.registry.rest.v3.beans.EditableVersionMetaData;
import io.apicurio.registry.rest.v3.beans.GroupMetaData;
import io.apicurio.registry.rest.v3.beans.GroupSearchResults;
import io.apicurio.registry.rest.v3.beans.GroupSortBy;
import io.apicurio.registry.rest.v3.beans.HandleReferencesType;
import io.apicurio.registry.rest.v3.beans.IfArtifactExists;
import io.apicurio.registry.rest.v3.beans.NewComment;
import io.apicurio.registry.rest.v3.beans.ReplaceBranchVersions;
import io.apicurio.registry.rest.v3.beans.Rule;
import io.apicurio.registry.rest.v3.beans.SortOrder;
import io.apicurio.registry.rest.v3.beans.VersionMetaData;
import io.apicurio.registry.rest.v3.beans.VersionSearchResults;
import io.apicurio.registry.rest.v3.beans.VersionSortBy;
import io.apicurio.registry.rest.v3.shared.CommonResourceOperations;
import io.apicurio.registry.rules.RuleApplicationType;
import io.apicurio.registry.rules.RulesService;
import io.apicurio.registry.storage.RegistryStorage.RetrievalBehavior;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.ArtifactSearchResultsDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.BranchMetaDataDto;
import io.apicurio.registry.storage.dto.BranchSearchResultsDto;
import io.apicurio.registry.storage.dto.CommentDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.EditableBranchMetaDataDto;
import io.apicurio.registry.storage.dto.EditableGroupMetaDataDto;
import io.apicurio.registry.storage.dto.EditableVersionMetaDataDto;
import io.apicurio.registry.storage.dto.GroupMetaDataDto;
import io.apicurio.registry.storage.dto.GroupSearchResultsDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.apicurio.registry.storage.dto.VersionSearchResultsDto;
import io.apicurio.registry.storage.error.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.storage.error.GroupNotFoundException;
import io.apicurio.registry.storage.error.InvalidArtifactIdException;
import io.apicurio.registry.storage.error.InvalidGroupIdException;
import io.apicurio.registry.storage.error.VersionNotFoundException;
import io.apicurio.registry.types.ReferenceType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.types.VersionState;
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

import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_ARTIFACT_ID;
import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_CANONICAL;
import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_EDITABLE_METADATA;
import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_GROUP_ID;
import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_IF_EXISTS;
import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_RULE;
import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_RULE_TYPE;
import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_VERSION;
import static java.util.stream.Collectors.toList;

/**
 * Implements the {@link GroupsResource} JAX-RS interface.
 */
@ApplicationScoped
@Interceptors({ ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class })
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
    CommonResourceOperations common;

    public enum RegistryHashAlgorithm {
        SHA256, MD5
    }

    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#getArtifactVersionReferences(java.lang.String,
     *      java.lang.String, java.lang.String, io.apicurio.registry.types.ReferenceType)
     */
    @Override
    public List<ArtifactReference> getArtifactVersionReferences(String groupId, String artifactId,
            String versionExpression, ReferenceType refType) {

        var gav = VersionExpressionParser.parse(new GA(groupId, artifactId), versionExpression,
                (ga, branchId) -> storage.getBranchTip(ga, branchId, RetrievalBehavior.DEFAULT));

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
     * @see io.apicurio.registry.rest.v3.GroupsResource#deleteArtifact(java.lang.String, java.lang.String)
     */
    @Override
    @Audited(extractParameters = { "0", KEY_GROUP_ID, "1", KEY_ARTIFACT_ID })
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public void deleteArtifact(String groupId, String artifactId) {
        if (!restConfig.isArtifactDeletionEnabled()) {
            throw new NotAllowedException("Artifact deletion operation is not enabled.", HttpMethod.GET,
                    (String[]) null);
        }

        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);

        storage.deleteArtifact(new GroupId(groupId).getRawGroupIdWithNull(), artifactId);
    }

    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#getArtifactMetaData(java.lang.String,
     *      java.lang.String)
     */
    @Override
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Read)
    public ArtifactMetaData getArtifactMetaData(String groupId, String artifactId) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);

        ArtifactMetaDataDto dto = storage.getArtifactMetaData(new GroupId(groupId).getRawGroupIdWithNull(),
                artifactId);
        return V3ApiUtil.dtoToArtifactMetaData(dto);
    }

    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#updateArtifactMetaData(java.lang.String,
     *      java.lang.String, io.apicurio.registry.rest.v3.beans.EditableArtifactMetaData)
     */
    @Override
    @Audited(extractParameters = { "0", KEY_GROUP_ID, "1", KEY_ARTIFACT_ID, "2", KEY_EDITABLE_METADATA })
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public void updateArtifactMetaData(String groupId, String artifactId, EditableArtifactMetaData data) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);

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
    @Authorized(style = AuthorizedStyle.GroupOnly, level = AuthorizedLevel.Write)
    public void updateGroupById(String groupId, EditableGroupMetaData data) {
        requireParameter("groupId", groupId);

        EditableGroupMetaDataDto dto = new EditableGroupMetaDataDto();
        dto.setDescription(data.getDescription());
        dto.setLabels(data.getLabels());
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
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Write)
    public GroupMetaData createGroup(CreateGroup data) {
        GroupMetaDataDto.GroupMetaDataDtoBuilder group = GroupMetaDataDto.builder().groupId(data.getGroupId())
                .description(data.getDescription()).labels(data.getLabels());

        String user = securityIdentity.getPrincipal().getName();
        group.owner(user).createdOn(new Date().getTime());

        storage.createGroup(group.build());

        return V3ApiUtil.groupDtoToGroup(storage.getGroupMetaData(data.getGroupId()));
    }

    @Override
    public List<RuleType> listGroupRules(String groupId) {
        requireParameter("groupId", groupId);

        return storage.getGroupRules(new GroupId(groupId).getRawGroupIdWithNull());
    }

    @Override
    public void createGroupRule(String groupId, CreateRule data) {
        requireParameter("groupId", groupId);
        requireParameter("ruleType", data.getRuleType());
        requireParameter("config", data.getConfig());

        if (data.getConfig() == null || data.getConfig().isEmpty()) {
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
    public Rule updateGroupRuleConfig(String groupId, RuleType ruleType, Rule data) {
        requireParameter("groupId", groupId);
        requireParameter("ruleType", ruleType);
        requireParameter("config", data.getConfig());

        RuleConfigurationDto dto = new RuleConfigurationDto(data.getConfig());
        storage.updateGroupRule(new GroupId(groupId).getRawGroupIdWithNull(), ruleType, dto);
        Rule rval = new Rule();
        rval.setRuleType(ruleType);
        rval.setConfig(data.getConfig());
        return rval;
    }

    @Override
    public void deleteGroupRules(String groupId) {
        requireParameter("groupId", groupId);

        storage.deleteGroupRules(new GroupId(groupId).getRawGroupIdWithNull());
    }

    @Override
    public Rule getGroupRuleConfig(String groupId, RuleType ruleType) {
        requireParameter("groupId", groupId);
        requireParameter("ruleType", ruleType);

        RuleConfigurationDto dto = storage.getGroupRule(new GroupId(groupId).getRawGroupIdWithNull(),
                ruleType);
        Rule rval = new Rule();
        rval.setConfig(dto.getConfiguration());
        rval.setRuleType(ruleType);
        return rval;
    }

    @Override
    public void deleteGroupRule(String groupId, RuleType rule) {
        requireParameter("groupId", groupId);
        requireParameter("rule", rule);

        storage.deleteGroupRule(new GroupId(groupId).getRawGroupIdWithNull(), rule);
    }

    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#listArtifactRules(java.lang.String, java.lang.String)
     */
    @Override
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Read)
    public List<RuleType> listArtifactRules(String groupId, String artifactId) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);

        return storage.getArtifactRules(new GroupId(groupId).getRawGroupIdWithNull(), artifactId);
    }

    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#createArtifactRule(String, String, CreateRule)
     */
    @Override
    @Audited(extractParameters = { "0", KEY_GROUP_ID, "1", KEY_ARTIFACT_ID, "2", KEY_RULE })
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public void createArtifactRule(String groupId, String artifactId, CreateRule data) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);
        requireParameter("ruleType", data.getRuleType());
        requireParameter("config", data.getConfig());

        if (data.getConfig() == null || data.getConfig().isEmpty()) {
            throw new MissingRequiredParameterException("Config");
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
    @Audited(extractParameters = { "0", KEY_GROUP_ID, "1", KEY_ARTIFACT_ID })
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public void deleteArtifactRules(String groupId, String artifactId) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);

        storage.deleteArtifactRules(new GroupId(groupId).getRawGroupIdWithNull(), artifactId);
    }

    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#getArtifactRuleConfig(java.lang.String,
     *      java.lang.String, io.apicurio.registry.types.RuleType)
     */
    @Override
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Read)
    public Rule getArtifactRuleConfig(String groupId, String artifactId, RuleType ruleType) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);
        requireParameter("ruleType", ruleType);

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
    @Audited(extractParameters = { "0", KEY_GROUP_ID, "1", KEY_ARTIFACT_ID, "2", KEY_RULE_TYPE, "3",
            KEY_RULE })
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public Rule updateArtifactRuleConfig(String groupId, String artifactId, RuleType ruleType, Rule data) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);
        requireParameter("ruleType", ruleType);
        requireParameter("config", data.getConfig());

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
    @Audited(extractParameters = { "0", KEY_GROUP_ID, "1", KEY_ARTIFACT_ID, "2", KEY_RULE_TYPE })
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public void deleteArtifactRule(String groupId, String artifactId, RuleType rule) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);
        requireParameter("rule", rule);

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
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);
        requireParameter("versionExpression", versionExpression);

        var gav = VersionExpressionParser.parse(new GA(groupId, artifactId), versionExpression,
                (ga, branchId) -> storage.getBranchTip(ga, branchId, RetrievalBehavior.SKIP_DISABLED_LATEST));

        if (references == null) {
            references = HandleReferencesType.PRESERVE;
        }

        ArtifactVersionMetaDataDto metaData = storage.getArtifactVersionMetaData(gav.getRawGroupIdWithNull(),
                gav.getRawArtifactId(), gav.getRawVersionId());
        if (VersionState.DISABLED.equals(metaData.getState())) {
            throw new VersionNotFoundException(groupId, artifactId, versionExpression);
        }
        StoredArtifactVersionDto artifact = storage.getArtifactVersionContent(gav.getRawGroupIdWithNull(),
                gav.getRawArtifactId(), gav.getRawVersionId());

        TypedContent contentToReturn = TypedContent.create(artifact.getContent(), artifact.getContentType());
        contentToReturn = handleContentReferences(references, metaData.getArtifactType(), contentToReturn,
                artifact.getReferences());

        Response.ResponseBuilder builder = Response.ok(contentToReturn.getContent(),
                artifact.getContentType());
        checkIfDeprecated(metaData::getState, groupId, artifactId, versionExpression, builder);
        return builder.build();
    }

    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#deleteArtifactVersion(java.lang.String,
     *      java.lang.String, java.lang.String)
     */
    @Override
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public void deleteArtifactVersion(String groupId, String artifactId, String version) {
        if (!restConfig.isArtifactVersionDeletionEnabled()) {
            throw new NotAllowedException("Artifact version deletion operation is not enabled.",
                    HttpMethod.GET, (String[]) null);
        }

        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);
        requireParameter("version", version);

        var gav = VersionExpressionParser.parse(new GA(groupId, artifactId), version,
                (ga, branchId) -> storage.getBranchTip(ga, branchId, RetrievalBehavior.DEFAULT));

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
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);
        requireParameter("version", version);

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
    @Audited(extractParameters = { "0", KEY_GROUP_ID, "1", KEY_ARTIFACT_ID, "2", KEY_VERSION, "3",
            KEY_EDITABLE_METADATA })
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public void updateArtifactVersionMetaData(String groupId, String artifactId, String versionExpression,
            EditableVersionMetaData data) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);
        requireParameter("versionExpression", versionExpression);

        var gav = VersionExpressionParser.parse(new GA(groupId, artifactId), versionExpression,
                (ga, branchId) -> storage.getBranchTip(ga, branchId, RetrievalBehavior.DEFAULT));

        EditableVersionMetaDataDto dto = new EditableVersionMetaDataDto();
        dto.setName(data.getName());
        dto.setDescription(data.getDescription());
        dto.setLabels(data.getLabels());
        dto.setState(data.getState());
        storage.updateArtifactVersionMetaData(gav.getRawGroupIdWithNull(), gav.getRawArtifactId(),
                gav.getRawVersionId(), dto);
    }

    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#addArtifactVersionComment(java.lang.String,
     *      java.lang.String, java.lang.String, io.apicurio.registry.rest.v3.beans.NewComment)
     */
    @Override
    @Audited(extractParameters = { "0", KEY_GROUP_ID, "1", KEY_ARTIFACT_ID, "2", KEY_VERSION })
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public Comment addArtifactVersionComment(String groupId, String artifactId, String versionExpression,
            NewComment data) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);
        requireParameter("versionExpression", versionExpression);

        var gav = VersionExpressionParser.parse(new GA(groupId, artifactId), versionExpression,
                (ga, branchId) -> storage.getBranchTip(ga, branchId, RetrievalBehavior.DEFAULT));

        CommentDto newComment = storage.createArtifactVersionComment(gav.getRawGroupIdWithNull(),
                gav.getRawArtifactId(), gav.getRawVersionId(), data.getValue());
        return V3ApiUtil.commentDtoToComment(newComment);
    }

    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#deleteArtifactVersionComment(java.lang.String,
     *      java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    @Audited(extractParameters = { "0", KEY_GROUP_ID, "1", KEY_ARTIFACT_ID, "2", KEY_VERSION, "3",
            "comment_id" }) // TODO
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public void deleteArtifactVersionComment(String groupId, String artifactId, String versionExpression,
            String commentId) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);
        requireParameter("versionExpression", versionExpression);
        requireParameter("commentId", commentId);

        var gav = VersionExpressionParser.parse(new GA(groupId, artifactId), versionExpression,
                (ga, branchId) -> storage.getBranchTip(ga, branchId, RetrievalBehavior.DEFAULT));

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
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);
        requireParameter("version", version);

        var gav = VersionExpressionParser.parse(new GA(groupId, artifactId), version,
                (ga, branchId) -> storage.getBranchTip(ga, branchId, RetrievalBehavior.DEFAULT));

        return storage.getArtifactVersionComments(gav.getRawGroupIdWithNull(), gav.getRawArtifactId(),
                gav.getRawVersionId()).stream().map(V3ApiUtil::commentDtoToComment).collect(toList());
    }

    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#updateArtifactVersionComment(java.lang.String,
     *      java.lang.String, java.lang.String, java.lang.String,
     *      io.apicurio.registry.rest.v3.beans.NewComment)
     */
    @Override
    @Audited(extractParameters = { "0", KEY_GROUP_ID, "1", KEY_ARTIFACT_ID, "2", KEY_VERSION, "3",
            "comment_id" }) // TODO
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public void updateArtifactVersionComment(String groupId, String artifactId, String versionExpression,
            String commentId, NewComment data) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);
        requireParameter("versionExpression", versionExpression);
        requireParameter("commentId", commentId);
        requireParameter("value", data.getValue());

        var gav = VersionExpressionParser.parse(new GA(groupId, artifactId), versionExpression,
                (ga, branchId) -> storage.getBranchTip(ga, branchId, RetrievalBehavior.DEFAULT));

        storage.updateArtifactVersionComment(gav.getRawGroupIdWithNull(), gav.getRawArtifactId(),
                gav.getRawVersionId(), commentId, data.getValue());
    }

    @Override
    @Authorized(style = AuthorizedStyle.GroupOnly, level = AuthorizedLevel.Read)
    public ArtifactSearchResults listArtifactsInGroup(String groupId, BigInteger limit, BigInteger offset,
            SortOrder order, ArtifactSortBy orderby) {
        requireParameter("groupId", groupId);

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
    @Audited(extractParameters = { "0", KEY_GROUP_ID })
    @Authorized(style = AuthorizedStyle.GroupOnly, level = AuthorizedLevel.Write)
    public void deleteArtifactsInGroup(String groupId) {
        if (!restConfig.isArtifactDeletionEnabled()) {
            throw new NotAllowedException("Artifact deletion operation is not enabled.", HttpMethod.GET,
                    (String[]) null);
        }

        requireParameter("groupId", groupId);

        storage.deleteArtifacts(new GroupId(groupId).getRawGroupIdWithNull());
    }

    @Override
    @Audited(extractParameters = { "0", KEY_GROUP_ID, "1", KEY_IF_EXISTS, "2", KEY_CANONICAL, "3", "dryRun" })
    @Authorized(style = AuthorizedStyle.GroupOnly, level = AuthorizedLevel.Write, dryRunParam = 3)
    public CreateArtifactResponse createArtifact(String groupId, IfArtifactExists ifExists, Boolean canonical,
            Boolean dryRun, CreateArtifact data) {
        requireParameter("groupId", groupId);
        if (data.getFirstVersion() != null) {
            requireParameter("body.firstVersion.content", data.getFirstVersion().getContent());
            requireParameter("body.firstVersion.content.content",
                    data.getFirstVersion().getContent().getContent());
            requireParameter("body.firstVersion.content.contentType",
                    data.getFirstVersion().getContent().getContentType());
            if (data.getFirstVersion().getBranches() == null) {
                data.getFirstVersion().setBranches(Collections.emptyList());
            }
        } else {
            requireParameter("body.artifactType", data.getArtifactType());
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

        if (content != null && content.bytes().length == 0) {
            throw new BadRequestException(EMPTY_CONTENT_ERROR_MESSAGE);
        }

        try {
            if (artifactId == null || artifactId.trim().isEmpty()) {
                artifactId = idGenerator.generate();
            } else if (!ArtifactIdValidator.isArtifactIdAllowed(artifactId)) {
                throw new InvalidArtifactIdException(ArtifactIdValidator.ARTIFACT_ID_ERROR_MESSAGE);
            }
            TypedContent typedContent = TypedContent.create(content, contentType);

            String artifactType = ArtifactTypeUtil.determineArtifactType(typedContent, data.getArtifactType(),
                    factory);

            // Convert references to DTOs
            final List<ArtifactReferenceDto> referencesAsDtos = toReferenceDtos(references);

            // Try to resolve the references
            final Map<String, TypedContent> resolvedReferences = storage.resolveReferences(referencesAsDtos);

            // Apply any configured rules
            if (content != null) {
                rulesService.applyRules(new GroupId(groupId).getRawGroupIdWithNull(), artifactId,
                        artifactType, typedContent, RuleApplicationType.CREATE, references,
                        resolvedReferences);
            }

            // Create the artifact (with optional first version)
            EditableArtifactMetaDataDto artifactMetaData = EditableArtifactMetaDataDto.builder()
                    .description(data.getDescription()).name(data.getName()).labels(data.getLabels()).build();
            String firstVersion = null;
            ContentWrapperDto firstVersionContent = null;
            EditableVersionMetaDataDto firstVersionMetaData = null;
            List<String> firstVersionBranches = null;
            if (data.getFirstVersion() != null) {
                firstVersion = data.getFirstVersion().getVersion();
                firstVersionContent = ContentWrapperDto.builder().content(content).contentType(contentType)
                        .references(referencesAsDtos).build();
                firstVersionMetaData = EditableVersionMetaDataDto.builder()
                        .description(data.getFirstVersion().getDescription())
                        .name(data.getFirstVersion().getName()).labels(data.getFirstVersion().getLabels())
                        .build();
                firstVersionBranches = data.getFirstVersion().getBranches();
            }

            Pair<ArtifactMetaDataDto, ArtifactVersionMetaDataDto> storageResult = storage.createArtifact(
                    new GroupId(groupId).getRawGroupIdWithNull(), artifactId, artifactType, artifactMetaData,
                    firstVersion, firstVersionContent, firstVersionMetaData, firstVersionBranches,
                    dryRun != null && dryRun);

            // Now return both the artifact metadata and (if available) the version metadata
            CreateArtifactResponse rval = CreateArtifactResponse.builder()
                    .artifact(V3ApiUtil.dtoToArtifactMetaData(storageResult.getLeft())).build();
            if (storageResult.getRight() != null) {
                rval.setVersion(V3ApiUtil.dtoToVersionMetaData(storageResult.getRight()));
            }
            return rval;
        } catch (ArtifactAlreadyExistsException ex) {
            return handleIfExists(groupId, artifactId, ifExists, data.getFirstVersion(), fcanonical);
        }
    }

    @Override
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Read)
    public VersionSearchResults listArtifactVersions(String groupId, String artifactId, BigInteger offset,
            BigInteger limit, SortOrder order, VersionSortBy orderby) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);
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
        final OrderDirection oDir = order == null || order == SortOrder.desc ? OrderDirection.asc
            : OrderDirection.desc;

        Set<SearchFilter> filters = Set.of(
                SearchFilter.ofGroupId(new GroupId(groupId).getRawGroupIdWithNull()),
                SearchFilter.ofArtifactId(artifactId));
        VersionSearchResultsDto resultsDto = storage.searchVersions(filters, oBy, oDir, offset.intValue(),
                limit.intValue());
        return V3ApiUtil.dtoToSearchResults(resultsDto);
    }

    @Override
    @Audited(extractParameters = { "0", KEY_GROUP_ID, "1", KEY_ARTIFACT_ID, "2", "dryRun" })
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write, dryRunParam = 2)
    public VersionMetaData createArtifactVersion(String groupId, String artifactId, Boolean dryRun,
            CreateVersion data) {
        requireParameter("content", data.getContent());
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);
        requireParameter("body.content", data.getContent());
        requireParameter("body.content.content", data.getContent().getContent());
        requireParameter("body.content.contentType", data.getContent().getContentType());

        ContentHandle content = ContentHandle.create(data.getContent().getContent());
        if (content.bytes().length == 0) {
            throw new BadRequestException(EMPTY_CONTENT_ERROR_MESSAGE);
        }
        String ct = data.getContent().getContentType();

        // Transform the given references into dtos
        final List<ArtifactReferenceDto> referencesAsDtos = toReferenceDtos(
                data.getContent().getReferences());

        // Try to resolve the new artifact references and the nested ones (if any)
        final Map<String, TypedContent> resolvedReferences = storage.resolveReferences(referencesAsDtos);

        String artifactType = lookupArtifactType(groupId, artifactId);
        TypedContent typedContent = TypedContent.create(content, ct);
        rulesService.applyRules(new GroupId(groupId).getRawGroupIdWithNull(), artifactId, artifactType,
                typedContent, RuleApplicationType.UPDATE, data.getContent().getReferences(),
                resolvedReferences);
        EditableVersionMetaDataDto metaDataDto = EditableVersionMetaDataDto.builder()
                .description(data.getDescription()).name(data.getName()).labels(data.getLabels()).build();
        ContentWrapperDto contentDto = ContentWrapperDto.builder().contentType(ct).content(content)
                .references(referencesAsDtos).build();

        ArtifactVersionMetaDataDto vmd = storage.createArtifactVersion(
                new GroupId(groupId).getRawGroupIdWithNull(), artifactId, data.getVersion(), artifactType,
                contentDto, metaDataDto, data.getBranches(), dryRun != null && dryRun);

        return V3ApiUtil.dtoToVersionMetaData(vmd);
    }

    @Override
    @Audited(extractParameters = { "0", KEY_GROUP_ID, "1", KEY_ARTIFACT_ID })
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public BranchMetaData createBranch(String groupId, String artifactId, CreateBranch data) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);
        requireParameter("branchId", data.getBranchId());

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
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);

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
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);

        BranchMetaDataDto branch = storage.getBranchMetaData(new GA(groupId, artifactId),
                new BranchId(branchId));
        return V3ApiUtil.dtoToBranchMetaData(branch);
    }

    @Override
    @Audited(extractParameters = { "0", KEY_GROUP_ID, "1", KEY_ARTIFACT_ID })
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public void updateBranchMetaData(String groupId, String artifactId, String branchId,
            EditableBranchMetaData data) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);
        requireParameter("branchId", branchId);

        EditableBranchMetaDataDto dto = EditableBranchMetaDataDto.builder().description(data.getDescription())
                .build();
        storage.updateBranchMetaData(new GA(groupId, artifactId), new BranchId(branchId), dto);
    }

    @Override
    @Audited(extractParameters = { "0", KEY_GROUP_ID, "1", KEY_ARTIFACT_ID })
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public void deleteBranch(String groupId, String artifactId, String branchId) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);
        requireParameter("branchId", branchId);

        storage.deleteBranch(new GA(groupId, artifactId), new BranchId(branchId));
    }

    @Override
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Read)
    public VersionSearchResults listBranchVersions(String groupId, String artifactId, String branchId,
            BigInteger offset, BigInteger limit) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);
        requireParameter("branchId", branchId);

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
    @Audited(extractParameters = { "0", KEY_GROUP_ID, "1", KEY_ARTIFACT_ID })
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public void replaceBranchVersions(String groupId, String artifactId, String branchId,
            ReplaceBranchVersions data) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);
        requireParameter("branchId", branchId);
        requireParameter("versions", data.getVersions());

        GA ga = new GA(groupId, artifactId);
        BranchId bid = new BranchId(branchId);

        // Throw 404 if the artifact or branch does not exist.
        storage.getBranchMetaData(ga, bid);

        storage.replaceBranchVersions(ga, bid, data.getVersions().stream().map(VersionId::new).toList());
    }

    @Override
    @Audited(extractParameters = { "0", KEY_GROUP_ID, "1", KEY_ARTIFACT_ID })
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public void addVersionToBranch(String groupId, String artifactId, String branchId,
            AddVersionToBranch data) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);
        requireParameter("branchId", branchId);

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

    private static void requireParameter(String parameterName, Object parameterValue) {
        if (parameterValue == null) {
            throw new MissingRequiredParameterException(parameterName);
        }
    }

    private CreateArtifactResponse handleIfExists(String groupId, String artifactId,
            IfArtifactExists ifExists, CreateVersion theVersion, boolean canonical) {
        if (ifExists == null || theVersion == null) {
            ifExists = IfArtifactExists.FAIL;
        }

        switch (ifExists) {
            case CREATE_VERSION:
                return updateArtifactInternal(groupId, artifactId, theVersion);
            // case RETURN:
            // GAV latestGAV = storage.getBranchTip(new GA(groupId, artifactId), BranchId.LATEST,
            // ArtifactRetrievalBehavior.DEFAULT);
            // ArtifactVersionMetaDataDto latestVersionMD =
            // storage.getArtifactVersionMetaData(latestGAV.getRawGroupIdWithNull(),
            // latestGAV.getRawArtifactId(), latestGAV.getRawVersionId());
            // return V3ApiUtil.dtoToVersionMetaData(latestVersionMD);
            case FIND_OR_CREATE_VERSION:
                return handleIfExistsReturnOrUpdate(groupId, artifactId, theVersion, canonical);
            default:
                throw new ArtifactAlreadyExistsException(groupId, artifactId);
        }
    }

    private CreateArtifactResponse handleIfExistsReturnOrUpdate(String groupId, String artifactId,
            CreateVersion theVersion, boolean canonical) {
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
        return updateArtifactInternal(groupId, artifactId, theVersion);
    }

    private CreateArtifactResponse updateArtifactInternal(String groupId, String artifactId,
            CreateVersion theVersion) {
        String version = theVersion.getVersion();
        String name = theVersion.getName();
        String description = theVersion.getDescription();
        List<String> branches = theVersion.getBranches();
        Map<String, String> labels = theVersion.getLabels();
        List<ArtifactReference> references = theVersion.getContent().getReferences();
        String contentType = theVersion.getContent().getContentType();
        ContentHandle content = ContentHandle.create(theVersion.getContent().getContent());

        String artifactType = lookupArtifactType(groupId, artifactId);

        // Transform the given references into dtos and set the contentId, this will also detect if any of the
        // passed references does not exist.
        final List<ArtifactReferenceDto> referencesAsDtos = toReferenceDtos(references);

        final Map<String, TypedContent> resolvedReferences = storage.resolveReferences(referencesAsDtos);
        final TypedContent typedContent = TypedContent.create(content, contentType);
        rulesService.applyRules(new GroupId(groupId).getRawGroupIdWithNull(), artifactId, artifactType,
                typedContent, RuleApplicationType.UPDATE, references, resolvedReferences);
        EditableVersionMetaDataDto metaData = EditableVersionMetaDataDto.builder().name(name)
                .description(description).labels(labels).build();
        ContentWrapperDto contentDto = ContentWrapperDto.builder().contentType(contentType).content(content)
                .references(referencesAsDtos).build();
        ArtifactVersionMetaDataDto vmdDto = storage.createArtifactVersion(groupId, artifactId, version,
                artifactType, contentDto, metaData, branches, false);
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

}
