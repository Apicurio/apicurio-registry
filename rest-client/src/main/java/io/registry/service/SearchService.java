package io.registry.service;

import io.apicurio.registry.rest.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.beans.SearchOver;
import io.apicurio.registry.rest.beans.SortOrder;
import io.apicurio.registry.rest.beans.VersionSearchResults;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface SearchService {

    @GET("search/artifacts")
    ArtifactSearchResults searchArtifacts(@Query("search") String search,
                                          @Query("offset") Integer offset, @Query("limit") Integer limit,
                                          @Query("over") SearchOver over, @Query("order") SortOrder order);

    @GET("search/artifacts/{artifactId}/versions")
    VersionSearchResults searchVersions(@Path("artifactId") String artifactId,
                                        @Query("offset") Integer offset, @Query("limit") Integer limit);

}
