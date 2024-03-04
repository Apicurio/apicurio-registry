package io.apicurio.registry.limits;

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import org.eclipse.microprofile.context.ThreadContext;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.decorator.RegistryStorageDecorator;
import io.apicurio.registry.storage.decorator.RegistryStorageDecoratorBase;
import io.apicurio.registry.storage.decorator.RegistryStorageDecoratorOrderConstants;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.EditableVersionMetaDataDto;
import io.apicurio.registry.storage.error.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.storage.error.VersionNotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Decorator of {@link RegistryStorage} that applies limits enforcement, with this is possible to limit how many artifacts can be created in registry...
 * All of that is abstracted with the LimitsService and the LimitsConfigurationService
 *
 */
@ApplicationScoped
// TODO Importing is not covered under limits!
public class RegistryStorageLimitsEnforcer extends RegistryStorageDecoratorBase implements RegistryStorageDecorator {

    @Inject
    ThreadContext threadContext;

    @Inject
    RegistryLimitsService limitsService;

    @Inject
    RegistryLimitsConfigurationProducer limitsConfiguration;

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
        return RegistryStorageDecoratorOrderConstants.LIMITS_ENFORCER_DECORATOR;
    }

    /**
     * @see io.apicurio.registry.storage.decorator.RegistryStorageDecorator#createArtifact (java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.content.ContentHandle, java.util.List)
     */
    @Override
    public ArtifactVersionMetaDataDto createArtifact(String groupId, String artifactId,
                                              String version, String artifactType, ContentHandle content, List<ArtifactReferenceDto> references)
            throws ArtifactAlreadyExistsException, RegistryStorageException {

        ArtifactVersionMetaDataDto dto = withLimitsCheck(() -> limitsService.canCreateArtifact(null, content))
                .execute(() -> super.createArtifact(groupId, artifactId, version, artifactType, content, references));
        limitsService.artifactCreated();
        return dto;
    }

    /**
     * @see io.apicurio.registry.storage.decorator.RegistryStorageDecorator#createArtifactWithMetadata (java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.content.ContentHandle, io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto, java.util.List)
     */
    @Override
    public ArtifactVersionMetaDataDto createArtifactWithMetadata(String groupId, String artifactId,
            String version, String artifactType, ContentHandle content,
            EditableArtifactMetaDataDto metaData, List<ArtifactReferenceDto> references)
            throws ArtifactAlreadyExistsException, RegistryStorageException
    {

        ArtifactVersionMetaDataDto dto = withLimitsCheck(() -> limitsService.canCreateArtifact(metaData, content))
                .execute(() -> super.createArtifactWithMetadata(groupId, artifactId, version, artifactType, content, metaData, references));
        limitsService.artifactCreated();
        return dto;
    }

    /**
     * @see io.apicurio.registry.storage.decorator.RegistryStorageDecorator#updateArtifact (java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.content.ContentHandle)
     */
    @Override
    public ArtifactVersionMetaDataDto createArtifactVersion(String groupId, String artifactId,
            String version, String artifactType, ContentHandle content, List<ArtifactReferenceDto> references)
            throws ArtifactNotFoundException, RegistryStorageException
    {
        ArtifactVersionMetaDataDto dto = withLimitsCheck(() -> limitsService.canCreateArtifactVersion(groupId, artifactId, null, content))
                .execute(() -> super.createArtifactVersion(groupId, artifactId, version, artifactType, content, references));
        limitsService.artifactVersionCreated(groupId, artifactId);
        return dto;
    }

    @Override
    public ArtifactVersionMetaDataDto createArtifactVersionWithMetadata(String groupId, String artifactId,
            String version, String artifactType, ContentHandle content,
            EditableVersionMetaDataDto metaData, List<ArtifactReferenceDto> references) throws ArtifactNotFoundException, RegistryStorageException
    {
        ArtifactVersionMetaDataDto dto = withLimitsCheck(() -> limitsService.canCreateArtifactVersion(groupId, artifactId, metaData, content))
                .execute(() -> super.createArtifactVersionWithMetadata(groupId, artifactId, version, artifactType, content, metaData, references));
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
     * @see io.apicurio.registry.storage.decorator.RegistryStorageDecoratorBase#updateArtifactVersionMetaData(java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.storage.dto.EditableVersionMetaDataDto)
     */
    @Override
    public void updateArtifactVersionMetaData(String groupId, String artifactId, String version,
            EditableVersionMetaDataDto metaData)
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
     * Notice the "threadContext.withContextCapture" because of using CompletionStage it's possible that certain operations may be executed in different threads.
     * We need context propagation to move the ThreadLocale context
     * from one thread to another, that's why we use withContextCapture
     *
     * @param checker
     * @return
     */
    public LimitedActionExecutor withLimitsCheck(LimitsChecker checker) {
        return new LimitedActionExecutor() {
            @SuppressWarnings({"unchecked", "rawtypes"})
            @Override
            public <T> T execute(LimitedAction<T> action) {
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
    private interface LimitsChecker extends Supplier<LimitsCheckResult> {
    }


    @FunctionalInterface
    private interface LimitedAction<T> extends Supplier<T> {
    }


    @FunctionalInterface
    private interface LimitedActionExecutor {

        <T> T execute(LimitedAction<T> action);
    }
}
