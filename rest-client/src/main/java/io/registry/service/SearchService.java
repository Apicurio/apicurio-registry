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

package io.registry.service;

import io.apicurio.registry.rest.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.beans.SearchOver;
import io.apicurio.registry.rest.beans.SortOrder;
import io.apicurio.registry.rest.beans.VersionSearchResults;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * @author Carles Arnal <carnalca@redhat.com>
 */
public interface SearchService {

    @GET("search/artifacts")
    Call<ArtifactSearchResults> searchArtifacts(@Query("search") String search,
                                                @Query("offset") Integer offset, @Query("limit") Integer limit,
                                                @Query("over") SearchOver over, @Query("order") SortOrder order);

    @GET("search/artifacts/{artifactId}/versions")
    Call<VersionSearchResults> searchVersions(@Path("artifactId") String artifactId,
                                        @Query("offset") Integer offset, @Query("limit") Integer limit);

}
