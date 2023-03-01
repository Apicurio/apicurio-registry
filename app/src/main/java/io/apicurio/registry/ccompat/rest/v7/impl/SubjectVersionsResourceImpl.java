/*
 * Copyright 2022 Red Hat
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

package io.apicurio.registry.ccompat.rest.v7.impl;

import io.apicurio.common.apps.logging.Logged;
import io.apicurio.common.apps.logging.audit.Audited;
import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.ccompat.dto.Schema;
import io.apicurio.registry.ccompat.dto.SchemaId;
import io.apicurio.registry.ccompat.dto.SchemaInfo;
import io.apicurio.registry.ccompat.rest.v7.SubjectVersionsResource;
import io.apicurio.registry.ccompat.store.FacadeConverter;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;

import jakarta.inject.Inject;
import jakarta.interceptor.Interceptors;
import jakarta.ws.rs.BadRequestException;
import java.util.List;

import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_ARTIFACT_ID;
import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_VERSION;

/**
 * @author Carles Arnal
 */
@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
@Logged
public class SubjectVersionsResourceImpl extends AbstractResource implements SubjectVersionsResource {

    @Inject
    FacadeConverter converter;

    @Override
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Read)
    public List<Integer> listVersions(String subject, String groupId) throws Exception {
        return facade.getVersions(subject, groupId);
    }

    @Override
    @Audited(extractParameters = {"0", KEY_ARTIFACT_ID})
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Write)
    public SchemaId register(String subject, SchemaInfo request, Boolean normalize, String groupId) throws Exception {
        final boolean fnormalize = normalize == null ? Boolean.FALSE : normalize;
        Long id = facade.createSchema(subject, request.getSchema(), request.getSchemaType(), request.getReferences(), fnormalize, groupId);
        int sid = converter.convertUnsigned(id);
        return new SchemaId(sid);
    }

    @Override
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Read)
    public Schema getSchemaByVersion(String subject, String version, String groupId) throws Exception {
        return facade.getSchema(subject, version, groupId);
    }

    @Override
    @Audited(extractParameters = {"0", KEY_ARTIFACT_ID, "1", KEY_VERSION})
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Write)
    public int deleteSchemaVersion(String subject, String version, Boolean permanent, String groupId) throws Exception {
        try {
            final boolean fnormalize = permanent == null ? Boolean.FALSE : permanent;
            return facade.deleteSchema(subject, version, fnormalize, groupId);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(ex); // TODO
        }
    }

    @Override
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Read)
    public String getSchemaOnly(String subject, String version, String groupId) throws Exception {
        return facade.getSchema(subject, version, groupId).getSchema();
    }

    @Override
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Read)
    public List<Long> getSchemasReferencedBy(String subject, String version, String groupId) throws Exception {
        return facade.getContentIdsReferencingArtifact(subject, version, groupId);
    }
}
