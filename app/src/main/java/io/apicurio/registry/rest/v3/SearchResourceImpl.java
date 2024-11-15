package io.apicurio.registry.rest.v3;

import io.apicurio.common.apps.logging.Logged;
import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.model.GroupId;
import io.apicurio.registry.rest.v3.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.v3.beans.ArtifactSortBy;
import io.apicurio.registry.rest.v3.beans.GroupSearchResults;
import io.apicurio.registry.rest.v3.beans.GroupSortBy;
import io.apicurio.registry.rest.v3.beans.SortOrder;
import io.apicurio.registry.rest.v3.beans.VersionSearchResults;
import io.apicurio.registry.rest.v3.beans.VersionSortBy;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactSearchResultsDto;
import io.apicurio.registry.storage.dto.GroupSearchResultsDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.VersionSearchResultsDto;
import io.apicurio.registry.storage.impl.sql.RegistryStorageContentUtils;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.types.VersionState;
import io.apicurio.registry.utils.StringUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptors;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Context;

import java.io.InputStream;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ApplicationScoped
@Interceptors({ ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class })
@Logged
public class SearchResourceImpl implements SearchResource {

    private static final String EMPTY_CONTENT_ERROR_MESSAGE = "Empty content is not allowed.";
    private static final String CANONICAL_QUERY_PARAM_ERROR_MESSAGE = "When setting 'canonical' to 'true', the 'artifactType' query parameter is also required.";

    @Inject
    @Current
    RegistryStorage storage;

    @Context
    HttpServletRequest request;

    @Inject
    RegistryStorageContentUtils contentUtils;

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public ArtifactSearchResults searchArtifacts(String name, BigInteger offset, BigInteger limit,
            SortOrder order, ArtifactSortBy orderby, List<String> labels, String description, String groupId,
            Long globalId, Long contentId, String artifactId, String artifactType) {
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
        final OrderDirection oDir = (order == null || order == SortOrder.asc) ? OrderDirection.asc
            : OrderDirection.desc;

        Set<SearchFilter> filters = new HashSet<SearchFilter>();
        if (!StringUtil.isEmpty(name)) {
            filters.add(SearchFilter.ofName(name));
        }
        if (!StringUtil.isEmpty(description)) {
            filters.add(SearchFilter.ofDescription(description));
        }
        if (!StringUtil.isEmpty(groupId)) {
            filters.add(SearchFilter.ofGroupId(new GroupId(groupId).getRawGroupIdWithNull()));
        }
        if (!StringUtil.isEmpty(artifactId)) {
            filters.add(SearchFilter.ofArtifactId(artifactId));
        }
        if (!StringUtil.isEmpty(artifactType)) {
            filters.add(SearchFilter.ofArtifactType(artifactType));
        }

        if (labels != null && !labels.isEmpty()) {
            labels.stream().map(prop -> {
                int delimiterIndex = prop.indexOf(":");
                String labelKey;
                String labelValue;
                if (delimiterIndex == 0) {
                    throw new BadRequestException(
                            "label search filter wrong format, missing left side of ':' delimiter");
                }
                if (delimiterIndex == (prop.length() - 1)) {
                    throw new BadRequestException(
                            "label search filter wrong format, missing right side of ':' delimiter");
                }
                if (delimiterIndex < 0) {
                    labelKey = prop;
                    labelValue = null;
                } else {
                    labelKey = prop.substring(0, delimiterIndex);
                    labelValue = prop.substring(delimiterIndex + 1);
                }
                return SearchFilter.ofLabel(labelKey, labelValue);
            }).forEach(filters::add);
        }
        if (globalId != null && globalId > 0) {
            filters.add(SearchFilter.ofGlobalId(globalId));
        }
        if (contentId != null && contentId > 0) {
            filters.add(SearchFilter.ofContentId(contentId));
        }

        ArtifactSearchResultsDto results = storage.searchArtifacts(filters, oBy, oDir, offset.intValue(),
                limit.intValue());
        return V3ApiUtil.dtoToSearchResults(results);
    }

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public ArtifactSearchResults searchArtifactsByContent(Boolean canonical, String artifactType,
            String groupId, BigInteger offset, BigInteger limit, SortOrder order, ArtifactSortBy orderby,
            InputStream data) {

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

        if (canonical == null) {
            canonical = Boolean.FALSE;
        }
        ContentHandle content = ContentHandle.create(data);
        if (content.bytes().length == 0) {
            throw new BadRequestException(EMPTY_CONTENT_ERROR_MESSAGE);
        }
        String ct = getContentType();
        TypedContent typedContent = TypedContent.create(content, ct);

        Set<SearchFilter> filters = new HashSet<SearchFilter>();
        if (canonical && artifactType != null) {
            String canonicalHash = contentUtils.getCanonicalContentHash(typedContent, artifactType, null,
                    null);
            filters.add(SearchFilter.ofCanonicalHash(canonicalHash));
        } else if (!canonical) {
            String contentHash = content.getSha256Hash();
            filters.add(SearchFilter.ofContentHash(contentHash));
        } else {
            throw new BadRequestException(CANONICAL_QUERY_PARAM_ERROR_MESSAGE);
        }
        if (!StringUtil.isEmpty(groupId)) {
            filters.add(SearchFilter.ofGroupId(new GroupId(groupId).getRawGroupIdWithNull()));
        }

        ArtifactSearchResultsDto results = storage.searchArtifacts(filters, oBy, oDir, offset.intValue(),
                limit.intValue());
        return V3ApiUtil.dtoToSearchResults(results);
    }

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public GroupSearchResults searchGroups(BigInteger offset, BigInteger limit, SortOrder order,
            GroupSortBy orderby, List<String> labels, String description, String groupId) {
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

        Set<SearchFilter> filters = new HashSet<SearchFilter>();
        if (!StringUtil.isEmpty(groupId)) {
            filters.add(SearchFilter.ofGroupId(groupId));
        }
        if (!StringUtil.isEmpty(description)) {
            filters.add(SearchFilter.ofDescription(description));
        }

        if (labels != null && !labels.isEmpty()) {
            labels.stream().map(prop -> {
                int delimiterIndex = prop.indexOf(":");
                String labelKey;
                String labelValue;
                if (delimiterIndex == 0) {
                    throw new BadRequestException(
                            "label search filter wrong formatted, missing left side of ':' delimiter");
                }
                if (delimiterIndex == (prop.length() - 1)) {
                    throw new BadRequestException(
                            "label search filter wrong formatted, missing right side of ':' delimiter");
                }
                if (delimiterIndex < 0) {
                    labelKey = prop;
                    labelValue = null;
                } else {
                    labelKey = prop.substring(0, delimiterIndex);
                    labelValue = prop.substring(delimiterIndex + 1);
                }
                return SearchFilter.ofLabel(labelKey, labelValue);
            }).forEach(filters::add);
        }

        GroupSearchResultsDto results = storage.searchGroups(filters, oBy, oDir, offset.intValue(),
                limit.intValue());
        return V3ApiUtil.dtoToSearchResults(results);
    }

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public VersionSearchResults searchVersions(String version, BigInteger offset, BigInteger limit,
            SortOrder order, VersionSortBy orderby, List<String> labels, String description, String groupId,
            Long globalId, Long contentId, String artifactId, String name, VersionState state,
            String artifactType) {
        if (orderby == null) {
            orderby = VersionSortBy.globalId;
        }
        if (offset == null) {
            offset = BigInteger.valueOf(0);
        }
        if (limit == null) {
            limit = BigInteger.valueOf(20);
        }

        final OrderBy oBy = OrderBy.valueOf(orderby.name());
        final OrderDirection oDir = (order == null || order == SortOrder.asc) ? OrderDirection.asc
            : OrderDirection.desc;

        Set<SearchFilter> filters = new HashSet<SearchFilter>();
        if (!StringUtil.isEmpty(groupId)) {
            filters.add(SearchFilter.ofGroupId(new GroupId(groupId).getRawGroupIdWithNull()));
        }
        if (!StringUtil.isEmpty(artifactId)) {
            filters.add(SearchFilter.ofArtifactId(artifactId));
        }
        if (!StringUtil.isEmpty(version)) {
            filters.add(SearchFilter.ofVersion(version));
        }
        if (!StringUtil.isEmpty(name)) {
            filters.add(SearchFilter.ofName(name));
        }
        if (!StringUtil.isEmpty(description)) {
            filters.add(SearchFilter.ofDescription(description));
        }
        if (!StringUtil.isEmpty(artifactType)) {
            filters.add(SearchFilter.ofArtifactType(artifactType));
        }
        if (labels != null && !labels.isEmpty()) {
            labels.stream().map(prop -> {
                int delimiterIndex = prop.indexOf(":");
                String labelKey;
                String labelValue;
                if (delimiterIndex == 0) {
                    throw new BadRequestException(
                            "label search filter wrong formatted, missing left side of ':' delimiter");
                }
                if (delimiterIndex == (prop.length() - 1)) {
                    throw new BadRequestException(
                            "label search filter wrong formatted, missing right side of ':' delimiter");
                }
                if (delimiterIndex < 0) {
                    labelKey = prop;
                    labelValue = null;
                } else {
                    labelKey = prop.substring(0, delimiterIndex);
                    labelValue = prop.substring(delimiterIndex + 1);
                }
                return SearchFilter.ofLabel(labelKey, labelValue);
            }).forEach(filters::add);
        }
        if (globalId != null && globalId > 0) {
            filters.add(SearchFilter.ofGlobalId(globalId));
        }
        if (contentId != null && contentId > 0) {
            filters.add(SearchFilter.ofContentId(contentId));
        }
        if (state != null) {
            filters.add(SearchFilter.ofState(state));
        }

        VersionSearchResultsDto results = storage.searchVersions(filters, oBy, oDir, offset.intValue(),
                limit.intValue());
        return V3ApiUtil.dtoToSearchResults(results);
    }

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public VersionSearchResults searchVersionsByContent(Boolean canonical, String artifactType,
            BigInteger offset, BigInteger limit, SortOrder order, VersionSortBy orderby, String groupId,
            String artifactId, InputStream data) {

        if (orderby == null) {
            orderby = VersionSortBy.globalId;
        }
        if (offset == null) {
            offset = BigInteger.valueOf(0);
        }
        if (limit == null) {
            limit = BigInteger.valueOf(20);
        }

        final OrderBy oBy = OrderBy.valueOf(orderby.name());
        final OrderDirection oDir = (order == null || order == SortOrder.asc) ? OrderDirection.asc
            : OrderDirection.desc;

        Set<SearchFilter> filters = new HashSet<SearchFilter>();
        if (!StringUtil.isEmpty(groupId)) {
            filters.add(SearchFilter.ofGroupId(new GroupId(groupId).getRawGroupIdWithNull()));
        }
        if (!StringUtil.isEmpty(artifactId)) {
            filters.add(SearchFilter.ofArtifactId(artifactId));
        }

        if (canonical == null) {
            canonical = Boolean.FALSE;
        }
        ContentHandle content = ContentHandle.create(data);
        if (content.bytes().length == 0) {
            throw new BadRequestException(EMPTY_CONTENT_ERROR_MESSAGE);
        }
        String ct = getContentType();
        TypedContent typedContent = TypedContent.create(content, ct);

        if (canonical && artifactType != null) {
            String canonicalHash = contentUtils.getCanonicalContentHash(typedContent, artifactType, null,
                    null);
            filters.add(SearchFilter.ofCanonicalHash(canonicalHash));
        } else if (!canonical) {
            String contentHash = content.getSha256Hash();
            filters.add(SearchFilter.ofContentHash(contentHash));
        } else {
            throw new BadRequestException(CANONICAL_QUERY_PARAM_ERROR_MESSAGE);
        }

        VersionSearchResultsDto results = storage.searchVersions(filters, oBy, oDir, offset.intValue(),
                limit.intValue());
        return V3ApiUtil.dtoToSearchResults(results);
    }

    /**
     * Make sure this is ONLY used when request instance is active. e.g. in actual http request
     */
    private String getContentType() {
        return request.getContentType();
    }
}
