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

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.interceptor.Interceptors;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.common.apps.logging.Logged;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.rest.HeadersHack;
import io.apicurio.registry.rest.v2.beans.ArtifactReference;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.dto.StoredArtifactDto;
import io.apicurio.registry.types.ArtifactMediaTypes;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;

/**
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
@Logged
public class IdsResourceImpl implements IdsResource {

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    ArtifactTypeUtilProviderFactory factory;

    private void checkIfDeprecated(Supplier<ArtifactState> stateSupplier, String artifactId, String version, Response.ResponseBuilder builder) {
        HeadersHack.checkIfDeprecated(stateSupplier, null, artifactId, version, builder);
    }

    /**
     * @see io.apicurio.registry.rest.v2.IdsResource#getContentById(long)
     */
    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public Response getContentById(long contentId) {
        ContentHandle content = storage.getArtifactByContentId(contentId).getContent();
        Response.ResponseBuilder builder = Response.ok(content, ArtifactMediaTypes.BINARY);
        return builder.build();
    }

    /**
     * @see io.apicurio.registry.rest.v2.IdsResource#getContentByGlobalId(long, java.lang.Boolean)
     */
    @Override
    @Authorized(style = AuthorizedStyle.GlobalId, level = AuthorizedLevel.Read)
    public Response getContentByGlobalId(long globalId, Boolean dereference) {
        ArtifactMetaDataDto metaData = storage.getArtifactMetaData(globalId);
        if (ArtifactState.DISABLED.equals(metaData.getState())) {
            throw new ArtifactNotFoundException(null, String.valueOf(globalId));
        }

        if (dereference == null) {
            dereference = Boolean.FALSE;
        }

        StoredArtifactDto artifact = storage.getArtifactVersion(globalId);

        MediaType contentType = factory.getArtifactMediaType(metaData.getType());

        ContentHandle contentToReturn = artifact.getContent();
        //TODO:carnalca when dereferencing is implemented, we should return the content dereferenced here
        /*
        if (dereference && !artifact.getReferences().isEmpty()) {
            contentToReturn = factory.getArtifactTypeProvider(metaData.getType()).getContentDereferencer().dereference(artifact.getContent(), storage.resolveReferences(artifact.getReferences()));
        }
        */

        Response.ResponseBuilder builder = Response.ok(contentToReturn, contentType);
        checkIfDeprecated(metaData::getState, metaData.getId(), metaData.getVersion(), builder);
        return builder.build();
    }

    /**
     * @see io.apicurio.registry.rest.v2.IdsResource#getContentByHash(java.lang.String)
     */
    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public Response getContentByHash(String contentHash) {
        ContentHandle content = storage.getArtifactByContentHash(contentHash).getContent();
        Response.ResponseBuilder builder = Response.ok(content, ArtifactMediaTypes.BINARY);
        return builder.build();
    }

    @Override
    public List<ArtifactReference> referencesByContentHash(String contentHash) {
        ContentWrapperDto artifact = storage.getArtifactByContentHash(contentHash);
        return artifact.getReferences().stream()
                .map(V2ApiUtil::referenceDtoToReference)
                .collect(Collectors.toList());
    }

    @Override
    public List<ArtifactReference> referencesByContentId(long contentId) {
        ContentWrapperDto artifact = storage.getArtifactByContentId(contentId);
        return artifact.getReferences().stream()
                .map(V2ApiUtil::referenceDtoToReference)
                .collect(Collectors.toList());
    }

    @Override
    public List<ArtifactReference> referencesByGlobalId(long globalId) {
        StoredArtifactDto artifact = storage.getArtifactVersion(globalId);
        return artifact.getReferences().stream()
                .map(V2ApiUtil::referenceDtoToReference)
                .collect(Collectors.toList());
    }
}
