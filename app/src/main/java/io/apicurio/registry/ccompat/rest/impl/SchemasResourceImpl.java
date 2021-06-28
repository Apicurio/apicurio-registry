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

package io.apicurio.registry.ccompat.rest.impl;

import io.apicurio.registry.ccompat.dto.SchemaContent;
import io.apicurio.registry.ccompat.dto.SubjectVersion;
import io.apicurio.registry.ccompat.rest.SchemasResource;
import io.apicurio.registry.logging.Logged;
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
    public SchemaContent getSchema(int id) {
        return facade.getSchemaContent(id);
    }

    @Override
    public List<SubjectVersion> getSubjectVersions(int id) {
        return facade.getSubjectVersions(id);
    }

    @Override
    public List<String> getRegisteredTypes() {
        return Arrays.asList(ArtifactType.JSON.value(), ArtifactType.PROTOBUF.value(), ArtifactType.AVRO.value());
    }
}
