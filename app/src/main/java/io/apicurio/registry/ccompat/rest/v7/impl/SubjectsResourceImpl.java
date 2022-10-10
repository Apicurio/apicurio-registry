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
import io.apicurio.registry.ccompat.dto.SchemaContent;
import io.apicurio.registry.ccompat.rest.v7.SubjectsResource;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;

import javax.interceptor.Interceptors;
import java.util.List;

import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_ARTIFACT_ID;

/**
 * @author Carles Arnal
 */
@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
@Logged
public class SubjectsResourceImpl extends AbstractResource implements SubjectsResource {

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public List<String> listSubjects(String subjectPrefix, Boolean deleted) {
        //Since contexts are not supported, subjectPrefix is not used
        final boolean fdeleted = deleted == null ? Boolean.FALSE : deleted;
        return facade.getSubjects(fdeleted);
    }

    @Override
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Read)
    public Schema findSchemaByContent(String subject, SchemaContent request, Boolean normalize) throws Exception {
        final boolean fnormalize = normalize == null ? Boolean.FALSE : normalize;
        return facade.getSchema(subject, request, fnormalize);
    }

    @Override
    @Audited(extractParameters = {"0", KEY_ARTIFACT_ID})
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Write)
    public List<Integer> deleteSubject(String subject, Boolean permanent) throws Exception {
        final boolean fpermanent = permanent == null ? Boolean.FALSE : permanent;
        return facade.deleteSubject(subject, fpermanent);
    }
}
