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

import java.util.HashSet;
import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.interceptor.Interceptors;

import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.common.apps.logging.Logged;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
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
@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
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
    @Authorized(style=AuthorizedStyle.None, level=AuthorizedLevel.Read)
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
            filter.setStringValue(search);
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
                    break;
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
    @Authorized(style=AuthorizedStyle.ArtifactOnly, level=AuthorizedLevel.Read)
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
