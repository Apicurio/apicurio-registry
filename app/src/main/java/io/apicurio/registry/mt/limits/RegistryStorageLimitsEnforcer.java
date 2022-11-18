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

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.RegistryStorageException;
import io.apicurio.registry.storage.VersionNotFoundException;
import io.apicurio.registry.storage.decorator.RegistryStorageDecorator;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.GroupSearchResultsDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.types.RegistryException;
import org.eclipse.microprofile.context.ThreadContext;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

/**
 * Decorator of {@link RegistryStorage} that applies per-tenant limits enforcement, with this is possible to limit how many artifacts a tenant can create...
 * This feature can also be applied to non multitenancy scenarios, there is the notion of default tenant that can have limits configuration.
 * All of that is abstracted with the TenantLimitsService and the TenantLimitsConfigurationService
 *
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
     * @see io.apicurio.registry.storage.decorator.RegistryStorageDecorator#createArtifact (java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.content.ContentHandle, java.util.List)
     */
    @Override
    public ArtifactMetaDataDto createArtifact(String groupId, String artifactId,
                                              String version, String artifactType, ContentHandle content, List<ArtifactReferenceDto> references)
            throws ArtifactAlreadyExistsException, RegistryStorageException {

        ArtifactMetaDataDto dto = withLimitsCheck(() -> limitsService.canCreateArtifact(null, content))
                .execute(() -> super.createArtifact(groupId, artifactId, version, artifactType, content, references));
        limitsService.artifactCreated();
        return dto;
    }

    /**
     * @see io.apicurio.registry.storage.decorator.RegistryStorageDecorator#createArtifactWithMetadata (java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.content.ContentHandle, io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto, java.util.List)
     */
    @Override
    public ArtifactMetaDataDto createArtifactWithMetadata(String groupId, String artifactId,
            String version, String artifactType, ContentHandle content,
            EditableArtifactMetaDataDto metaData, List<ArtifactReferenceDto> references)
            throws ArtifactAlreadyExistsException, RegistryStorageException {

        ArtifactMetaDataDto dto = withLimitsCheck(() -> limitsService.canCreateArtifact(metaData, content))
                .execute(() -> super.createArtifactWithMetadata(groupId, artifactId, version, artifactType, content, metaData, references));
        limitsService.artifactCreated();
        return dto;
    }

    /**
     * @see io.apicurio.registry.storage.decorator.RegistryStorageDecorator#updateArtifact (java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.content.ContentHandle)
     */
    @Override
    public ArtifactMetaDataDto updateArtifact(String groupId, String artifactId,
            String version, String artifactType, ContentHandle content, List<ArtifactReferenceDto> references)
            throws ArtifactNotFoundException, RegistryStorageException {

        ArtifactMetaDataDto dto = withLimitsCheck(() -> limitsService.canCreateArtifactVersion(groupId, artifactId, null, content))
                .execute(() -> super.updateArtifact(groupId, artifactId, version, artifactType, content, references));
        limitsService.artifactVersionCreated(groupId, artifactId);
        return dto;
    }

    /**
     * @see io.apicurio.registry.storage.decorator.RegistryStorageDecorator#updateArtifactWithMetadata (java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.content.ContentHandle, io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto)
     */
    @Override
    public ArtifactMetaDataDto updateArtifactWithMetadata(String groupId, String artifactId,
            String version, String artifactType, ContentHandle content,
            EditableArtifactMetaDataDto metaData, List<ArtifactReferenceDto> references) throws ArtifactNotFoundException, RegistryStorageException {

        ArtifactMetaDataDto dto = withLimitsCheck(() -> limitsService.canCreateArtifactVersion(groupId, artifactId, metaData, content))
                .execute(() -> super.updateArtifactWithMetadata(groupId, artifactId, version, artifactType, content, metaData, references));
        limitsService.artifactVersionCreated(groupId, artifactId);
        return dto;
    }

    /**
     * @see io.apicurio.registry.storage.decorator.RegistryStorageDecorator#updateArtifactMetaData(java.lang.String, java.lang.String, io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto)
     */
    @Override
    public void updateArtifactMetaData(String groupId, String artifactId,
            EditableArtifactMetaDataDto metaData) throws ArtifactNotFoundException, RegistryStorageException {

        withLimitsCheck(() -> limitsService.checkMetaData(metaData))
                .execute(() -> {
                    super.updateArtifactMetaData(groupId, artifactId, metaData);
                    return null;
                });

    }

    /**
     * @see io.apicurio.registry.storage.decorator.RegistryStorageDecorator#updateArtifactVersionMetaData(java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto)
     */
    @Override
    public void updateArtifactVersionMetaData(String groupId, String artifactId, String version,
            EditableArtifactMetaDataDto metaData)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {

        withLimitsCheck(() -> limitsService.checkMetaData(metaData))
            .execute(() -> {
                super.updateArtifactVersionMetaData(groupId, artifactId, version, metaData);
                return null;
            });
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

    /**
     * @see io.apicurio.registry.storage.decorator.RegistryStorageDecorator#resolveReferences(List)
     */
    @Override
    public Map<String, ContentHandle> resolveReferences(List<ArtifactReferenceDto> references) {
        return delegate.resolveReferences(references);
    }

    /**
     * @see io.apicurio.registry.storage.decorator.RegistryStorageDecorator#searchGroups(Set, OrderBy, OrderDirection, Integer, Integer)
     */
    @Override
    public GroupSearchResultsDto searchGroups(Set<SearchFilter> filters, OrderBy orderBy, OrderDirection orderDirection, Integer offset, Integer limit) {
        return delegate.searchGroups(filters, orderBy, orderDirection, offset, limit);
    }

    /**
     * Notice the "threadContext.withContextCapture" because of using CompletionStage it's possible that certain operations may be executed in different threads.
     * But we have the TenantContext that stores per-tenant configurations in a ThreadLocale variable. We need context propagation to move the ThreadLocale context
     * from one thread to another, that's why we use withContextCapture
     * @param checker
     * @return
     */
    public ActionProvider withLimitsCheck(LimitsChecker checker) {
        return new ActionProvider() {
            @SuppressWarnings({ "unchecked", "rawtypes" })
            @Override
            public <T> T execute(LimitedAction<T> action) throws RegistryException {
                LimitsCheckResult r = checker.get();
                if (r.isAllowed()) {
                    T result = action.get();
                    if (result instanceof CompletionStage) {
                        result = (T) threadContext.withContextCapture((CompletionStage) result);
                    }
                    return result;
                } else {
                    throw new LimitExceededException(r.getMessage());
                }
            }
        };
    }

    @FunctionalInterface
    private static interface LimitsChecker extends Supplier<LimitsCheckResult> {
    }

    @FunctionalInterface
    private static interface LimitedAction<T> {
        T get() throws RegistryException;
    }

    @FunctionalInterface
    private static interface ActionProvider  {
        public <T> T execute(LimitedAction<T> action) throws RegistryException;
    }

}
