package io.apicurio.registry.rest.v3;

import com.google.common.hash.Hashing;
import io.apicurio.common.apps.logging.Logged;
import io.apicurio.common.apps.logging.audit.Audited;
import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.model.*;
import io.apicurio.registry.rest.HeadersHack;
import io.apicurio.registry.rest.MissingRequiredParameterException;
import io.apicurio.registry.rest.ParametersConflictException;
import io.apicurio.registry.rest.RestConfig;
import io.apicurio.registry.rest.v3.beans.*;
import io.apicurio.registry.rest.v3.shared.CommonResourceOperations;
import io.apicurio.registry.rules.RuleApplicationType;
import io.apicurio.registry.rules.RulesService;
import io.apicurio.registry.storage.RegistryStorage.ArtifactRetrievalBehavior;
import io.apicurio.registry.storage.dto.*;
import io.apicurio.registry.storage.error.*;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ReferenceType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.util.ArtifactIdGenerator;
import io.apicurio.registry.util.ArtifactTypeUtil;
import io.apicurio.registry.util.ContentTypeUtil;
import io.apicurio.registry.utils.ArtifactIdValidator;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.JAXRSClientUtil;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptors;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.NotAllowedException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jose4j.base64url.Base64;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Supplier;

import static io.apicurio.common.apps.logging.audit.AuditingConstants.*;
import static io.apicurio.registry.logging.audit.AuditingConstants.KEY_OWNER;
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
    ArtifactIdGenerator idGenerator;

    @Inject
    RestConfig restConfig;

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    CommonResourceOperations common;

    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#getLatestArtifact(java.lang.String, java.lang.String, io.apicurio.registry.rest.v3.beans.HandleReferencesType)
     */
    @Override
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Read)
    public Response getLatestArtifact(String groupId, String artifactId, HandleReferencesType references) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);

        if (references == null) {
            references = HandleReferencesType.PRESERVE;
        }

        ArtifactMetaDataDto metaData = storage.getArtifactMetaData(new GroupId(groupId).getRawGroupIdWithNull(), artifactId);
        if (ArtifactState.DISABLED.equals(metaData.getState())) {
            throw new ArtifactNotFoundException(groupId, artifactId);
        }
        StoredArtifactDto artifact = storage.getArtifact(new GroupId(groupId).getRawGroupIdWithNull(), artifactId);

        MediaType contentType = factory.getArtifactMediaType(metaData.getType());

        ContentHandle contentToReturn = artifact.getContent();
        contentToReturn = handleContentReferences(references, metaData.getType(), contentToReturn, artifact.getReferences());

        Response.ResponseBuilder builder = Response.ok(contentToReturn, contentType);
        checkIfDeprecated(metaData::getState, groupId, artifactId, metaData.getVersion(), builder);
        return builder.build();
    }


    @Override
    @Audited(extractParameters = {"0", KEY_GROUP_ID, "1", KEY_ARTIFACT_ID, "2", KEY_VERSION, "3", KEY_NAME,
            "4", KEY_NAME_ENCODED, "5", KEY_DESCRIPTION, "6", KEY_DESCRIPTION_ENCODED, "7", "branch"}) // TODO
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public ArtifactMetaData updateArtifact(String groupId, String artifactId, String xRegistryVersion, String xRegistryName,
                                           String xRegistryNameEncoded, String xRegistryDescription, String xRegistryDescriptionEncoded, List<String> xRegistryArtifactBranches,
                                           InputStream data) {
        return this.updateArtifactWithRefs(groupId, artifactId, xRegistryVersion, xRegistryName, xRegistryNameEncoded, xRegistryDescription, xRegistryDescriptionEncoded, xRegistryArtifactBranches, data, Collections.emptyList());
    }


    @Override
    @Audited(extractParameters = {"0", KEY_GROUP_ID, "1", KEY_ARTIFACT_ID, "2", KEY_VERSION, "3", KEY_NAME,
            "4", KEY_NAME_ENCODED, "5", KEY_DESCRIPTION, "6", KEY_DESCRIPTION_ENCODED, "7", "branch"}) // TODO
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public ArtifactMetaData updateArtifact(String groupId, String artifactId, String xRegistryVersion, String xRegistryName,
                                           String xRegistryNameEncoded, String xRegistryDescription, String xRegistryDescriptionEncoded, List<String> xRegistryArtifactBranches,
                                           ArtifactContent data) {
        requireParameter("content", data.getContent());
        return this.updateArtifactWithRefs(groupId, artifactId, xRegistryVersion, xRegistryName, xRegistryNameEncoded, xRegistryDescription, xRegistryDescriptionEncoded, xRegistryArtifactBranches, IoUtil.toStream(data.getContent()), data.getReferences());
    }

    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#getArtifactVersionReferences(java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.types.ReferenceType)
     */
    @Override
    public List<ArtifactReference> getArtifactVersionReferences(String groupId, String artifactId,
                                                                String versionExpression, ReferenceType refType) {

        var gav = VersionExpressionParser.parse(new GA(groupId, artifactId), versionExpression,
                (ga, branchId) -> storage.getArtifactBranchTip(ga, branchId, ArtifactRetrievalBehavior.DEFAULT));

        if (refType == null || refType == ReferenceType.OUTBOUND) {
            return storage.getArtifactVersion(gav.getRawGroupIdWithNull(), gav.getRawArtifactId(), gav.getRawVersionId())
                    .getReferences()
                    .stream()
                    .map(V3ApiUtil::referenceDtoToReference)
                    .collect(toList());
        } else {
            return storage.getInboundArtifactReferences(gav.getRawGroupIdWithNull(), gav.getRawArtifactId(), gav.getRawVersionId())
                    .stream()
                    .map(V3ApiUtil::referenceDtoToReference)
                    .collect(toList());
        }
    }

    private ArtifactMetaData updateArtifactWithRefs(String groupId, String artifactId, String xRegistryVersion, String xRegistryName,
                                                    String xRegistryNameEncoded, String xRegistryDescription, String xRegistryDescriptionEncoded,
                                                    List<String> artifactBranches,
                                                    InputStream data, List<ArtifactReference> references) {

        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);

        maxOneOf("X-Registry-Name", xRegistryName, "X-Registry-Name-Encoded", xRegistryNameEncoded);
        maxOneOf("X-Registry-Description", xRegistryDescription, "X-Registry-Description-Encoded", xRegistryDescriptionEncoded);

        String artifactName = getOneOf(xRegistryName, decode(xRegistryNameEncoded));
        String artifactDescription = getOneOf(xRegistryDescription, decode(xRegistryDescriptionEncoded));

        ContentHandle content = ContentHandle.create(data);
        if (content.bytes().length == 0) {
            throw new BadRequestException(EMPTY_CONTENT_ERROR_MESSAGE);
        }
        return updateArtifactInternal(groupId, artifactId, xRegistryVersion, artifactName, artifactDescription, artifactBranches, content, getContentType(), references);
    }

    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#deleteArtifact(java.lang.String, java.lang.String)
     */
    @Override
    @Audited(extractParameters = {"0", KEY_GROUP_ID, "1", KEY_ARTIFACT_ID})
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public void deleteArtifact(String groupId, String artifactId) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);

        storage.deleteArtifact(new GroupId(groupId).getRawGroupIdWithNull(), artifactId);
    }

    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#getArtifactMetaData(java.lang.String, java.lang.String)
     */
    @Override
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Read)
    public ArtifactMetaData getArtifactMetaData(String groupId, String artifactId) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);

        ArtifactMetaDataDto dto = storage.getArtifactMetaData(new GroupId(groupId).getRawGroupIdWithNull(), artifactId);
        return V3ApiUtil.dtoToMetaData(new GroupId(groupId).getRawGroupIdWithNull(), artifactId, dto.getType(), dto);
    }

    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#updateArtifactMetaData(java.lang.String, java.lang.String, io.apicurio.registry.rest.v3.beans.EditableMetaData)
     */
    @Override
    @Audited(extractParameters = {"0", KEY_GROUP_ID, "1", KEY_ARTIFACT_ID, "2", KEY_EDITABLE_METADATA})
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public void updateArtifactMetaData(String groupId, String artifactId, EditableArtifactMetaData data) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);

        EditableArtifactMetaDataDto dto = new EditableArtifactMetaDataDto();
        dto.setName(data.getName());
        dto.setDescription(data.getDescription());
        dto.setLabels(data.getLabels());
        storage.updateArtifactMetaData(new GroupId(groupId).getRawGroupIdWithNull(), artifactId, dto);
    }

    @Override
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Read)
    public ArtifactOwner getArtifactOwner(String groupId, String artifactId) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);

        ArtifactMetaDataDto dto = storage.getArtifactMetaData(new GroupId(groupId).getRawGroupIdWithNull(), artifactId);
        ArtifactOwner owner = new ArtifactOwner();
        owner.setOwner(dto.getOwner());
        return owner;
    }

    @Override
    @Audited(extractParameters = {"0", KEY_GROUP_ID, "1", KEY_ARTIFACT_ID, "2", KEY_OWNER})
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.AdminOrOwner)
    public void updateArtifactOwner(String groupId, String artifactId, ArtifactOwner data) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);
        requireParameter("data", data);

        if (data.getOwner().isEmpty()) {
            throw new MissingRequiredParameterException("Missing required owner");
        }

        ArtifactOwnerDto dto = new ArtifactOwnerDto(data.getOwner());
        storage.updateArtifactOwner(new GroupId(groupId).getRawGroupIdWithNull(), artifactId, dto);
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
        storage.deleteGroup(groupId);
    }
    
    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#updateGroupById(java.lang.String, io.apicurio.registry.rest.v3.beans.EditableGroupMetaData)
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
    public GroupSearchResults listGroups(BigInteger limit, BigInteger offset, SortOrder order, SortBy orderby) {
        if (orderby == null) {
            orderby = SortBy.name;
        }
        if (offset == null) {
            offset = BigInteger.valueOf(0);
        }
        if (limit == null) {
            limit = BigInteger.valueOf(20);
        }

        final OrderBy oBy = OrderBy.valueOf(orderby.name());
        final OrderDirection oDir = order == null || order == SortOrder.asc ? OrderDirection.asc : OrderDirection.desc;

        Set<SearchFilter> filters = Collections.emptySet();

        GroupSearchResultsDto resultsDto = storage.searchGroups(filters, oBy, oDir, offset.intValue(), limit.intValue());
        return V3ApiUtil.dtoToSearchResults(resultsDto);
    }

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Write)
    public GroupMetaData createGroup(CreateGroupMetaData data) {
        GroupMetaDataDto.GroupMetaDataDtoBuilder group = GroupMetaDataDto.builder()
                .groupId(data.getId())
                .description(data.getDescription())
                .labels(data.getLabels());

        String user = securityIdentity.getPrincipal().getName();
        group.owner(user).createdOn(new Date().getTime());

        storage.createGroup(group.build());

        return V3ApiUtil.groupDtoToGroup(storage.getGroupMetaData(data.getId()));
    }

    @Override
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Read)
    public VersionMetaData getArtifactVersionMetaDataByContent(String groupId, String artifactId, Boolean canonical, ArtifactContent artifactContent) {
        return getArtifactVersionMetaDataByContent(groupId, artifactId, canonical, IoUtil.toStream(artifactContent.getContent()), artifactContent.getReferences());
    }

    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#getArtifactVersionMetaDataByContent(java.lang.String, java.lang.String, java.lang.Boolean, java.io.InputStream)
     */
    @Override
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Read)
    public VersionMetaData getArtifactVersionMetaDataByContent(String groupId, String artifactId, Boolean canonical, InputStream data) {
        return getArtifactVersionMetaDataByContent(groupId, artifactId, canonical, data, Collections.emptyList());
    }

    private VersionMetaData getArtifactVersionMetaDataByContent(String groupId, String artifactId, Boolean canonical, InputStream data, List<ArtifactReference> artifactReferences) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);

        if (canonical == null) {
            canonical = Boolean.FALSE;
        }
        ContentHandle content = ContentHandle.create(data);
        if (content.bytes().length == 0) {
            throw new BadRequestException(EMPTY_CONTENT_ERROR_MESSAGE);
        }
        if (ContentTypeUtil.isApplicationYaml(getContentType())) {
            content = ContentTypeUtil.yamlToJson(content);
        }

        final List<ArtifactReferenceDto> artifactReferenceDtos = toReferenceDtos(artifactReferences);

        ArtifactVersionMetaDataDto dto = storage.getArtifactVersionMetaData(new GroupId(groupId).getRawGroupIdWithNull(), artifactId, canonical, content, artifactReferenceDtos);
        return V3ApiUtil.dtoToVersionMetaData(new GroupId(groupId).getRawGroupIdWithNull(), artifactId, dto.getType(), dto);
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
     * @see io.apicurio.registry.rest.v3.GroupsResource#createArtifactRule(java.lang.String, java.lang.String, io.apicurio.registry.rest.v3.beans.Rule)
     */
    @Override
    @Audited(extractParameters = {"0", KEY_GROUP_ID, "1", KEY_ARTIFACT_ID, "2", KEY_RULE})
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public void createArtifactRule(String groupId, String artifactId, Rule data) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);

        RuleType type = data.getType();
        requireParameter("type", type);

        if (data.getConfig() == null || data.getConfig().isEmpty()) {
            throw new MissingRequiredParameterException("Config");
        }

        RuleConfigurationDto config = new RuleConfigurationDto();
        config.setConfiguration(data.getConfig());

        if (!storage.isArtifactExists(new GroupId(groupId).getRawGroupIdWithNull(), artifactId)) {
            throw new ArtifactNotFoundException(groupId, artifactId);
        }

        storage.createArtifactRule(new GroupId(groupId).getRawGroupIdWithNull(), artifactId, data.getType(), config);
    }

    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#deleteArtifactRules(java.lang.String, java.lang.String)
     */
    @Override
    @Audited(extractParameters = {"0", KEY_GROUP_ID, "1", KEY_ARTIFACT_ID})
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public void deleteArtifactRules(String groupId, String artifactId) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);

        storage.deleteArtifactRules(new GroupId(groupId).getRawGroupIdWithNull(), artifactId);
    }

    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#getArtifactRuleConfig(java.lang.String, java.lang.String, io.apicurio.registry.types.RuleType)
     */
    @Override
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Read)
    public Rule getArtifactRuleConfig(String groupId, String artifactId, RuleType rule) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);
        requireParameter("rule", rule);

        RuleConfigurationDto dto = storage.getArtifactRule(new GroupId(groupId).getRawGroupIdWithNull(), artifactId, rule);
        Rule rval = new Rule();
        rval.setConfig(dto.getConfiguration());
        rval.setType(rule);
        return rval;
    }

    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#updateArtifactRuleConfig(java.lang.String, java.lang.String, io.apicurio.registry.types.RuleType, io.apicurio.registry.rest.v3.beans.Rule)
     */
    @Override
    @Audited(extractParameters = {"0", KEY_GROUP_ID, "1", KEY_ARTIFACT_ID, "2", KEY_RULE_TYPE, "3", KEY_RULE})
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public Rule updateArtifactRuleConfig(String groupId, String artifactId, RuleType rule, Rule data) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);
        requireParameter("rule", rule);

        RuleConfigurationDto dto = new RuleConfigurationDto(data.getConfig());
        storage.updateArtifactRule(new GroupId(groupId).getRawGroupIdWithNull(), artifactId, rule, dto);
        Rule rval = new Rule();
        rval.setType(rule);
        rval.setConfig(data.getConfig());
        return rval;
    }

    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#deleteArtifactRule(java.lang.String, java.lang.String, io.apicurio.registry.types.RuleType)
     */
    @Override
    @Audited(extractParameters = {"0", KEY_GROUP_ID, "1", KEY_ARTIFACT_ID, "2", KEY_RULE_TYPE})
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public void deleteArtifactRule(String groupId, String artifactId, RuleType rule) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);
        requireParameter("rule", rule);

        storage.deleteArtifactRule(new GroupId(groupId).getRawGroupIdWithNull(), artifactId, rule);
    }

    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#updateArtifactState(java.lang.String, java.lang.String, io.apicurio.registry.rest.v3.beans.UpdateState)
     */
    @Override
    @Audited(extractParameters = {"0", KEY_GROUP_ID, "1", KEY_ARTIFACT_ID, "2", KEY_UPDATE_STATE})
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public void updateArtifactState(String groupId, String artifactId, UpdateState data) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);
        requireParameter("body.state", data.getState());
        storage.updateArtifactState(new GroupId(groupId).getRawGroupIdWithNull(), artifactId, data.getState());
    }

    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#testUpdateArtifact(java.lang.String, java.lang.String, java.io.InputStream)
     */
    @Override
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public void testUpdateArtifact(String groupId, String artifactId, InputStream data) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);
        ContentHandle content = ContentHandle.create(data);
        if (content.bytes().length == 0) {
            throw new BadRequestException(EMPTY_CONTENT_ERROR_MESSAGE);
        }

        String ct = getContentType();
        if (ContentTypeUtil.isApplicationYaml(ct)) {
            content = ContentTypeUtil.yamlToJson(content);
        }

        String artifactType = lookupArtifactType(groupId, artifactId);
        rulesService.applyRules(new GroupId(groupId).getRawGroupIdWithNull(), artifactId, artifactType, content,
                RuleApplicationType.UPDATE, Collections.emptyList(), Collections.emptyMap()); //TODO:references not supported for testing update
    }

    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#getArtifactVersion(java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.rest.v3.beans.HandleReferencesType)
     */
    @Override
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Read)
    public Response getArtifactVersion(String groupId, String artifactId, String versionExpression, HandleReferencesType references) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);
        requireParameter("versionExpression", versionExpression);

        var gav = VersionExpressionParser.parse(new GA(groupId, artifactId), versionExpression,
                (ga, branchId) -> storage.getArtifactBranchTip(ga, branchId, ArtifactRetrievalBehavior.DEFAULT));

        if (references == null) {
            references = HandleReferencesType.PRESERVE;
        }

        ArtifactVersionMetaDataDto metaData = storage.getArtifactVersionMetaData(gav.getRawGroupIdWithNull(), gav.getRawArtifactId(), gav.getRawVersionId());
        if (ArtifactState.DISABLED.equals(metaData.getState())) {
            throw new VersionNotFoundException(groupId, artifactId, versionExpression);
        }
        StoredArtifactDto artifact = storage.getArtifactVersion(gav.getRawGroupIdWithNull(), gav.getRawArtifactId(), gav.getRawVersionId());

        MediaType contentType = factory.getArtifactMediaType(metaData.getType());

        ContentHandle contentToReturn = artifact.getContent();
        contentToReturn = handleContentReferences(references, metaData.getType(), contentToReturn, artifact.getReferences());

        Response.ResponseBuilder builder = Response.ok(contentToReturn, contentType);
        checkIfDeprecated(metaData::getState, groupId, artifactId, versionExpression, builder);
        return builder.build();
    }

    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#deleteArtifactVersion(java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public void deleteArtifactVersion(String groupId, String artifactId, String version) {
        if (!restConfig.isArtifactVersionDeletionEnabled()) {
            throw new NotAllowedException("Artifact version deletion operation is not enabled.", HttpMethod.GET, (String[]) null);
        }

        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);
        requireParameter("version", version);

        var gav = VersionExpressionParser.parse(new GA(groupId, artifactId), version,
                (ga, branchId) -> storage.getArtifactBranchTip(ga, branchId, ArtifactRetrievalBehavior.DEFAULT));

        storage.deleteArtifactVersion(gav.getRawGroupIdWithNull(), gav.getRawArtifactId(), gav.getRawVersionId());
    }

    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#getArtifactVersionMetaData(java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Read)
    public VersionMetaData getArtifactVersionMetaData(String groupId, String artifactId, String version) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);
        requireParameter("version", version);

        var gav = VersionExpressionParser.parse(new GA(groupId, artifactId), version,
                (ga, branchId) -> storage.getArtifactBranchTip(ga, branchId, ArtifactRetrievalBehavior.DEFAULT));

        ArtifactVersionMetaDataDto dto = storage.getArtifactVersionMetaData(gav.getRawGroupIdWithNull(), gav.getRawArtifactId(), gav.getRawVersionId());
        return V3ApiUtil.dtoToVersionMetaData(gav.getRawGroupIdWithDefaultString(), gav.getRawArtifactId(), dto.getType(), dto);
    }

    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#updateArtifactVersionMetaData(java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.rest.v3.beans.EditableArtifactMetaData)
     */
    @Override
    @Audited(extractParameters = {"0", KEY_GROUP_ID, "1", KEY_ARTIFACT_ID, "2", KEY_VERSION, "3", KEY_EDITABLE_METADATA})
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public void updateArtifactVersionMetaData(String groupId, String artifactId, String versionExpression, EditableArtifactMetaData data) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);
        requireParameter("versionExpression", versionExpression);

        var gav = VersionExpressionParser.parse(new GA(groupId, artifactId), versionExpression,
                (ga, branchId) -> storage.getArtifactBranchTip(ga, branchId, ArtifactRetrievalBehavior.DEFAULT));

        EditableArtifactMetaDataDto dto = new EditableArtifactMetaDataDto();
        dto.setName(data.getName());
        dto.setDescription(data.getDescription());
        dto.setLabels(data.getLabels());
        storage.updateArtifactVersionMetaData(gav.getRawGroupIdWithNull(), gav.getRawArtifactId(), gav.getRawVersionId(), dto);
    }

    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#deleteArtifactVersionMetaData(java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    @Audited(extractParameters = {"0", KEY_GROUP_ID, "1", KEY_ARTIFACT_ID, "2", KEY_VERSION})
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public void deleteArtifactVersionMetaData(String groupId, String artifactId, String version) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);
        requireParameter("version", version);

        var gav = VersionExpressionParser.parse(new GA(groupId, artifactId), version,
                (ga, branchId) -> storage.getArtifactBranchTip(ga, branchId, ArtifactRetrievalBehavior.DEFAULT));

        storage.deleteArtifactVersionMetaData(gav.getRawGroupIdWithNull(), gav.getRawArtifactId(), gav.getRawVersionId());
    }

    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#addArtifactVersionComment(java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.rest.v3.beans.NewComment)
     */
    @Override
    @Audited(extractParameters = {"0", KEY_GROUP_ID, "1", KEY_ARTIFACT_ID, "2", KEY_VERSION})
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public Comment addArtifactVersionComment(String groupId, String artifactId, String versionExpression, NewComment data) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);
        requireParameter("versionExpression", versionExpression);

        var gav = VersionExpressionParser.parse(new GA(groupId, artifactId), versionExpression,
                (ga, branchId) -> storage.getArtifactBranchTip(ga, branchId, ArtifactRetrievalBehavior.DEFAULT));

        CommentDto newComment = storage.createArtifactVersionComment(gav.getRawGroupIdWithNull(), gav.getRawArtifactId(),
                gav.getRawVersionId(), data.getValue());
        return V3ApiUtil.commentDtoToComment(newComment);
    }

    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#deleteArtifactVersionComment(java.lang.String, java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    @Audited(extractParameters = {"0", KEY_GROUP_ID, "1", KEY_ARTIFACT_ID, "2", KEY_VERSION, "3", "comment_id"}) // TODO
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public void deleteArtifactVersionComment(String groupId, String artifactId, String versionExpression, String commentId) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);
        requireParameter("versionExpression", versionExpression);
        requireParameter("commentId", commentId);

        var gav = VersionExpressionParser.parse(new GA(groupId, artifactId), versionExpression,
                (ga, branchId) -> storage.getArtifactBranchTip(ga, branchId, ArtifactRetrievalBehavior.DEFAULT));

        storage.deleteArtifactVersionComment(gav.getRawGroupIdWithNull(), gav.getRawArtifactId(), gav.getRawVersionId(), commentId);
    }

    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#getArtifactVersionComments(java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Read)
    public List<Comment> getArtifactVersionComments(String groupId, String artifactId, String version) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);
        requireParameter("version", version);

        var gav = VersionExpressionParser.parse(new GA(groupId, artifactId), version,
                (ga, branchId) -> storage.getArtifactBranchTip(ga, branchId, ArtifactRetrievalBehavior.DEFAULT));

        return storage.getArtifactVersionComments(gav.getRawGroupIdWithNull(), gav.getRawArtifactId(), gav.getRawVersionId())
                .stream()
                .map(V3ApiUtil::commentDtoToComment)
                .collect(toList());
    }

    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#updateArtifactVersionComment(java.lang.String, java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.rest.v3.beans.NewComment)
     */
    @Override
    @Audited(extractParameters = {"0", KEY_GROUP_ID, "1", KEY_ARTIFACT_ID, "2", KEY_VERSION, "3", "comment_id"}) // TODO
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public void updateArtifactVersionComment(String groupId, String artifactId, String versionExpression, String commentId, NewComment data) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);
        requireParameter("versionExpression", versionExpression);
        requireParameter("commentId", commentId);
        requireParameter("value", data.getValue());

        var gav = VersionExpressionParser.parse(new GA(groupId, artifactId), versionExpression,
                (ga, branchId) -> storage.getArtifactBranchTip(ga, branchId, ArtifactRetrievalBehavior.DEFAULT));

        storage.updateArtifactVersionComment(gav.getRawGroupIdWithNull(), gav.getRawArtifactId(),
                gav.getRawVersionId(), commentId, data.getValue());
    }

    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#updateArtifactVersionState(java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.rest.v3.beans.UpdateState)
     */
    @Override
    @Audited(extractParameters = {"0", KEY_GROUP_ID, "1", KEY_ARTIFACT_ID, "2", KEY_VERSION, "3", KEY_UPDATE_STATE})
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public void updateArtifactVersionState(String groupId, String artifactId, String versionExpression, UpdateState data) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);
        requireParameter("versionExpression", versionExpression);

        var gav = VersionExpressionParser.parse(new GA(groupId, artifactId), versionExpression,
                (ga, branchId) -> storage.getArtifactBranchTip(ga, branchId, ArtifactRetrievalBehavior.DEFAULT));

        storage.updateArtifactState(gav.getRawGroupIdWithNull(), gav.getRawArtifactId(), gav.getRawVersionId(), data.getState());
    }


    @Override
    @Authorized(style = AuthorizedStyle.GroupOnly, level = AuthorizedLevel.Read)
    public ArtifactSearchResults listArtifactsInGroup(String groupId, BigInteger limit, BigInteger offset,
                                                      SortOrder order, SortBy orderby) {
        requireParameter("groupId", groupId);

        if (orderby == null) {
            orderby = SortBy.name;
        }
        if (offset == null) {
            offset = BigInteger.valueOf(0);
        }
        if (limit == null) {
            limit = BigInteger.valueOf(20);
        }

        final OrderBy oBy = OrderBy.valueOf(orderby.name());
        final OrderDirection oDir = order == null || order == SortOrder.asc ? OrderDirection.asc : OrderDirection.desc;

        Set<SearchFilter> filters = new HashSet<>();
        filters.add(SearchFilter.ofGroup(new GroupId(groupId).getRawGroupIdWithNull()));

        ArtifactSearchResultsDto resultsDto = storage.searchArtifacts(filters, oBy, oDir, offset.intValue(), limit.intValue());
        return V3ApiUtil.dtoToSearchResults(resultsDto);
    }

    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#deleteArtifactsInGroup(java.lang.String)
     */
    @Override
    @Audited(extractParameters = {"0", KEY_GROUP_ID})
    @Authorized(style = AuthorizedStyle.GroupOnly, level = AuthorizedLevel.Write)
    public void deleteArtifactsInGroup(String groupId) {
        requireParameter("groupId", groupId);

        storage.deleteArtifacts(new GroupId(groupId).getRawGroupIdWithNull());
    }


    @Override
    @Audited(extractParameters = {"0", KEY_GROUP_ID, "1", KEY_ARTIFACT_TYPE, "2", KEY_ARTIFACT_ID, "3", KEY_VERSION,
            "4", KEY_IF_EXISTS, "5", KEY_CANONICAL, "6", KEY_DESCRIPTION, "7", KEY_DESCRIPTION_ENCODED,
            "8", KEY_NAME, "9", KEY_NAME_ENCODED, "10", KEY_FROM_URL, "11", KEY_SHA,
            "12", "branch"}) // TODO
    @Authorized(style = AuthorizedStyle.GroupOnly, level = AuthorizedLevel.Write)
    public ArtifactMetaData createArtifact(String groupId, String xRegistryArtifactType, String xRegistryArtifactId, String xRegistryVersion,
                                           IfExists ifExists, Boolean canonical, String xRegistryDescription, String xRegistryDescriptionEncoded,
                                           String xRegistryName, String xRegistryNameEncoded, String xRegistryContentHash, String xRegistryHashAlgorithm,
                                           List<String> xRegistryArtifactBranches, InputStream data) {
        return this.createArtifactWithRefs(groupId, xRegistryArtifactType, xRegistryArtifactId, xRegistryVersion, ifExists, canonical, xRegistryDescription, xRegistryDescriptionEncoded, xRegistryName,
                xRegistryNameEncoded, xRegistryContentHash, xRegistryHashAlgorithm, xRegistryArtifactBranches, data, Collections.emptyList());
    }


    @Override
    @Audited(extractParameters = {"0", KEY_GROUP_ID, "1", KEY_ARTIFACT_TYPE, "2", KEY_ARTIFACT_ID, "3", KEY_VERSION,
            "4", KEY_IF_EXISTS, "5", KEY_CANONICAL, "6", KEY_DESCRIPTION, "7", KEY_DESCRIPTION_ENCODED,
            "8", KEY_NAME, "9", KEY_NAME_ENCODED, "10", KEY_FROM_URL, "11", KEY_SHA,
            "12", "branch"}) // TODO
    @Authorized(style = AuthorizedStyle.GroupOnly, level = AuthorizedLevel.Write)
    public ArtifactMetaData createArtifact(String groupId, String xRegistryArtifactType, String xRegistryArtifactId, String xRegistryVersion,
                                           IfExists ifExists, Boolean canonical, String xRegistryDescription, String xRegistryDescriptionEncoded,
                                           String xRegistryName, String xRegistryNameEncoded, String xRegistryContentHash, String xRegistryHashAlgorithm,
                                           List<String> xRegistryArtifactBranches, ArtifactContent data) {
        requireParameter("content", data.getContent());

        Client client = null;
        InputStream content;
        try {
            try {
                URL url = new URL(data.getContent());
                client = JAXRSClientUtil.getJAXRSClient(restConfig.getDownloadSkipSSLValidation());
                content = fetchContentFromURL(client, url.toURI());
            } catch (MalformedURLException | URISyntaxException e) {
                content = IoUtil.toStream(data.getContent());
            }

            return this.createArtifactWithRefs(groupId, xRegistryArtifactType, xRegistryArtifactId, xRegistryVersion, ifExists,
                    canonical, xRegistryDescription, xRegistryDescriptionEncoded, xRegistryName,
                    xRegistryNameEncoded, xRegistryContentHash, xRegistryHashAlgorithm, xRegistryArtifactBranches, content, data.getReferences());
        } catch (KeyManagementException kme) {
            throw new RuntimeException(kme);
        } catch (NoSuchAlgorithmException nsae) {
            throw new RuntimeException(nsae);
        } finally {
            if (client != null) {
                client.close();
            }
        }
    }

    public enum RegistryHashAlgorithm {
        SHA256,
        MD5
    }

    /**
     * Return an InputStream for the resource to be downloaded
     *
     * @param url
     */
    private InputStream fetchContentFromURL(Client client, URI url) {
        try {
            // 1. Registry issues HTTP HEAD request to the target URL.
            List<Object> contentLengthHeaders = client
                    .target(url)
                    .request()
                    .head()
                    .getHeaders()
                    .get("Content-Length");

            if (contentLengthHeaders == null || contentLengthHeaders.size() < 1) {
                throw new BadRequestException("Requested resource URL does not provide 'Content-Length' in the headers");
            }

            // 2. According to HTTP specification, target server must return Content-Length header.
            int contentLength = Integer.parseInt(contentLengthHeaders.get(0).toString());

            // 3. Registry analyzes value of Content-Length to check if file with declared size could be processed securely.
            if (contentLength > restConfig.getDownloadMaxSize()) {
                throw new BadRequestException("Requested resource is bigger than " + restConfig.getDownloadMaxSize() + " and cannot be downloaded.");
            }

            if (contentLength <= 0) {
                throw new BadRequestException("Requested resource URL is providing 'Content-Length' <= 0.");
            }

            // 4. Finally, registry issues HTTP GET to the target URL and fetches only amount of bytes specified by HTTP HEAD from step 1.
            return new BufferedInputStream(client
                    .target(url)
                    .request()
                    .get()
                    .readEntity(InputStream.class), contentLength);
        } catch (BadRequestException bre) {
            throw bre;
        } catch (Exception e) {
            throw new BadRequestException("Errors downloading the artifact content.", e);
        }
    }

    /**
     * Creates an artifact with references.  Shared by both variants of createArtifact.
     *
     * @param groupId
     * @param xRegistryArtifactType
     * @param xRegistryArtifactId
     * @param xRegistryVersion
     * @param ifExists
     * @param canonical
     * @param xRegistryDescription
     * @param xRegistryDescriptionEncoded
     * @param xRegistryName
     * @param xRegistryNameEncoded
     * @param xRegistryContentHash
     * @param xRegistryHashAlgorithm
     * @param data
     * @param references
     */
    @SuppressWarnings("deprecation")
    private ArtifactMetaData createArtifactWithRefs(String groupId, String xRegistryArtifactType, String xRegistryArtifactId,
                                                    String xRegistryVersion, IfExists ifExists, Boolean canonical,
                                                    String xRegistryDescription, String xRegistryDescriptionEncoded,
                                                    String xRegistryName, String xRegistryNameEncoded,
                                                    String xRegistryContentHash, String xRegistryHashAlgorithm,
                                                    List<String> artifactBranches,
                                                    InputStream data, List<ArtifactReference> references) {

        requireParameter("groupId", groupId);

        maxOneOf("X-Registry-Name", xRegistryName, "X-Registry-Name-Encoded", xRegistryNameEncoded);
        maxOneOf("X-Registry-Description", xRegistryDescription, "X-Registry-Description-Encoded", xRegistryDescriptionEncoded);

        String artifactName = getOneOf(xRegistryName, decode(xRegistryNameEncoded));
        String artifactDescription = getOneOf(xRegistryDescription, decode(xRegistryDescriptionEncoded));

        if (!ArtifactIdValidator.isGroupIdAllowed(groupId)) {
            throw new InvalidGroupIdException(ArtifactIdValidator.GROUP_ID_ERROR_MESSAGE);
        }

        // TODO do something with the optional user-provided Version

        ContentHandle content = ContentHandle.create(data);
        if (content.bytes().length == 0) {
            throw new BadRequestException(EMPTY_CONTENT_ERROR_MESSAGE);
        }

        // Mitigation for MITM attacks, verify that the artifact is the expected one
        if (xRegistryContentHash != null) {
            String calculatedSha = null;
            try {
                RegistryHashAlgorithm algorithm = (xRegistryHashAlgorithm == null) ? RegistryHashAlgorithm.SHA256 : RegistryHashAlgorithm.valueOf(xRegistryHashAlgorithm);
                switch (algorithm) {
                    case MD5:
                        calculatedSha = Hashing.md5().hashString(content.content(), StandardCharsets.UTF_8).toString();
                        break;
                    case SHA256:
                        calculatedSha = Hashing.sha256().hashString(content.content(), StandardCharsets.UTF_8).toString();
                        break;
                }
            } catch (Exception e) {
                throw new BadRequestException("Requested hash algorithm not supported");
            }

            if (!calculatedSha.equals(xRegistryContentHash.trim())) {
                throw new BadRequestException("Provided Artifact Hash doesn't match with the content");
            }
        }

        final boolean fcanonical = canonical == null ? Boolean.FALSE : canonical;

        String ct = getContentType();
        try {
            String artifactId = xRegistryArtifactId;

            if (artifactId == null || artifactId.trim().isEmpty()) {
                artifactId = idGenerator.generate();
            } else if (!ArtifactIdValidator.isArtifactIdAllowed(artifactId)) {
                throw new InvalidArtifactIdException(ArtifactIdValidator.ARTIFACT_ID_ERROR_MESSAGE);
            }
            if (ContentTypeUtil.isApplicationYaml(ct) ||
                    (ContentTypeUtil.isApplicationCreateExtended(ct) && ContentTypeUtil.isParsableYaml(content))) {
                content = ContentTypeUtil.yamlToJson(content);
            }

            String artifactType = ArtifactTypeUtil.determineArtifactType(content, xRegistryArtifactType, ct, factory.getAllArtifactTypes());

            final List<ArtifactReferenceDto> referencesAsDtos = toReferenceDtos(references);

            //Try to resolve the new artifact references and the nested ones (if any)
            final Map<String, ContentHandle> resolvedReferences = storage.resolveReferences(referencesAsDtos);

            rulesService.applyRules(new GroupId(groupId).getRawGroupIdWithNull(), artifactId, artifactType, content, RuleApplicationType.CREATE, references, resolvedReferences);

            final String finalArtifactId = artifactId;
            EditableArtifactMetaDataDto metaData = getEditableMetaData(artifactName, artifactDescription);

            ArtifactMetaDataDto amd = storage.createArtifactWithMetadata(new GroupId(groupId).getRawGroupIdWithNull(), artifactId, xRegistryVersion, artifactType, content, metaData, referencesAsDtos);

            for (String rawBranchId : normalizeMultiValuedHeader(artifactBranches)) {
                storage.createOrUpdateArtifactBranch(new GAV(groupId, artifactId, amd.getVersion()), new BranchId(rawBranchId));
            }

            return V3ApiUtil.dtoToMetaData(new GroupId(groupId).getRawGroupIdWithNull(), finalArtifactId, artifactType, amd);
        } catch (ArtifactAlreadyExistsException ex) {
            return handleIfExists(groupId, xRegistryArtifactId, xRegistryVersion, ifExists, artifactName, artifactDescription, content, ct, fcanonical, references);
        }
    }


    @Override
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Read)
    public VersionSearchResults listArtifactVersions(String groupId, String artifactId, BigInteger offset, BigInteger limit) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);

        if (offset == null) {
            offset = BigInteger.valueOf(0);
        }
        if (limit == null) {
            limit = BigInteger.valueOf(20);
        }

        VersionSearchResultsDto resultsDto = storage.searchVersions(new GroupId(groupId).getRawGroupIdWithNull(), artifactId, offset.intValue(), limit.intValue());
        return V3ApiUtil.dtoToSearchResults(resultsDto);
    }


    @Override
    @Audited(extractParameters = {"0", KEY_GROUP_ID, "1", KEY_ARTIFACT_ID, "2", KEY_VERSION, "3", KEY_NAME,
            "4", KEY_DESCRIPTION, "5", KEY_DESCRIPTION_ENCODED, "6", KEY_NAME_ENCODED, "7", "branch"}) // TODO
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public VersionMetaData createArtifactVersion(String groupId, String artifactId, String xRegistryVersion, String xRegistryName,
                                                 String xRegistryDescription, String xRegistryDescriptionEncoded, String xRegistryNameEncoded, List<String> xRegistryArtifactBranches,
                                                 InputStream data) {
        return this.createArtifactVersionWithRefs(groupId, artifactId, xRegistryVersion, xRegistryName, xRegistryDescription, xRegistryDescriptionEncoded, xRegistryNameEncoded, xRegistryArtifactBranches, data, Collections.emptyList());
    }


    @Override
    @Audited(extractParameters = {"0", KEY_GROUP_ID, "1", KEY_ARTIFACT_ID, "2", KEY_VERSION, "3", KEY_NAME,
            "4", KEY_DESCRIPTION, "5", KEY_DESCRIPTION_ENCODED, "6", KEY_NAME_ENCODED, "7", "branch"}) // TODO
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public VersionMetaData createArtifactVersion(String groupId, String artifactId, String xRegistryVersion, String xRegistryName,
                                                 String xRegistryDescription, String xRegistryDescriptionEncoded, String xRegistryNameEncoded, List<String> xRegistryArtifactBranches,
                                                 ArtifactContent data) {
        requireParameter("content", data.getContent());
        return this.createArtifactVersionWithRefs(groupId, artifactId, xRegistryVersion, xRegistryName, xRegistryDescription, xRegistryDescriptionEncoded, xRegistryNameEncoded, xRegistryArtifactBranches, IoUtil.toStream(data.getContent()), data.getReferences());
    }


    @Override
    @Audited(extractParameters = {"0", KEY_GROUP_ID, "1", KEY_ARTIFACT_ID})
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Read)
    public List<ArtifactBranch> listArtifactBranches(String groupId, String artifactId) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);

        return storage.getArtifactBranches(new GA(groupId, artifactId))
                .entrySet()
                .stream()
                .map(e -> {
                    return ArtifactBranch.builder()
                            .groupId(groupId)
                            .artifactId(artifactId)
                            .branchId(e.getKey().getRawBranchId())
                            .versions(
                                    e.getValue()
                                            .stream()
                                            .map(GAV::getRawVersionId)
                                            .collect(toList())
                            )
                            .build();
                })
                .collect(toList());
    }


    @Override
    @Audited(extractParameters = {"0", KEY_GROUP_ID, "1", KEY_ARTIFACT_ID, "2", "branch_id"}) // TODO
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Read)
    public ArtifactBranch getArtifactBranch(String groupId, String artifactId, String rawBranchId) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);
        requireParameter("branchId", rawBranchId);

        return ArtifactBranch.builder()
                .groupId(groupId)
                .artifactId(artifactId)
                .branchId(rawBranchId)
                .versions(
                        storage.getArtifactBranch(new GA(groupId, artifactId), new BranchId(rawBranchId), ArtifactRetrievalBehavior.DEFAULT)
                                .stream()
                                .map(GAV::getRawVersionId)
                                .collect(toList())
                )
                .build();

    }


    /**
     * @see io.apicurio.registry.rest.v3.GroupsResource#createOrUpdateArtifactBranch(java.lang.String, java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    @Audited(extractParameters = {"0", KEY_GROUP_ID, "1", KEY_ARTIFACT_ID, "2", "branch_id", "3", KEY_VERSION}) // TODO
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public ArtifactBranch createOrUpdateArtifactBranch(String groupId, String artifactId, String rawBranchId, String version) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);
        requireParameter("branchId", rawBranchId);
        requireParameter("version", version);

        var gav = new GAV(groupId, artifactId, version);
        var branchId = new BranchId(rawBranchId);

        storage.createOrUpdateArtifactBranch(gav, branchId);

        return ArtifactBranch.builder()
                .groupId(gav.getRawGroupIdWithDefaultString())
                .artifactId(gav.getRawArtifactId())
                .branchId(branchId.getRawBranchId())
                .versions(
                        storage.getArtifactBranch(gav, branchId, ArtifactRetrievalBehavior.DEFAULT)
                                .stream()
                                .map(GAV::getRawVersionId)
                                .collect(toList())
                )
                .build();
    }


    @Override
    @Audited(extractParameters = {"0", KEY_GROUP_ID, "1", KEY_ARTIFACT_ID, "2", "branch_id", "3", "branch"}) // TODO
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public ArtifactBranch createOrReplaceArtifactBranch(String groupId, String artifactId, String rawBranchId, ArtifactBranch branch) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);
        requireParameter("branchId", rawBranchId);
        requireParameter("branch", branch);

        var ga = new GA(groupId, artifactId);
        var branchId = new BranchId(rawBranchId);
        var versions = branch.getVersions()
                .stream()
                .map(VersionId::new)
                .collect(toList());

        storage.createOrReplaceArtifactBranch(ga, branchId, versions);

        return ArtifactBranch.builder()
                .groupId(ga.getRawGroupIdWithDefaultString())
                .artifactId(ga.getRawArtifactId())
                .branchId(branchId.getRawBranchId())
                .versions(
                        storage.getArtifactBranch(ga, branchId, ArtifactRetrievalBehavior.DEFAULT)
                                .stream()
                                .map(GAV::getRawVersionId)
                                .collect(toList())
                )
                .build();
    }


    @Override
    @Audited(extractParameters = {"0", KEY_GROUP_ID, "1", KEY_ARTIFACT_ID, "2", "branch_id"}) // TODO
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Write)
    public void deleteArtifactBranch(String groupId, String artifactId, String rawBranchId) {
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);
        requireParameter("branchId", rawBranchId);

        storage.deleteArtifactBranch(new GA(groupId, artifactId), new BranchId(rawBranchId));
    }


    // ========== Not endpoints: ==========

    /**
     * Creates an artifact version with references.  Shared implementation for both variants of createArtifactVersion.
     *
     * @param groupId
     * @param artifactId
     * @param xRegistryVersion
     * @param xRegistryName
     * @param xRegistryDescription
     * @param xRegistryDescriptionEncoded
     * @param xRegistryNameEncoded
     * @param data
     * @param references
     */
    private VersionMetaData createArtifactVersionWithRefs(String groupId, String artifactId, String xRegistryVersion, String xRegistryName,
                                                          String xRegistryDescription, String xRegistryDescriptionEncoded, String xRegistryNameEncoded, List<String> artifactBranches,
                                                          InputStream data, List<ArtifactReference> references) {
        // TODO do something with the user-provided version info
        requireParameter("groupId", groupId);
        requireParameter("artifactId", artifactId);

        maxOneOf("X-Registry-Name", xRegistryName, "X-Registry-Name-Encoded", xRegistryNameEncoded);
        maxOneOf("X-Registry-Description", xRegistryDescription, "X-Registry-Description-Encoded", xRegistryDescriptionEncoded);

        String artifactName = getOneOf(xRegistryName, decode(xRegistryNameEncoded));
        String artifactDescription = getOneOf(xRegistryDescription, decode(xRegistryDescriptionEncoded));

        ContentHandle content = ContentHandle.create(data);
        if (content.bytes().length == 0) {
            throw new BadRequestException(EMPTY_CONTENT_ERROR_MESSAGE);
        }
        String ct = getContentType();
        if (ContentTypeUtil.isApplicationYaml(ct)) {
            content = ContentTypeUtil.yamlToJson(content);
        }

        //Transform the given references into dtos and set the contentId, this will also detect if any of the passed references does not exist.
        final List<ArtifactReferenceDto> referencesAsDtos = toReferenceDtos(references);

        //Try to resolve the new artifact references and the nested ones (if any)
        final Map<String, ContentHandle> resolvedReferences = storage.resolveReferences(referencesAsDtos);

        String artifactType = lookupArtifactType(groupId, artifactId);
        rulesService.applyRules(new GroupId(groupId).getRawGroupIdWithNull(), artifactId, artifactType, content, RuleApplicationType.UPDATE, references, resolvedReferences);
        EditableArtifactMetaDataDto metaData = getEditableMetaData(artifactName, artifactDescription);
        ArtifactMetaDataDto amd = storage.updateArtifactWithMetadata(new GroupId(groupId).getRawGroupIdWithNull(), artifactId, xRegistryVersion, artifactType, content, metaData, referencesAsDtos);

        for (String rawBranchId : normalizeMultiValuedHeader(artifactBranches)) {
            storage.createOrUpdateArtifactBranch(new GAV(groupId, artifactId, amd.getVersion()), new BranchId(rawBranchId));
        }

        return V3ApiUtil.dtoToVersionMetaData(new GroupId(groupId).getRawGroupIdWithNull(), artifactId, artifactType, amd);
    }

    /**
     * Check to see if the artifact version is deprecated.
     *
     * @param stateSupplier
     * @param groupId
     * @param artifactId
     * @param version
     * @param builder
     */
    private void checkIfDeprecated(Supplier<ArtifactState> stateSupplier, String groupId, String artifactId, String version, Response.ResponseBuilder builder) {
        HeadersHack.checkIfDeprecated(stateSupplier, groupId, artifactId, version, builder);
    }

    /**
     * Looks up the artifact type for the given artifact.
     *
     * @param groupId
     * @param artifactId
     */
    private String lookupArtifactType(String groupId, String artifactId) {
        return storage.getArtifactMetaData(new GroupId(groupId).getRawGroupIdWithNull(), artifactId).getType();
    }

    /**
     * Make sure this is ONLY used when request instance is active.
     * e.g. in actual http request
     */
    private String getContentType() {
        return request.getContentType();
    }

    private static final void requireParameter(String parameterName, Object parameterValue) {
        if (parameterValue == null) {
            throw new MissingRequiredParameterException(parameterName);
        }
    }

    private static void maxOneOf(String parameterOneName, Object parameterOneValue, String parameterTwoName, Object parameterTwoValue) {
        if (parameterOneValue != null && parameterTwoValue != null) {
            throw new ParametersConflictException(parameterOneName, parameterTwoName);
        }
    }

    private static <T> T getOneOf(T parameterOneValue, T parameterTwoValue) {
        return parameterOneValue != null ? parameterOneValue : parameterTwoValue;
    }

    private static String decode(String encoded) {
        if (encoded == null) {
            return null;
        }
        return new String(Base64.decode(encoded));
    }

    private ArtifactMetaData handleIfExists(String groupId, String artifactId, String version, IfExists ifExists,
                                            String artifactName, String artifactDescription, ContentHandle content,
                                            String contentType, boolean canonical, List<ArtifactReference> references) {
        final ArtifactMetaData artifactMetaData = getArtifactMetaData(groupId, artifactId);
        if (ifExists == null) {
            ifExists = IfExists.FAIL;
        }

        switch (ifExists) {
            case UPDATE:
                return updateArtifactInternal(groupId, artifactId, version, artifactName, artifactDescription, List.of(), content, contentType, references);
            case RETURN:
                return artifactMetaData;
            case RETURN_OR_UPDATE:
                return handleIfExistsReturnOrUpdate(groupId, artifactId, version, artifactName, artifactDescription, content, contentType, canonical, references);
            default:
                throw new ArtifactAlreadyExistsException(groupId, artifactId);
        }
    }

    private ArtifactMetaData handleIfExistsReturnOrUpdate(String groupId, String artifactId, String version,
                                                          String artifactName, String artifactDescription,
                                                          ContentHandle content, String contentType, boolean canonical, List<ArtifactReference> references) {
        try {
            ArtifactVersionMetaDataDto mdDto = this.storage.getArtifactVersionMetaData(new GroupId(groupId).getRawGroupIdWithNull(), artifactId, canonical, content, toReferenceDtos(references));
            ArtifactMetaData md = V3ApiUtil.dtoToMetaData(new GroupId(groupId).getRawGroupIdWithNull(), artifactId, null, mdDto);
            return md;
        } catch (ArtifactNotFoundException nfe) {
            // This is OK - we'll update the artifact if there is no matching content already there.
        }
        return updateArtifactInternal(groupId, artifactId, version, artifactName, artifactDescription, List.of(), content, contentType, references);
    }


    private ArtifactMetaData updateArtifactInternal(String groupId, String artifactId, String version,
                                            String name, String description,
                                            List<String> artifactBranches,
                                            ContentHandle content, String contentType, List<ArtifactReference> references) {

        if (ContentTypeUtil.isApplicationYaml(contentType)) {
            content = ContentTypeUtil.yamlToJson(content);
        }

        String artifactType = lookupArtifactType(groupId, artifactId);

        //Transform the given references into dtos and set the contentId, this will also detect if any of the passed references does not exist.
        final List<ArtifactReferenceDto> referencesAsDtos = toReferenceDtos(references);

        final Map<String, ContentHandle> resolvedReferences = storage.resolveReferences(referencesAsDtos);

        rulesService.applyRules(new GroupId(groupId).getRawGroupIdWithNull(), artifactId, artifactType, content,
                RuleApplicationType.UPDATE, references, resolvedReferences);
        EditableArtifactMetaDataDto metaData = getEditableMetaData(name, description);
        ArtifactMetaDataDto dto = storage.updateArtifactWithMetadata(new GroupId(groupId).getRawGroupIdWithNull(), artifactId, version, artifactType, content, metaData, referencesAsDtos);

        for (String rawBranchId : normalizeMultiValuedHeader(artifactBranches)) {
            storage.createOrUpdateArtifactBranch(new GAV(groupId, artifactId, dto.getVersion()), new BranchId(rawBranchId));
        }

        return V3ApiUtil.dtoToMetaData(new GroupId(groupId).getRawGroupIdWithNull(), artifactId, artifactType, dto);
    }


    private EditableArtifactMetaDataDto getEditableMetaData(String name, String description) {
        if (name != null || description != null) {
            return new EditableArtifactMetaDataDto(name, description, null);
        }
        return null;
    }

    private List<ArtifactReferenceDto> toReferenceDtos(List<ArtifactReference> references) {
        if (references == null) {
            references = Collections.emptyList();
        }
        return references.stream()
                .peek(r -> r.setGroupId(new GroupId(r.getGroupId()).getRawGroupIdWithNull()))
                .map(V3ApiUtil::referenceToDto)
                .collect(toList());
    }


    private List<String> normalizeMultiValuedHeader(List<String> value) {
        return value.stream().flatMap(v -> Arrays.stream(v.split(",")).map(String::strip)).collect(toList());
    }
}
