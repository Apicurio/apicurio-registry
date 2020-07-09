package io.registry;

import io.apicurio.registry.rest.beans.ArtifactMetaData;
import retrofit2.http.GET;
import retrofit2.http.Path;

import javax.ws.rs.core.Response;

public interface IdsService {

    @GET("ids/{globalId}")
    Response getArtifactByGlobalId(@Path("globalId") long globalId);

    @GET("ids/{globalId}/meta")
    ArtifactMetaData getArtifactMetaDataByGlobalId(@Path("globalId") long globalId);
}
