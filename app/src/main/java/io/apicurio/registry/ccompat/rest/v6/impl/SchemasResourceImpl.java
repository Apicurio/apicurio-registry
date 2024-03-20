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

import io.apicurio.common.apps.logging.Logged;
import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.ccompat.dto.SchemaInfo;
import io.apicurio.registry.ccompat.dto.SubjectVersion;
import io.apicurio.registry.ccompat.rest.v6.SchemasResource;
import io.apicurio.registry.ccompat.rest.v7.impl.AbstractResource;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.ContentAndReferencesDto;
import io.apicurio.registry.storage.dto.StoredArtifactDto;
import io.apicurio.registry.storage.impl.sql.RegistryContentUtils;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.util.ArtifactTypeUtil;
import jakarta.interceptor.Interceptors;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static io.apicurio.registry.storage.RegistryStorage.ArtifactRetrievalBehavior.DEFAULT;

/**
 * @author Ales Justin
 * @author Jakub Senko <em>m@jsenko.net</em>
 */
@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
@Logged
public class SchemasResourceImpl extends AbstractResource implements SchemasResource {

    @Override
    @Authorized(style = AuthorizedStyle.GlobalId, level = AuthorizedLevel.Read)
    public SchemaInfo getSchema(int id) {
        ContentHandle contentHandle;
        List<ArtifactReferenceDto> references;
        if (getCconfig().getLegacyIdModeEnabled().get()) {
            StoredArtifactDto artifactVersion = getStorage().getArtifactVersion(id);
            contentHandle = artifactVersion.getContent();
            references = artifactVersion.getReferences();
        } else {
            ContentAndReferencesDto contentAndReferences = getStorage().getArtifactByContentId(id);
            contentHandle = getStorage().getArtifactByContentId(id).getContent();
            references = contentAndReferences.getReferences();
            List<ArtifactMetaDataDto> artifacts = getStorage().getArtifactVersionsByContentId(id);
            if (artifacts == null || artifacts.isEmpty()) {
                //the contentId points to an orphaned content
                throw new ArtifactNotFoundException("ContentId: " + id);
            }
        }
        return getConverter().convert(contentHandle,
                ArtifactTypeUtil.determineArtifactType(
                        contentHandle,
                        null,
                        null,
                        RegistryContentUtils.recursivelyResolveReferences(references, r -> getStorage().getContentByReference(r)),
                        getFactory().getAllArtifactTypes()
                ),
                references);
    }

    @Override
    @Authorized(style = AuthorizedStyle.GlobalId, level = AuthorizedLevel.Read)
    public List<SubjectVersion> getSubjectVersions(int id) {
        if (getCconfig().getLegacyIdModeEnabled().get()) {
            ArtifactMetaDataDto artifactMetaData = getStorage().getArtifactMetaData(id);
            return Collections.singletonList(getConverter().convert(artifactMetaData.getId(), artifactMetaData.getVersionId()));
        }

        return getStorage().getArtifactVersionsByContentId(id)
                .stream()
                .filter(artifactMetaData -> isArtifactActive(artifactMetaData.getId(), artifactMetaData.getGroupId(), DEFAULT))
                .map(artifactMetaData -> getConverter().convert(artifactMetaData.getId(), artifactMetaData.getVersionId()))
                .collect(Collectors.toList());
    }

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public List<String> getRegisteredTypes() {
        return Arrays.asList(ArtifactType.JSON, ArtifactType.PROTOBUF, ArtifactType.AVRO);
    }
}
