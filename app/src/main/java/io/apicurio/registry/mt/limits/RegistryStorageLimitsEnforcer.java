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

package io.apicurio.registry.mt.limits;

import java.util.List;
import java.util.concurrent.CompletionStage;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.microprofile.context.ThreadContext;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.RegistryStorageException;
import io.apicurio.registry.storage.VersionNotFoundException;
import io.apicurio.registry.storage.decorator.RegistryStorageDecorator;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.types.ArtifactType;

/**
 * @author Fabian Martinez
 */
@ApplicationScoped
public class RegistryStorageLimitsEnforcer extends RegistryStorageDecorator {

    @Inject
    ThreadContext threadContext;

    @Inject
    TenantLimitsService limitsService;

    @Inject
    TenantLimitsConfigurationService limitsConfiguration;

    /**
     * @see io.apicurio.registry.storage.decorator.RegistryStorageDecorator#isEnabled()
     */
    @Override
    public boolean isEnabled() {
        return limitsConfiguration.isConfigured();
    }

    /**
     * @see io.apicurio.registry.storage.decorator.RegistryStorageDecorator#order()
     */
    @Override
    public int order() {
        return 0;
    }

    /**
     * @see io.apicurio.registry.storage.decorator.RegistryStorageDecorator#createArtifact(java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.content.ContentHandle)
     */
    @Override
    public CompletionStage<ArtifactMetaDataDto> createArtifact(String groupId, String artifactId,
            String version, ArtifactType artifactType, ContentHandle content)
            throws ArtifactAlreadyExistsException, RegistryStorageException {

        LimitsCheckResult r = limitsService.canCreateArtifact(null);
        if (r.isAllowed()) {
            return threadContext.withContextCapture(super.createArtifact(groupId, artifactId, version, artifactType, content))
                    .whenComplete((a, ex) -> {
                        if (ex == null) {
                            limitsService.artifactCreated();
                        }
                    });
        } else {
            throw new LimitExceededException(r.getMessage());
        }

    }

    /**
     * @see io.apicurio.registry.storage.decorator.RegistryStorageDecorator#createArtifactWithMetadata(java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.content.ContentHandle, io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto)
     */
    @Override
    public CompletionStage<ArtifactMetaDataDto> createArtifactWithMetadata(String groupId, String artifactId,
            String version, ArtifactType artifactType, ContentHandle content,
            EditableArtifactMetaDataDto metaData)
            throws ArtifactAlreadyExistsException, RegistryStorageException {

        LimitsCheckResult r = limitsService.canCreateArtifact(metaData);
        if (r.isAllowed()) {
            return threadContext.withContextCapture(super.createArtifactWithMetadata(groupId, artifactId, version, artifactType, content, metaData))
                    .whenComplete((a, ex) -> {
                        if (ex == null) {
                            limitsService.artifactCreated();
                        }
                    });
        } else {
            throw new LimitExceededException(r.getMessage());
        }

    }

    /**
     * @see io.apicurio.registry.storage.decorator.RegistryStorageDecorator#updateArtifact(java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.content.ContentHandle)
     */
    @Override
    public CompletionStage<ArtifactMetaDataDto> updateArtifact(String groupId, String artifactId,
            String version, ArtifactType artifactType, ContentHandle content)
            throws ArtifactNotFoundException, RegistryStorageException {

        LimitsCheckResult r = limitsService.canCreateArtifactVersion(groupId, artifactId, null);
        if (r.isAllowed()) {
            return threadContext.withContextCapture(super.updateArtifact(groupId, artifactId, version, artifactType, content))
                    .whenComplete((a, ex) -> {
                        if (ex == null) {
                            limitsService.artifactVersionCreated(groupId, artifactId);
                        }
                    });
        } else {
            throw new LimitExceededException(r.getMessage());
        }
    }

    /**
     * @see io.apicurio.registry.storage.decorator.RegistryStorageDecorator#updateArtifactWithMetadata(java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.content.ContentHandle, io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto)
     */
    @Override
    public CompletionStage<ArtifactMetaDataDto> updateArtifactWithMetadata(String groupId, String artifactId,
            String version, ArtifactType artifactType, ContentHandle content,
            EditableArtifactMetaDataDto metaData) throws ArtifactNotFoundException, RegistryStorageException {

        LimitsCheckResult r = limitsService.canCreateArtifactVersion(groupId, artifactId, metaData);
        if (r.isAllowed()) {
            return threadContext.withContextCapture(super.updateArtifactWithMetadata(groupId, artifactId, version, artifactType, content, metaData))
                    .whenComplete((a, ex) -> {
                        if (ex == null) {
                            limitsService.artifactVersionCreated(groupId, artifactId);
                        }
                    });
        } else {
            throw new LimitExceededException(r.getMessage());
        }
    }

    /**
     * @see io.apicurio.registry.storage.decorator.RegistryStorageDecorator#updateArtifactMetaData(java.lang.String, java.lang.String, io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto)
     */
    @Override
    public void updateArtifactMetaData(String groupId, String artifactId,
            EditableArtifactMetaDataDto metaData) throws ArtifactNotFoundException, RegistryStorageException {

        LimitsCheckResult r = limitsService.checkMetaData(metaData);
        if (r.isAllowed()) {
            super.updateArtifactMetaData(groupId, artifactId, metaData);
        } else {
            throw new LimitExceededException(r.getMessage());
        }

    }

    /**
     * @see io.apicurio.registry.storage.decorator.RegistryStorageDecorator#updateArtifactVersionMetaData(java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto)
     */
    @Override
    public void updateArtifactVersionMetaData(String groupId, String artifactId, String version,
            EditableArtifactMetaDataDto metaData)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {

        LimitsCheckResult r = limitsService.checkMetaData(metaData);
        if (r.isAllowed()) {
            super.updateArtifactVersionMetaData(groupId, artifactId, version, metaData);
        } else {
            throw new LimitExceededException(r.getMessage());
        }

    }

    /**
     * @see io.apicurio.registry.storage.decorator.RegistryStorageDecorator#deleteArtifact(java.lang.String, java.lang.String)
     */
    @Override
    public List<String> deleteArtifact(String groupId, String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        List<String> ids = super.deleteArtifact(groupId, artifactId);
        limitsService.artifactDeleted();
        return ids;
    }

    /**
     * @see io.apicurio.registry.storage.decorator.RegistryStorageDecorator#deleteArtifacts(java.lang.String)
     */
    @Override
    public void deleteArtifacts(String groupId) throws RegistryStorageException {
        super.deleteArtifacts(groupId);
        limitsService.artifactDeleted();
    }

    /**
     * @see io.apicurio.registry.storage.decorator.RegistryStorageDecorator#deleteArtifactVersion(java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public void deleteArtifactVersion(String groupId, String artifactId, String version)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        super.deleteArtifactVersion(groupId, artifactId, version);
        limitsService.artifactVersionDeleted(groupId, artifactId);
    }

}
