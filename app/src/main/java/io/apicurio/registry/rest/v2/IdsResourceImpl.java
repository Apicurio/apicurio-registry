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

import io.apicurio.common.apps.logging.Logged;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.rest.v2.beans.ArtifactReference;
import io.apicurio.registry.rest.v3.beans.HandleReferencesType;
import io.apicurio.registry.types.ReferenceType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptors;
import jakarta.ws.rs.core.Response;

/**
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
@Logged
public class IdsResourceImpl implements IdsResource {
    
    @Inject
    io.apicurio.registry.rest.v3.IdsResourceImpl v3Impl;

    /**
     * @see io.apicurio.registry.rest.v2.IdsResource#getContentById(long)
     */
    @Override
    public Response getContentById(long contentId) {
        return v3Impl.getContentById(contentId);
    }

    /**
     * @see io.apicurio.registry.rest.v2.IdsResource#getContentByGlobalId(long, java.lang.Boolean)
     */
    @Override
    public Response getContentByGlobalId(long globalId, Boolean dereference) {
        HandleReferencesType handleRefs = HandleReferencesType.PRESERVE;
        if (dereference != null && dereference.booleanValue()) {
            handleRefs = HandleReferencesType.DEREFERENCE;
        }
        return v3Impl.getContentByGlobalId(globalId, handleRefs );
    }

    /**
     * @see io.apicurio.registry.rest.v2.IdsResource#getContentByHash(java.lang.String)
     */
    @Override
    public Response getContentByHash(String contentHash) {
        return v3Impl.getContentByHash(contentHash);
    }

    /**
     * @see io.apicurio.registry.rest.v2.IdsResource#referencesByContentHash(java.lang.String)
     */
    @Override
    public List<ArtifactReference> referencesByContentHash(String contentHash) {
        return V2ApiUtil.fromV3_ArtifactReferenceList(v3Impl.referencesByContentHash(contentHash));
    }

    /**
     * @see io.apicurio.registry.rest.v2.IdsResource#referencesByContentId(long)
     */
    @Override
    public List<ArtifactReference> referencesByContentId(long contentId) {
        return V2ApiUtil.fromV3_ArtifactReferenceList(v3Impl.referencesByContentId(contentId));
    }

    /**
     * @see io.apicurio.registry.rest.v2.IdsResource#referencesByGlobalId(long, io.apicurio.registry.types.ReferenceType)
     */
    @Override
    public List<ArtifactReference> referencesByGlobalId(long globalId, ReferenceType refType) {
        return V2ApiUtil.fromV3_ArtifactReferenceList(v3Impl.referencesByGlobalId(globalId, refType));
    }
    
}
