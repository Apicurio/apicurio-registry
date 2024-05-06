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
import io.apicurio.common.apps.logging.audit.Audited;
import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.ccompat.dto.Schema;
import io.apicurio.registry.ccompat.dto.SchemaInfo;
import io.apicurio.registry.ccompat.rest.error.SchemaNotFoundException;
import io.apicurio.registry.ccompat.rest.v6.SubjectsResource;
import io.apicurio.registry.ccompat.rest.v7.impl.AbstractResource;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.SearchedArtifactDto;
import io.apicurio.registry.storage.dto.StoredArtifactDto;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.util.VersionUtil;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptors;
import org.slf4j.Logger;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_ARTIFACT_ID;

/**
 * @author Ales Justin
 * @author Jakub Senko <em>m@jsenko.net</em>
 */
@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
@Logged
public class SubjectsResourceImpl extends AbstractResource implements SubjectsResource {

    @Inject
    Logger logger;

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public List<String> listSubjects() {
        logger.warn("The Confluent V6 compatibility API is deprecated and will be removed in future versions");
        return getStorage().searchArtifacts(Set.of(SearchFilter.ofGroup(null)), OrderBy.createdOn, OrderDirection.asc, 0, getCconfig().getMaxSubjects().get()).getArtifacts().stream().filter(searchedArtifactDto -> isCcompatManagedType(searchedArtifactDto.getType()) && shouldFilterState(false, searchedArtifactDto.getState())).map(SearchedArtifactDto::getId).collect(Collectors.toList());
    }

    @Override
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Read)
    public Schema findSchemaByContent(String subject, SchemaInfo request) throws Exception {
        logger.warn("The Confluent V6 compatibility API is deprecated and will be removed in future versions");
        if (doesArtifactExist(subject, null)) {
            try {
                ArtifactVersionMetaDataDto amd;
                amd = lookupSchema(null, subject, request.getSchema(), request.getReferences(), request.getSchemaType(), false);
                if (amd.getState() != ArtifactState.DISABLED) {
                    StoredArtifactDto storedArtifact = getStorage().getArtifactVersion(null, subject, amd.getVersion());
                    return getConverter().convert(subject, storedArtifact);
                } else {
                    throw new SchemaNotFoundException(String.format("The given schema does not match any schema under the subject %s", subject));
                }
            } catch (ArtifactNotFoundException anf) {
                throw new SchemaNotFoundException(String.format("The given schema does not match any schema under the subject %s", subject));
            }
        } else {
            //If the artifact does not exist there is no need for looking up the schema, just fail.
            throw new ArtifactNotFoundException(null, subject);
        }
    }

    @Override
    @Audited(extractParameters = {"0", KEY_ARTIFACT_ID})
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Write)
    public List<Integer> deleteSubject(String subject) throws Exception {
        logger.warn("The Confluent V6 compatibility API is deprecated and will be removed in future versions");
        return deleteSubjectPermanent(null, subject);
    }

    private List<Integer> deleteSubjectPermanent(String groupId, String subject) {
        return getStorage().deleteArtifact(groupId, subject).stream().map(VersionUtil::toInteger).map(getConverter()::convertUnsigned).collect(Collectors.toList());
    }
}
