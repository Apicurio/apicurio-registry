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

package io.registry.client.service;

import io.apicurio.registry.rest.beans.ArtifactMetaData;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

/**
 * @author Carles Arnal <carnalca@redhat.com>
 */
public interface IdsService {

    @GET("ids/{globalId}")
    Call<ResponseBody> getArtifactByGlobalId(@Path("globalId") long globalId);

    @GET("ids/{globalId}/meta")
    Call<ArtifactMetaData> getArtifactMetaDataByGlobalId(@Path("globalId") long globalId);
}
