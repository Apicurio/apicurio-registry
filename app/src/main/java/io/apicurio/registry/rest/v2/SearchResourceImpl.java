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
import java.math.BigInteger;
import java.util.List;

import io.apicurio.common.apps.logging.Logged;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.rest.v2.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.v2.beans.SortBy;
import io.apicurio.registry.rest.v2.beans.SortOrder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptors;
import jakarta.validation.constraints.NotNull;

/**
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
@Logged
public class SearchResourceImpl implements SearchResource {
    
    @Inject
    io.apicurio.registry.rest.v3.SearchResourceImpl v3Impl;

    /**
     * @see io.apicurio.registry.rest.v2.SearchResource#searchArtifacts(java.lang.String, java.math.BigInteger, java.math.BigInteger, io.apicurio.registry.rest.v2.beans.SortOrder, io.apicurio.registry.rest.v2.beans.SortBy, java.util.List, java.util.List, java.lang.String, java.lang.String, java.lang.Long, java.lang.Long)
     */
    @Override
    public ArtifactSearchResults searchArtifacts(String name, BigInteger offset, BigInteger limit,
            SortOrder order, SortBy orderby, List<String> labels, List<String> properties, String description,
            String group, Long globalId, Long contentId) {
        
        return V2ApiUtil.fromV3(v3Impl.searchArtifacts(name, offset, limit, V2ApiUtil.toV3(order), V2ApiUtil.toV3(orderby), 
                labels, properties, description, group, globalId, contentId));
    }

    /**
     * @see io.apicurio.registry.rest.v2.SearchResource#searchArtifactsByContent(java.lang.Boolean, java.lang.String, java.math.BigInteger, java.math.BigInteger, io.apicurio.registry.rest.v2.beans.SortOrder, io.apicurio.registry.rest.v2.beans.SortBy, java.io.InputStream)
     */
    @Override
    public ArtifactSearchResults searchArtifactsByContent(Boolean canonical, String artifactType,
            BigInteger offset, BigInteger limit, SortOrder order, SortBy orderby, @NotNull InputStream data) {
        return V2ApiUtil.fromV3(v3Impl.searchArtifactsByContent(canonical, artifactType, offset, limit, 
                V2ApiUtil.toV3(order), V2ApiUtil.toV3(orderby), data));
    }

}
