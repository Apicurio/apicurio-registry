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
import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.ccompat.dto.CompatibilityCheckResponse;
import io.apicurio.registry.ccompat.dto.SchemaContent;
import io.apicurio.registry.ccompat.rest.error.UnprocessableEntityException;
import io.apicurio.registry.ccompat.rest.v7.CompatibilityResource;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.rules.RuleViolationException;
import io.apicurio.registry.rules.UnprocessableSchemaException;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import jakarta.interceptor.Interceptors;

import java.util.Collections;
import java.util.List;

/**
 * @author Carles Arnal
 */
@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
@Logged
public class CompatibilityResourceImpl extends AbstractResource implements CompatibilityResource {

    @Override
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Write)
    public CompatibilityCheckResponse testCompatibilityBySubjectName(String subject, SchemaContent request, Boolean verbose, String groupId) throws Exception {
        final GA ga = getGA(groupId, subject);

        final boolean fverbose = verbose == null ? Boolean.FALSE : verbose;
        try {
            final List<String> versions = storage.getArtifactVersions(ga.getGroupId(), ga.getArtifactId());
            for (String version : versions) {
                final ArtifactVersionMetaDataDto artifactVersionMetaData = storage.getArtifactVersionMetaData(ga.getGroupId(), ga.getArtifactId(), version);
                rulesService.applyRules(ga.getGroupId(), ga.getArtifactId(), version, artifactVersionMetaData.getType(),
                        ContentHandle.create(request.getSchema()), Collections.emptyList(), Collections.emptyMap());
            }
            return CompatibilityCheckResponse.IS_COMPATIBLE;
        } catch (RuleViolationException ex) {
            if (fverbose) {
                return new CompatibilityCheckResponse(false, ex.getMessage());
            } else {
                return CompatibilityCheckResponse.IS_NOT_COMPATIBLE;
            }
        } catch (UnprocessableSchemaException ex) {
            throw new UnprocessableEntityException(ex.getMessage());
        }
    }

    @Override
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Write)
    public CompatibilityCheckResponse testCompatibilityByVersion(String subject, String versionString, SchemaContent request, Boolean verbose, String groupId) throws Exception {
        final boolean fverbose = verbose == null ? Boolean.FALSE : verbose;
        final GA ga = getGA(groupId, subject);

        return parseVersionString(ga.getArtifactId(), versionString, ga.getGroupId(), v -> {
            try {
                final ArtifactVersionMetaDataDto artifact = storage.getArtifactVersionMetaData(ga.getGroupId(), ga.getArtifactId(), v);
                rulesService.applyRules(ga.getGroupId(), ga.getArtifactId(), v, artifact.getType(), ContentHandle.create(request.getSchema()), Collections.emptyList(), Collections.emptyMap());
                return CompatibilityCheckResponse.IS_COMPATIBLE;
            } catch (RuleViolationException ex) {
                if (fverbose) {
                    return new CompatibilityCheckResponse(false, ex.getMessage());
                } else {
                    return CompatibilityCheckResponse.IS_NOT_COMPATIBLE;
                }
            } catch (UnprocessableSchemaException ex) {
                throw new UnprocessableEntityException(ex.getMessage());
            }
        });
    }
}
