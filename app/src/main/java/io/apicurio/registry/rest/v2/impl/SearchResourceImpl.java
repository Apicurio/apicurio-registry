package io.apicurio.registry.rest.v2.impl;

import io.apicurio.registry.rest.v2.SearchResource;

import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.content.util.ContentTypeUtil;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.rest.v2.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.v2.beans.SortBy;
import io.apicurio.registry.rest.v2.beans.SortOrder;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactSearchResultsDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProvider;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import io.apicurio.registry.utils.StringUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptors;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Context;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;

import java.io.InputStream;
import java.math.BigInteger;
import java.util.Collections;
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
    Logger log;

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    ArtifactTypeUtilProviderFactory factory;

    @Context
    HttpServletRequest request;

    /**
     * @see io.apicurio.registry.rest.v2.SearchResource#searchArtifacts(String, BigInteger, BigInteger,
     *      SortOrder, SortBy, List, List, String, String, Long, Long)
     */
    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public ArtifactSearchResults searchArtifacts(String name, BigInteger offset, BigInteger limit,
            SortOrder order, SortBy orderby, List<String> labels, List<String> properties, String description,
            String group, Long globalId, Long contentId) {
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
        final OrderDirection oDir = order == null || order == SortOrder.asc ? OrderDirection.asc
            : OrderDirection.desc;

        Set<SearchFilter> filters = new HashSet<SearchFilter>();
        if (!StringUtil.isEmpty(name)) {
            filters.add(SearchFilter.ofName(name));
        }
        if (!StringUtil.isEmpty(description)) {
            filters.add(SearchFilter.ofDescription(description));
        }
        if (!StringUtil.isEmpty(group)) {
            filters.add(SearchFilter.ofGroupId(gidOrNull(group)));
        }

        if (properties != null && !properties.isEmpty()) {
            properties.stream().map(prop -> {
                int delimiterIndex = prop.indexOf(":");
                String propertyKey;
                String propertyValue;
                if (delimiterIndex == 0) {
                    throw new BadRequestException(
                            "property search filter incorrectly formatted, missing left side of ':' delimiter");
                }
                if (delimiterIndex == (prop.length() - 1)) {
                    throw new BadRequestException(
                            "property search filter incorrectly formatted, missing right side of ':' delimiter");
                }
                if (delimiterIndex < 0) {
                    propertyKey = prop;
                    propertyValue = null;
                } else {
                    propertyKey = prop.substring(0, delimiterIndex);
                    propertyValue = prop.substring(delimiterIndex + 1);
                }
                return SearchFilter.ofLabel(propertyKey, propertyValue);
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
        return V2ApiUtil.dtoToSearchResults(results);
    }

    /**
     * @see io.apicurio.registry.rest.v2.SearchResource#searchArtifactsByContent(Boolean, String, BigInteger,
     *      BigInteger, SortOrder, SortBy, InputStream)
     */
    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public ArtifactSearchResults searchArtifactsByContent(Boolean canonical, String artifactType,
            BigInteger offset, BigInteger limit, SortOrder order, SortBy orderby, InputStream data) {

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
        final OrderDirection oDir = order == null || order == SortOrder.asc ? OrderDirection.asc
            : OrderDirection.desc;

        if (canonical == null) {
            canonical = Boolean.FALSE;
        }
        ContentHandle content = ContentHandle.create(data);
        String contentType = getContentType();
        if (content.bytes().length == 0) {
            throw new BadRequestException(EMPTY_CONTENT_ERROR_MESSAGE);
        }
        if (ContentTypeUtil.isApplicationYaml(contentType)) {
            content = ContentTypeUtil.yamlToJson(content);
            contentType = ContentTypes.APPLICATION_JSON;
        }
        TypedContent typedContent = TypedContent.create(content, contentType);

        Set<SearchFilter> filters = new HashSet<SearchFilter>();
        if (canonical && artifactType != null) {
            String canonicalHash = sha256Hash(canonicalizeContent(artifactType, typedContent).getContent());
            filters.add(SearchFilter.ofCanonicalHash(canonicalHash));
        } else if (!canonical) {
            String contentHash = sha256Hash(content);
            filters.add(SearchFilter.ofContentHash(contentHash));
        } else {
            throw new BadRequestException(CANONICAL_QUERY_PARAM_ERROR_MESSAGE);
        }
        ArtifactSearchResultsDto results = storage.searchArtifacts(filters, oBy, oDir, offset.intValue(),
                limit.intValue());
        return V2ApiUtil.dtoToSearchResults(results);
    }

    /**
     * Make sure this is ONLY used when request instance is active. e.g. in actual http request
     */
    private String getContentType() {
        return request.getContentType();
    }

    private String sha256Hash(ContentHandle chandle) {
        // TODO: ContentHandle::getSha256Hash()
        return DigestUtils.sha256Hex(chandle.bytes());
    }

    private String gidOrNull(String groupId) {
        // TODO: Use io.apicurio.registry.model.GroupId
        if ("default".equalsIgnoreCase(groupId)) {
            return null;
        }
        return groupId;
    }

    protected TypedContent canonicalizeContent(String artifactType, TypedContent content) {
        try {
            ArtifactTypeUtilProvider provider = factory.getArtifactTypeProvider(artifactType);
            ContentCanonicalizer canonicalizer = provider.getContentCanonicalizer();
            TypedContent canonicalContent = canonicalizer.canonicalize(content, Collections.emptyMap());
            return canonicalContent;
        } catch (Exception e) {
            log.debug("Failed to canonicalize content of type: {}", artifactType);
            return content;
        }
    }
}
