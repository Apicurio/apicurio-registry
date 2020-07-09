package io.registry;


import io.apicurio.registry.rest.beans.*;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import retrofit2.http.*;

import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CompletionStage;

public interface ArtifactsService {

    @GET("artifacts")
    List<String> listArtifacts();

    @POST("artifacts")
    CompletionStage<ArtifactMetaData> createArtifact(@Header("X-Registry-ArtifactType") ArtifactType artifactType,
                                                     @Header("X-Registry-Artifactid") String xRegistryArtifactId,
                                                     @Query("ifExists") IfExistsType ifExistsType,
                                                     InputStream data);

    @GET("artifacts/{artifactId}")
    Response getLatestArtifact(@Path("artifactId") String artifactId);

    @PUT("artifacts/{artifactId}")
    CompletionStage<ArtifactMetaData> updateArtifact(@Path("artifactId") String artifactId,
                                                     @Header("X-Registry-ArtifactType") ArtifactType xRegistryArtifactType, InputStream data);

    @DELETE("artifacts/{artifactId}")
    void deleteArtifact(@Path("artifactId") String artifactId);

    @PUT("artifacts/{artifactId}/state")
    void updateArtifactState(@Path("artifactId") String artifactId, UpdateState data);

    @GET("artifacts/{artifactId/meta}")
    ArtifactMetaData getArtifactMetaData(@Path("artifactId") String artifactId);

    @PUT("artifacts/{artifactId}/meta")
    void updateArtifactMetaData(@Path("artifactId") String artifactId, EditableMetaData data);

    @POST("artifacts/{artifactId}/meta")
    ArtifactMetaData getArtifactMetaDataByContent(@Path("artifactId") String artifactId,
                                                  InputStream data);

    @GET("artifacts/{artifactId}/versions")
    List<Long> listArtifactVersions(@Path("artifactId") String artifactId);

    @POST("artifacts/{artifactId}/versions")
    CompletionStage<VersionMetaData> createArtifactVersion(@Path("artifactId") String artifactId,
                                                           @Header("X-Registry-ArtifactType") ArtifactType xRegistryArtifactType, InputStream data);

    @GET("artifacts/{artifactId}/versions/{version}")
    Response getArtifactVersion(@Path("version") Integer version,
                                @Path("artifactId") String artifactId);

    @PUT("artifacts/{artifactId}/versions/{version}/state")
    void updateArtifactVersionState(@Path("version") Integer version,
                                    @Path("artifactId") String artifactId, UpdateState data);

    @GET("artifacts/{artifactId}/versions/{version}/meta")
    VersionMetaData getArtifactVersionMetaData(@Path("version") Integer version,
                                               @Path("artifactId") String artifactId);

    @PUT("artifacts/{artifactId}/versions/{version}/meta")
    void updateArtifactVersionMetaData(@Path("version") Integer version,
                                       @Path("artifactId") String artifactId, EditableMetaData data);

    @DELETE("artifacts/{artifactId}/versions/{version}/meta")
    void deleteArtifactVersionMetaData(@Path("version") Integer version,
                                       @Path("artifactId") String artifactId);

    @GET("artifacts/{artifactId}/rules")
    List<RuleType> listArtifactRules(@Path("artifactId") String artifactId);

    @POST("artifacts/{artifactId}/rules")
    void createArtifactRule(@Path("artifactId") String artifactId, Rule data);

    @DELETE("artifacts/{artifactId}/rules")
    void deleteArtifactRules(@Path("artifactId") String artifactId);

    @GET("artifacts/{artifactId}/rules/{rule}")
    Rule getArtifactRuleConfig(@Path("rule") RuleType rule,
                               @Path("artifactId") String artifactId);

    @PUT("artifacts/{artifactId}/rules/{rule}")
    Rule updateArtifactRuleConfig(@Path("rule") RuleType rule,
                                  @Path("artifactId") String artifactId, Rule data);

    @DELETE("artifacts/{artifactId}/rules/{rule}")
    void deleteArtifactRule(@Path("rule") RuleType rule,
                            @Path("artifactId") String artifactId);

    @PUT("artifacts/{artifactId}/test")
    void testUpdateArtifact(@Path("artifactId") String artifactId,
                            @Header("X-Registry-ArtifactType") ArtifactType xRegistryArtifactType, InputStream data);
}
