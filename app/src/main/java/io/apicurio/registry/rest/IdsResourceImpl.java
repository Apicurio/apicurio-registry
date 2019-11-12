/*
 * Copyright 2019 Red Hat
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

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.storage.ArtifactMetaDataDto;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.StoredArtifact;
import io.apicurio.registry.types.ArtifactMediaTypes;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.util.DtoUtil;

/**
 * @author eric.wittmann@gmail.com
 */
public class IdsResourceImpl implements IdsResource {

    @Inject
    @Current
    RegistryStorage storage;

    @Context
    HttpServletRequest request;

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
        return Response.ok(artifact.content, contentType).build();
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
