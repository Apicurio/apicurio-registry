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

import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.ccompat.dto.SchemaInfo;
import io.apicurio.registry.ccompat.dto.SubjectVersion;
import io.apicurio.registry.ccompat.rest.v6.SchemasResource;
import io.apicurio.common.apps.logging.Logged;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.types.ArtifactType;

import javax.interceptor.Interceptors;
import java.util.Arrays;
import java.util.List;

/**
 * @author Ales Justin
 * @author Jakub Senko 'jsenko@redhat.com'
 */
@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
@Logged
public class SchemasResourceImpl extends AbstractResource implements SchemasResource {

    @Override
    @Authorized(style=AuthorizedStyle.GlobalId, level=AuthorizedLevel.Read)
    public SchemaInfo getSchema(int id) {
        return facade.getSchemaById(id);
    }

    @Override
    @Authorized(style=AuthorizedStyle.GlobalId, level=AuthorizedLevel.Read)
    public List<SubjectVersion> getSubjectVersions(int id) {
        return facade.getSubjectVersions(id);
    }

    @Override
    @Authorized(style=AuthorizedStyle.None, level=AuthorizedLevel.Read)
    public List<String> getRegisteredTypes() {
        return Arrays.asList(ArtifactType.JSON, ArtifactType.PROTOBUF, ArtifactType.AVRO);
    }
}
