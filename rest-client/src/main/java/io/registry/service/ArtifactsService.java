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

import io.apicurio.registry.rest.beans.*;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.*;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * @author Carles Arnal <carnalca@redhat.com>
 */
public interface ArtifactsService {

    @GET("artifacts")
    Call<List<String>> listArtifacts();

    @POST("artifacts")
    CompletableFuture<ArtifactMetaData> createArtifact(@Header("X-Registry-ArtifactType") ArtifactType artifactType,
                                                       @Header("X-Registry-Artifactid") String xRegistryArtifactId,
                                                       @Query("ifExists") IfExistsType ifExistsType,
                                                       @Body RequestBody data);

    @GET("artifacts/{artifactId}")
    Call<Response> getLatestArtifact(@Path("artifactId") String artifactId);

    @PUT("artifacts/{artifactId}")
    CompletableFuture<ArtifactMetaData> updateArtifact(@Path("artifactId") String artifactId,
                                                       @Header("X-Registry-ArtifactType") ArtifactType xRegistryArtifactType, @Body RequestBody data);

    @DELETE("artifacts/{artifactId}")
    Call<Void> deleteArtifact(@Path("artifactId") String artifactId);

    @PUT("artifacts/{artifactId}/state")
    Call<Void> updateArtifactState(@Path("artifactId") String artifactId, @Body UpdateState data);

    @GET("artifacts/{artifactId}/meta")
    Call<ArtifactMetaData> getArtifactMetaData(@Path("artifactId") String artifactId);

    @PUT("artifacts/{artifactId}/meta")
    Call<Void> updateArtifactMetaData(@Path("artifactId") String artifactId, @Body EditableMetaData data);

    @POST("artifacts/{artifactId}/meta")
    Call<ArtifactMetaData> getArtifactMetaDataByContent(@Path("artifactId") String artifactId,
                                                        @Body RequestBody data);

    @GET("artifacts/{artifactId}/versions")
    Call<List<Long>> listArtifactVersions(@Path("artifactId") String artifactId);

    @POST("artifacts/{artifactId}/versions")
    CompletableFuture<VersionMetaData> createArtifactVersion(@Path("artifactId") String artifactId,
                                                             @Header("X-Registry-ArtifactType") ArtifactType xRegistryArtifactType, @Body RequestBody data);

    @GET("artifacts/{artifactId}/versions/{version}")
    Call<Response> getArtifactVersion(@Path("version") Integer version,
                                      @Path("artifactId") String artifactId);

    @PUT("artifacts/{artifactId}/versions/{version}/state")
    Call<Void> updateArtifactVersionState(@Path("version") Integer version,
                                          @Path("artifactId") String artifactId, @Body UpdateState data);

    @GET("artifacts/{artifactId}/versions/{version}/meta")
    Call<VersionMetaData> getArtifactVersionMetaData(@Path("version") Integer version,
                                                     @Path("artifactId") String artifactId);

    @PUT("artifacts/{artifactId}/versions/{version}/meta")
    Call<Void> updateArtifactVersionMetaData(@Path("version") Integer version,
                                             @Path("artifactId") String artifactId, @Body EditableMetaData data);

    @DELETE("artifacts/{artifactId}/versions/{version}/meta")
    Call<Void> deleteArtifactVersionMetaData(@Path("version") Integer version,
                                             @Path("artifactId") String artifactId);

    @GET("artifacts/{artifactId}/rules")
    Call<List<RuleType>> listArtifactRules(@Path("artifactId") String artifactId);

    @POST("artifacts/{artifactId}/rules")
    Call<Void> createArtifactRule(@Path("artifactId") String artifactId, @Body Rule data);

    @DELETE("artifacts/{artifactId}/rules")
    Call<Void> deleteArtifactRules(@Path("artifactId") String artifactId);

    @GET("artifacts/{artifactId}/rules/{rule}")
    Call<Rule> getArtifactRuleConfig(@Path("rule") RuleType rule,
                                     @Path("artifactId") String artifactId);

    @PUT("artifacts/{artifactId}/rules/{rule}")
    Call<Rule> updateArtifactRuleConfig(@Path("rule") RuleType rule,
                                        @Path("artifactId") String artifactId, @Body Rule data);

    @DELETE("artifacts/{artifactId}/rules/{rule}")
    Call<Void> deleteArtifactRule(@Path("rule") RuleType rule,
                                  @Path("artifactId") String artifactId);

    @PUT("artifacts/{artifactId}/test")
    Call<Void> testUpdateArtifact(@Path("artifactId") String artifactId,
                                  @Header("X-Registry-ArtifactType") ArtifactType xRegistryArtifactType, @Body RequestBody data);
}
