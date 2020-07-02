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

package io.apicurio.registry.rest;

import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.metrics.RestMetricsApply;
import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.storage.ArtifactMetaDataDto;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.StoredArtifact;
import io.apicurio.registry.types.ArtifactMediaTypes;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.util.DtoUtil;
import io.quarkus.security.Authenticated;
import org.eclipse.microprofile.metrics.annotation.ConcurrentGauge;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Timed;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.interceptor.Interceptors;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.function.Supplier;

import static io.apicurio.registry.metrics.MetricIDs.*;
import static org.eclipse.microprofile.metrics.MetricUnits.MILLISECONDS;

/**
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
@RestMetricsApply
@Counted(name = REST_REQUEST_COUNT, description = REST_REQUEST_COUNT_DESC, tags = {"group=" + REST_GROUP_TAG, "metric=" + REST_REQUEST_COUNT}, reusable = true)
@ConcurrentGauge(name = REST_CONCURRENT_REQUEST_COUNT, description = REST_CONCURRENT_REQUEST_COUNT_DESC, tags = {"group=" + REST_GROUP_TAG, "metric=" + REST_CONCURRENT_REQUEST_COUNT}, reusable = true)
@Timed(name = REST_REQUEST_RESPONSE_TIME, description = REST_REQUEST_RESPONSE_TIME_DESC, tags = {"group=" + REST_GROUP_TAG, "metric=" + REST_REQUEST_RESPONSE_TIME}, unit = MILLISECONDS, reusable = true)
@Logged
@Authenticated
public class IdsResourceImpl implements IdsResource, Headers {

    @Inject
    @Current
    RegistryStorage storage;

    @Context
    HttpServletRequest request;

    @Override
    public void checkIfDeprecated(Supplier<ArtifactState> stateSupplier, String artifactId, Number version, Response.ResponseBuilder builder) {
        HeadersHack.checkIfDeprecated(stateSupplier, artifactId, version, builder);
    }

    /**
     * @see io.apicurio.registry.rest.IdsResource#getArtifactByGlobalId(long)
     */
    @Override
    public Response getArtifactByGlobalId(long globalId) {
        ArtifactMetaDataDto metaData = storage.getArtifactMetaData(globalId);
        StoredArtifact artifact = storage.getArtifactVersion(globalId);

        // protobuf - the content-type will be different for protobuf artifacts
        MediaType contentType = ArtifactMediaTypes.JSON;
        if (metaData.getType() == ArtifactType.PROTOBUF) {
            contentType = ArtifactMediaTypes.PROTO;
        }

        Response.ResponseBuilder builder = Response.ok(artifact.getContent(), contentType);
        checkIfDeprecated(metaData::getState, metaData.getId(), metaData.getVersion(), builder);
        return builder.build();
    }

    /**
     * @see io.apicurio.registry.rest.IdsResource#getArtifactMetaDataByGlobalId(long)
     */
    @Override
    public ArtifactMetaData getArtifactMetaDataByGlobalId(long globalId) {
        ArtifactMetaDataDto dto = storage.getArtifactMetaData(globalId);
        return DtoUtil.dtoToMetaData(dto.getId(), dto.getType(), dto);
    }

}
