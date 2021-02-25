/*
 * Copyright 2020 Red Hat
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

package io.apicurio.registry.rest.v1;

import static io.apicurio.registry.metrics.MetricIDs.REST_CONCURRENT_REQUEST_COUNT;
import static io.apicurio.registry.metrics.MetricIDs.REST_CONCURRENT_REQUEST_COUNT_DESC;
import static io.apicurio.registry.metrics.MetricIDs.REST_GROUP_TAG;
import static io.apicurio.registry.metrics.MetricIDs.REST_REQUEST_COUNT;
import static io.apicurio.registry.metrics.MetricIDs.REST_REQUEST_COUNT_DESC;
import static io.apicurio.registry.metrics.MetricIDs.REST_REQUEST_RESPONSE_TIME;
import static io.apicurio.registry.metrics.MetricIDs.REST_REQUEST_RESPONSE_TIME_DESC;
import static org.eclipse.microprofile.metrics.MetricUnits.MILLISECONDS;

import java.util.HashSet;
import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.interceptor.Interceptors;

import org.eclipse.microprofile.metrics.annotation.ConcurrentGauge;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Timed;

import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.metrics.RestMetricsApply;
import io.apicurio.registry.metrics.RestMetricsResponseFilteredNameBinding;
import io.apicurio.registry.rest.v1.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.v1.beans.SearchOver;
import io.apicurio.registry.rest.v1.beans.SortOrder;
import io.apicurio.registry.rest.v1.beans.VersionSearchResults;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactSearchResultsDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.SearchFilterType;
import io.apicurio.registry.storage.dto.VersionSearchResultsDto;
import io.apicurio.registry.types.Current;

/**
 * Implements the {@link SearchResource} interface.
 *
 * @author Carles Arnal
 */
@ApplicationScoped
@RestMetricsResponseFilteredNameBinding
@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
@RestMetricsApply
@Counted(name = REST_REQUEST_COUNT, description = REST_REQUEST_COUNT_DESC, tags = {"group=" + REST_GROUP_TAG, "metric=" + REST_REQUEST_COUNT})
@ConcurrentGauge(name = REST_CONCURRENT_REQUEST_COUNT, description = REST_CONCURRENT_REQUEST_COUNT_DESC, tags = {"group=" + REST_GROUP_TAG, "metric=" + REST_CONCURRENT_REQUEST_COUNT})
@Timed(name = REST_REQUEST_RESPONSE_TIME, description = REST_REQUEST_RESPONSE_TIME_DESC, tags = {"group=" + REST_GROUP_TAG, "metric=" + REST_REQUEST_RESPONSE_TIME}, unit = MILLISECONDS)
@Logged
@Deprecated
public class SearchResourceImpl implements SearchResource {

    @Inject
    @Current
    RegistryStorage registryStorage;

    /**
     * @see io.apicurio.registry.rest.v1.SearchResource#searchArtifacts(java.lang.String, java.lang.Integer, java.lang.Integer, io.apicurio.registry.rest.v1.beans.SearchOver, io.apicurio.registry.rest.v1.beans.SortOrder)
     */
    @Override
    public ArtifactSearchResults searchArtifacts(String search, Integer offset, Integer limit, SearchOver searchOver, SortOrder sortOrder) {
        if (offset == null) {
            offset = 0;
        }
        if (limit == null) {
            limit = 10;
        }
        final OrderBy orderBy = OrderBy.name;
        final OrderDirection orderDir = sortOrder == null || sortOrder == SortOrder.asc ? OrderDirection.asc : OrderDirection.desc;
        final SearchOver over = searchOver == null ? SearchOver.everything : searchOver;

        Set<SearchFilter> filters = new HashSet<>();
        if (search != null && !search.trim().isEmpty()) {
            SearchFilter filter = new SearchFilter();
            filter.setValue(search);
            filters.add(filter);
            switch (over) {
                case description:
                    filter.setType(SearchFilterType.description);
                    break;
                case labels:
                    filter.setType(SearchFilterType.labels);
                    break;
                case name:
                    filter.setType(SearchFilterType.name);
                case everything:
                default:
                    filter.setType(SearchFilterType.everything);
                    break;
            }
        }
        ArtifactSearchResultsDto results = registryStorage.searchArtifacts(filters, orderBy, orderDir, offset, limit);
        return V1ApiUtil.dtoToSearchResults(results);
    }

	@Override
	public VersionSearchResults searchVersions(String artifactId, Integer offset, Integer limit) {
        if (offset == null) {
            offset = 0;
        }
        if (limit == null) {
            limit = 10;
        }

        VersionSearchResultsDto results = registryStorage.searchVersions(null, artifactId, offset, limit);
        return V1ApiUtil.dtoToSearchResults(results);
	}
}
