/*
 * Copyright 2021 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.rest.v2;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.interceptor.Interceptors;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.core.Context;

import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;

import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.common.apps.logging.Logged;
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
import io.apicurio.registry.types.Current;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProvider;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import io.apicurio.registry.util.ContentTypeUtil;
import io.apicurio.registry.utils.StringUtil;

/**
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
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
     * @see io.apicurio.registry.rest.v2.SearchResource#searchArtifacts(java.lang.String, java.lang.Integer, java.lang.Integer, io.apicurio.registry.rest.v2.beans.SortOrder, io.apicurio.registry.rest.v2.beans.SortBy, java.util.List, java.util.List, java.lang.String, java.lang.String, java.lang.Long, java.lang.Long)
     */
    @Override
    @Authorized(style=AuthorizedStyle.None, level=AuthorizedLevel.Read)
    public ArtifactSearchResults searchArtifacts(String name, Integer offset, Integer limit, SortOrder order,
            SortBy orderby, List<String> labels, List<String> properties, String description, String group,
            Long globalId, Long contentId)
    {
        if (orderby == null) {
            orderby = SortBy.name;
        }
        if (offset == null) {
            offset = 0;
        }
        if (limit == null) {
            limit = 20;
        }

        final OrderBy oBy = OrderBy.valueOf(orderby.name());
        final OrderDirection oDir = order == null || order == SortOrder.asc ? OrderDirection.asc : OrderDirection.desc;

        Set<SearchFilter> filters = new HashSet<SearchFilter>();
        if (!StringUtil.isEmpty(name)) {
            filters.add(SearchFilter.ofName(name));
        }
        if (!StringUtil.isEmpty(description)) {
            filters.add(SearchFilter.ofDescription(description));
        }
        if (!StringUtil.isEmpty(group)) {
            filters.add(SearchFilter.ofGroup(gidOrNull(group)));
        }

        if (labels != null && !labels.isEmpty()) {
            labels.forEach(label -> filters.add(SearchFilter.ofLabel(label)));
        }
        if (properties != null && !properties.isEmpty()) {
            properties.stream()
                .map(prop -> {
                   int delimiterIndex = prop.indexOf(":");
                   String propertyKey;
                   String propertyValue;
                   if (delimiterIndex == 0) {
                       throw new BadRequestException("property search filter wrong formatted, missing left side of ':' delimiter");
                   }
                   if (delimiterIndex == (prop.length() - 1)) {
                       throw new BadRequestException("property search filter wrong formatted, missing right side of ':' delimiter");
                   }
                   if (delimiterIndex < 0) {
                       propertyKey = prop;
                       propertyValue = null;
                   } else{
                       propertyKey = prop.substring(0, delimiterIndex);
                       propertyValue = prop.substring(delimiterIndex + 1);
                   }
                   return SearchFilter.ofProperty(propertyKey, propertyValue);
                })
                .forEach(filters::add);
        }
        if (globalId != null && globalId > 0) {
            filters.add(SearchFilter.ofGlobalId(globalId));
        }
        if (contentId != null && contentId > 0) {
            filters.add(SearchFilter.ofContentId(contentId));
        }

        ArtifactSearchResultsDto results = storage.searchArtifacts(filters, oBy, oDir, offset, limit);
        return V2ApiUtil.dtoToSearchResults(results);
    }

    /**
     * @see io.apicurio.registry.rest.v2.SearchResource#searchArtifactsByContent(java.lang.Boolean, io.apicurio.registry.types.ArtifactType, java.lang.Integer, java.lang.Integer, io.apicurio.registry.rest.v2.beans.SortOrder, io.apicurio.registry.rest.v2.beans.SortBy, java.io.InputStream)
     */
    @Override
    @Authorized(style=AuthorizedStyle.None, level=AuthorizedLevel.Read)
    public ArtifactSearchResults searchArtifactsByContent(Boolean canonical, String artifactType, Integer offset, Integer limit,
            SortOrder order, SortBy orderby, InputStream data) {

        if (orderby == null) {
            orderby = SortBy.name;
        }
        if (offset == null) {
            offset = 0;
        }
        if (limit == null) {
            limit = 20;
        }
        final OrderBy oBy = OrderBy.valueOf(orderby.name());
        final OrderDirection oDir = order == null || order == SortOrder.asc ? OrderDirection.asc : OrderDirection.desc;

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

        Set<SearchFilter> filters = new HashSet<SearchFilter>();
        if (canonical && artifactType != null) {
            String canonicalHash = sha256Hash(canonicalizeContent(artifactType, content));
            filters.add(SearchFilter.ofCanonicalHash(canonicalHash));
        } else if (!canonical) {
            String contentHash = sha256Hash(content);
            filters.add(SearchFilter.ofContentHash(contentHash));
        } else {
            throw new BadRequestException(CANONICAL_QUERY_PARAM_ERROR_MESSAGE);
        }
        ArtifactSearchResultsDto results = storage.searchArtifacts(filters, oBy, oDir, offset, limit);
        return V2ApiUtil.dtoToSearchResults(results);
    }

    /**
     * Make sure this is ONLY used when request instance is active.
     * e.g. in actual http request
     */
    private String getContentType() {
        return request.getContentType();
    }

    private String sha256Hash(ContentHandle chandle) {
        return DigestUtils.sha256Hex(chandle.bytes());
    }

    private String gidOrNull(String groupId) {
        if ("default".equalsIgnoreCase(groupId)) {
            return null;
        }
        return groupId;
    }

    protected ContentHandle canonicalizeContent(String artifactType, ContentHandle content) {
        try {
            ArtifactTypeUtilProvider provider = factory.getArtifactTypeProvider(artifactType);
            ContentCanonicalizer canonicalizer = provider.getContentCanonicalizer();
            ContentHandle canonicalContent = canonicalizer.canonicalize(content, Collections.emptyMap());
            return canonicalContent;
        } catch (Exception e) {
            log.debug("Failed to canonicalize content of type: {}", artifactType);
            return content;
        }
    }

}
