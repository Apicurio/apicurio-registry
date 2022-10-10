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

package io.apicurio.registry.ccompat.rest.v6.impl;

import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_ARTIFACT_ID;
import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_VERSION;

import java.util.List;

import javax.inject.Inject;
import javax.interceptor.Interceptors;
import javax.ws.rs.BadRequestException;

import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.ccompat.dto.Schema;
import io.apicurio.registry.ccompat.dto.SchemaId;
import io.apicurio.registry.ccompat.dto.SchemaInfo;
import io.apicurio.registry.ccompat.rest.v6.SubjectVersionsResource;
import io.apicurio.registry.ccompat.store.FacadeConverter;
import io.apicurio.common.apps.logging.Logged;
import io.apicurio.common.apps.logging.audit.Audited;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;

/**
 * @author Ales Justin
 * @author Jakub Senko 'jsenko@redhat.com'
 */
@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
@Logged
public class SubjectVersionsResourceImpl extends AbstractResource implements SubjectVersionsResource {

    @Inject
    FacadeConverter converter;

    @Override
    @Authorized(style=AuthorizedStyle.ArtifactOnly, level=AuthorizedLevel.Read)
    public List<Integer> listVersions(String subject) throws Exception {
        return facade.getVersions(subject);
    }

    @Override
    @Audited(extractParameters = {"0", KEY_ARTIFACT_ID})
    @Authorized(style=AuthorizedStyle.ArtifactOnly, level=AuthorizedLevel.Write)
    public SchemaId register(String subject, SchemaInfo request) throws Exception {
        Long id = facade.createSchema(subject, request.getSchema(), request.getSchemaType(), request.getReferences(), false);
        int sid = converter.convertUnsigned(id);
        return new SchemaId(sid);
    }

    @Override
    @Authorized(style=AuthorizedStyle.ArtifactOnly, level=AuthorizedLevel.Read)
    public Schema getSchemaByVersion(
            String subject,
            String version) throws Exception {

        return facade.getSchema(subject, version);
    }

    @Override
    @Audited(extractParameters = {"0", KEY_ARTIFACT_ID, "1", KEY_VERSION})
    @Authorized(style=AuthorizedStyle.ArtifactOnly, level=AuthorizedLevel.Write)
    public int deleteSchemaVersion(
            String subject,
            String version) throws Exception {

        try {
            return facade.deleteSchema(subject, version, true);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(ex);
        }
    }

    @Override
    @Authorized(style=AuthorizedStyle.ArtifactOnly, level=AuthorizedLevel.Read)
    public String getSchemaOnly(
            String subject,
            String version) throws Exception {

        return facade.getSchema(subject, version).getSchema();
    }

    @Override
    @Authorized(style=AuthorizedStyle.ArtifactOnly, level=AuthorizedLevel.Read)
    public List<Long> getSchemasReferencedBy(String subject, String version) throws Exception {
        return facade.getContentIdsReferencingArtifact(subject, version);
    }
}
