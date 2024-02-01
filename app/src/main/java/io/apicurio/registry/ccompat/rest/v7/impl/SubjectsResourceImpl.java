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
import io.apicurio.registry.ccompat.dto.SchemaInfo;
import io.apicurio.registry.ccompat.rest.error.SchemaNotFoundException;
import io.apicurio.registry.ccompat.rest.error.SubjectNotSoftDeletedException;
import io.apicurio.registry.ccompat.rest.error.SubjectSoftDeletedException;
import io.apicurio.registry.ccompat.rest.v7.SubjectsResource;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.SearchedArtifactDto;
import io.apicurio.registry.storage.dto.StoredArtifactDto;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.InvalidArtifactStateException;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.util.VersionUtil;
import jakarta.interceptor.Interceptors;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_ARTIFACT_ID;
import static io.apicurio.registry.storage.RegistryStorage.ArtifactRetrievalBehavior.DEFAULT;

/**
 * @author Carles Arnal
 */
@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
@Logged
public class SubjectsResourceImpl extends AbstractResource implements SubjectsResource {

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public List<String> listSubjects(String subjectPrefix, Boolean deleted, String groupId) {
        //Since contexts are not supported, subjectPrefix is not used
        final boolean fdeleted = deleted == null ? Boolean.FALSE : deleted;
        return storage.searchArtifacts(Set.of(SearchFilter.ofGroup(groupId)), OrderBy.createdOn, OrderDirection.asc, 0, cconfig.maxSubjects.get()).getArtifacts().stream().filter(searchedArtifactDto -> isCcompatManagedType(searchedArtifactDto.getType()) && shouldFilterState(fdeleted, searchedArtifactDto.getState())).map(SearchedArtifactDto::getId).collect(Collectors.toList());
    }

    @Override
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Read)
    public Schema findSchemaByContent(String subject, SchemaInfo request, Boolean normalize, String groupId, Boolean deleted) throws Exception {
        if (doesArtifactExist(subject, groupId)) {
            final boolean fnormalize = normalize == null ? Boolean.FALSE : normalize;
            final boolean fdeleted = deleted == null ? Boolean.FALSE : deleted;

            try {
                ArtifactVersionMetaDataDto amd;
                amd = lookupSchema(groupId, subject, request.getSchema(), request.getReferences(), request.getSchemaType(), fnormalize);
                if (amd.getState() != ArtifactState.DISABLED || fdeleted) {
                    StoredArtifactDto storedArtifact = storage.getArtifactVersion(groupId, subject, amd.getVersion());
                    return converter.convert(subject, storedArtifact);
                } else {
                    throw new SchemaNotFoundException(String.format("The given schema does not match any schema under the subject %s", subject));
                }
            } catch (ArtifactNotFoundException anf) {
                throw new SchemaNotFoundException(String.format("The given schema does not match any schema under the subject %s", subject));
            }
        } else {
            //If the artifact does not exist there is no need for looking up the schema, just fail.
            throw new ArtifactNotFoundException(groupId, subject);
        }
    }

    @Override
    @Audited(extractParameters = {"0", KEY_ARTIFACT_ID})
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Write)
    public List<Integer> deleteSubject(String subject, Boolean permanent, String groupId) throws Exception {
        final boolean fpermanent = permanent == null ? Boolean.FALSE : permanent;
        if (fpermanent) {
            return deleteSubjectPermanent(groupId, subject);
        } else if (isArtifactActive(subject, groupId, DEFAULT)) {
            return deleteSubjectVersions(groupId, subject);
        } else {
            if (storage.isArtifactExists(groupId, subject)) {
                //The artifact exist, it's in DISABLED state but the delete request is set to not permanent, throw ex.
                throw new SubjectSoftDeletedException(String.format("Subject %s is in soft deleted state.", subject));
            } else {
                return Collections.emptyList();
            }
        }
    }

    private List<Integer> deleteSubjectPermanent(String groupId, String subject) {
        if (isArtifactActive(subject, groupId, DEFAULT)) {
            throw new SubjectNotSoftDeletedException(String.format("Subject %s must be soft deleted first", subject));
        } else {
            return storage.deleteArtifact(groupId, subject).stream().map(VersionUtil::toInteger).map(converter::convertUnsigned).collect(Collectors.toList());
        }
    }

    //Deleting artifact versions means updating all the versions status to DISABLED.
    private List<Integer> deleteSubjectVersions(String groupId, String subject) {
        List<String> deletedVersions = storage.getArtifactVersions(groupId, subject);
        try {
            deletedVersions.forEach(version -> storage.updateArtifactState(groupId, subject, version, ArtifactState.DISABLED));
        } catch (InvalidArtifactStateException ignored) {
            log.warn("Invalid artifact state transition", ignored);
        }
        return deletedVersions.stream().map(VersionUtil::toLong).map(converter::convertUnsigned).sorted().collect(Collectors.toList());
    }
}
