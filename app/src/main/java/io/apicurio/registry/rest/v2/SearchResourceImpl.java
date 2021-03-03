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

import static io.apicurio.registry.metrics.MetricIDs.REST_CONCURRENT_REQUEST_COUNT;
import static io.apicurio.registry.metrics.MetricIDs.REST_CONCURRENT_REQUEST_COUNT_DESC;
import static io.apicurio.registry.metrics.MetricIDs.REST_GROUP_TAG;
import static io.apicurio.registry.metrics.MetricIDs.REST_REQUEST_COUNT;
import static io.apicurio.registry.metrics.MetricIDs.REST_REQUEST_COUNT_DESC;
import static io.apicurio.registry.metrics.MetricIDs.REST_REQUEST_RESPONSE_TIME;
import static io.apicurio.registry.metrics.MetricIDs.REST_REQUEST_RESPONSE_TIME_DESC;
import static org.eclipse.microprofile.metrics.MetricUnits.MILLISECONDS;

import java.io.InputStream;
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
import org.eclipse.microprofile.metrics.annotation.ConcurrentGauge;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Timed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.metrics.RestMetricsApply;
import io.apicurio.registry.metrics.RestMetricsResponseFilteredNameBinding;
import io.apicurio.registry.rest.v2.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.v2.beans.SortBy;
import io.apicurio.registry.rest.v2.beans.SortOrder;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactSearchResultsDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.SearchFilterType;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProvider;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import io.apicurio.registry.util.ContentTypeUtil;
import io.apicurio.registry.utils.StringUtil;

/**
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
@RestMetricsResponseFilteredNameBinding
@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
@RestMetricsApply
@Counted(name = REST_REQUEST_COUNT, description = REST_REQUEST_COUNT_DESC, tags = {"group=" + REST_GROUP_TAG, "metric=" + REST_REQUEST_COUNT})
@ConcurrentGauge(name = REST_CONCURRENT_REQUEST_COUNT, description = REST_CONCURRENT_REQUEST_COUNT_DESC, tags = {"group=" + REST_GROUP_TAG, "metric=" + REST_CONCURRENT_REQUEST_COUNT})
@Timed(name = REST_REQUEST_RESPONSE_TIME, description = REST_REQUEST_RESPONSE_TIME_DESC, tags = {"group=" + REST_GROUP_TAG, "metric=" + REST_REQUEST_RESPONSE_TIME}, unit = MILLISECONDS)
@Logged
public class SearchResourceImpl implements SearchResource {

    private static final String EMPTY_CONTENT_ERROR_MESSAGE = "Empty content is not allowed.";
    private static final String CANONICAL_QUERY_PARAM_ERROR_MESSAGE = "When setting 'canonical' to 'true', the 'artifactType' query parameter is also required.";
    private static final Logger log = LoggerFactory.getLogger(SearchResourceImpl.class);

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    ArtifactTypeUtilProviderFactory factory;

    @Context
    HttpServletRequest request;

    /**
     * @see io.apicurio.registry.rest.v2.SearchResource#searchArtifacts(java.lang.String, java.lang.Integer, java.lang.Integer, io.apicurio.registry.rest.v2.beans.SortOrder, io.apicurio.registry.rest.v2.beans.SortBy, java.util.List, java.util.List, java.lang.String, java.lang.String)
     */
    @Override
    public ArtifactSearchResults searchArtifacts(String name, Integer offset, Integer limit, SortOrder order,
            SortBy orderby, List<String> labels, List<String> properties, String description,
            String group) {
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
            filters.add(new SearchFilter(SearchFilterType.name, name));
        }
        if (!StringUtil.isEmpty(description)) {
            filters.add(new SearchFilter(SearchFilterType.description, description));
        }
        if (!StringUtil.isEmpty(group)) {
            filters.add(new SearchFilter(SearchFilterType.group, gidOrNull(group)));
        }

        if (labels != null && !labels.isEmpty()) {
            labels.forEach(label -> {
                filters.add(new SearchFilter(SearchFilterType.labels, label));
            });
        }
        if (properties != null && !properties.isEmpty()) {
            // TODO implement filtering by properties!
        }

        ArtifactSearchResultsDto results = storage.searchArtifacts(filters, oBy, oDir, offset, limit);
        return V2ApiUtil.dtoToSearchResults(results);
    }

    /**
     * @see io.apicurio.registry.rest.v2.SearchResource#searchArtifactsByContent(java.lang.Boolean, io.apicurio.registry.types.ArtifactType, java.lang.Integer, java.lang.Integer, io.apicurio.registry.rest.v2.beans.SortOrder, io.apicurio.registry.rest.v2.beans.SortBy, java.io.InputStream)
     */
    @Override
    public ArtifactSearchResults searchArtifactsByContent(Boolean canonical, ArtifactType artifactType, Integer offset, Integer limit,
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
            filters.add(new SearchFilter(SearchFilterType.canonicalHash, canonicalHash));
        } else if (!canonical) {
            String contentHash = sha256Hash(content);
            filters.add(new SearchFilter(SearchFilterType.contentHash, contentHash));
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

    protected ContentHandle canonicalizeContent(ArtifactType artifactType, ContentHandle content) {
        try {
            ArtifactTypeUtilProvider provider = factory.getArtifactTypeProvider(artifactType);
            ContentCanonicalizer canonicalizer = provider.getContentCanonicalizer();
            ContentHandle canonicalContent = canonicalizer.canonicalize(content);
            return canonicalContent;
        } catch (Exception e) {
            log.debug("Failed to canonicalize content of type: {}", artifactType.name());
            return content;
        }
    }

}
