/*
 * Copyright 2020 Red Hat Inc
 * Copyright 2020 IBM
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

package io.apicurio.registry.asyncmem;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.PersistenceExceptionLivenessApply;
import io.apicurio.registry.metrics.PersistenceTimeoutReadinessApply;
import io.apicurio.registry.storage.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.ArtifactMetaDataDto;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.RegistryStorageException;
import io.apicurio.registry.storage.RuleAlreadyExistsException;
import io.apicurio.registry.storage.RuleConfigurationDto;
import io.apicurio.registry.storage.RuleNotFoundException;
import io.apicurio.registry.storage.StoredArtifact;
import io.apicurio.registry.storage.VersionNotFoundException;
import io.apicurio.registry.storage.impl.SimpleMapRegistryStorage;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.util.DtoUtil;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.metrics.annotation.ConcurrentGauge;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Timed;

import javax.enterprise.context.ApplicationScoped;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import static io.apicurio.registry.metrics.MetricIDs.STORAGE_CONCURRENT_OPERATION_COUNT;
import static io.apicurio.registry.metrics.MetricIDs.STORAGE_CONCURRENT_OPERATION_COUNT_DESC;
import static io.apicurio.registry.metrics.MetricIDs.STORAGE_GROUP_TAG;
import static io.apicurio.registry.metrics.MetricIDs.STORAGE_OPERATION_COUNT;
import static io.apicurio.registry.metrics.MetricIDs.STORAGE_OPERATION_COUNT_DESC;
import static io.apicurio.registry.metrics.MetricIDs.STORAGE_OPERATION_TIME;
import static io.apicurio.registry.metrics.MetricIDs.STORAGE_OPERATION_TIME_DESC;
import static org.eclipse.microprofile.metrics.MetricUnits.MILLISECONDS;

/**
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
@PersistenceExceptionLivenessApply
@PersistenceTimeoutReadinessApply
@Counted(name = STORAGE_OPERATION_COUNT + "_AsyncInMemoryRegistry", description = STORAGE_OPERATION_COUNT_DESC, tags = {"group=" + STORAGE_GROUP_TAG, "metric=" + STORAGE_OPERATION_COUNT}, reusable = true)
@ConcurrentGauge(name = STORAGE_CONCURRENT_OPERATION_COUNT + "_AsyncInMemoryRegistry", description = STORAGE_CONCURRENT_OPERATION_COUNT_DESC, tags = {"group=" + STORAGE_GROUP_TAG, "metric=" + STORAGE_CONCURRENT_OPERATION_COUNT}, reusable = true)
@Timed(name = STORAGE_OPERATION_TIME + "_AsyncInMemoryRegistry", description = STORAGE_OPERATION_TIME_DESC, tags = {"group=" + STORAGE_GROUP_TAG, "metric=" + STORAGE_OPERATION_TIME}, unit = MILLISECONDS, reusable = true)
@Logged
public class AsyncInMemoryRegistryStorage extends SimpleMapRegistryStorage {

    @ConfigProperty(name = "registry.asyncmem.delays.create", defaultValue = "500")
    long createDelay;
    @ConfigProperty(name = "registry.asyncmem.delays.update", defaultValue = "500")
    long updateDelay;
    @ConfigProperty(name = "registry.asyncmem.delays.delete", defaultValue = "500")
    long deleteDelay;
    
    private AtomicLong counter = new AtomicLong(1);
    private Map<String, Long> artifactCreation = new HashMap<>();
    private Map<Long, Long> globalCreation = new HashMap<>();
    
    private ExecutorService executor = Executors.newCachedThreadPool();
    
    @Override
    protected long nextGlobalId() {
        return counter.getAndIncrement();
    }

    /**
     * @see io.apicurio.registry.storage.impl.AbstractMapRegistryStorage#createArtifact(java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.content.ContentHandle)
     */
    @Override
    public CompletionStage<ArtifactMetaDataDto> createArtifact(String artifactId, ArtifactType artifactType,
            ContentHandle content) throws ArtifactAlreadyExistsException, RegistryStorageException {
        try {
            ArtifactMetaDataDto amdd = createOrUpdateArtifact(artifactId, artifactType, content, true, nextGlobalId());
            long globalId = amdd.getGlobalId();
            long creationTime = amdd.getCreatedOn() + createDelay;
            this.artifactCreation.put(artifactId, creationTime);
            this.globalCreation.put(globalId, creationTime);
            return CompletableFuture.completedFuture(amdd);
        } catch (ArtifactNotFoundException e) {
            throw new RegistryStorageException("Invalid state", e);
        }
    }

   // private static final Logger log = LoggerFactory.getLogger(AsyncInMemoryRegistryStorage.class);

    /**
     * @see io.apicurio.registry.storage.impl.AbstractMapRegistryStorage#createArtifactWithMetadata(java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.content.ContentHandle, io.apicurio.registry.storage.EditableArtifactMetaDataDto)
     */
    @Override
    public CompletionStage<ArtifactMetaDataDto> createArtifactWithMetadata(String artifactId, ArtifactType artifactType,
            ContentHandle content, EditableArtifactMetaDataDto metaData) throws ArtifactAlreadyExistsException, RegistryStorageException {
        return createArtifact(artifactId, artifactType, content).thenApply(amdd -> {
            this.executor.execute(() -> {
                preUpdateSleep();
                runWithErrorSuppression(() -> {
                    super.updateArtifactMetaData(artifactId, metaData);
                });
            });
            return DtoUtil.setEditableMetaDataInArtifact(amdd, metaData);
        });
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.AbstractRegistryStorage#createArtifactRule(java.lang.String, io.apicurio.registry.types.RuleType, io.apicurio.registry.storage.RuleConfigurationDto)
     */
    @Override
    public void createArtifactRule(String artifactId, RuleType rule, RuleConfigurationDto config)
            throws ArtifactNotFoundException, RuleAlreadyExistsException, RegistryStorageException {
        if (this.hasArtifactRule(artifactId, rule)) {
            throw new RuleAlreadyExistsException(rule);
        }
        this.executor.execute(() -> {
            preCreateSleep();
            runWithErrorSuppression(() -> {
                super.createArtifactRule(artifactId, rule, config);
            });
        });
    }

    /**
     * @see io.apicurio.registry.storage.impl.AbstractMapRegistryStorage#createGlobalRule(io.apicurio.registry.types.RuleType, io.apicurio.registry.storage.RuleConfigurationDto)
     */
    @Override
    public void createGlobalRule(RuleType rule, RuleConfigurationDto config)
            throws RuleAlreadyExistsException, RegistryStorageException {
        if (this.hasGlobalRule(rule)) {
            throw new RuleAlreadyExistsException(rule);
        }
        this.executor.execute(() -> {
            preCreateSleep();
            runWithErrorSuppression(() -> {
                super.createGlobalRule(rule, config);
            });
        });
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.AbstractMapRegistryStorage#updateArtifact(java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.content.ContentHandle)
     */
    @Override
    public CompletionStage<ArtifactMetaDataDto> updateArtifact(String artifactId, ArtifactType artifactType,
            ContentHandle content) throws ArtifactNotFoundException, RegistryStorageException {
        try {
            ArtifactMetaDataDto amdd = createOrUpdateArtifact(artifactId, artifactType, content, false, nextGlobalId());
            long globalId = amdd.getGlobalId();
            long creationTime = amdd.getCreatedOn() + updateDelay;
            this.globalCreation.put(globalId, creationTime);
            return CompletableFuture.completedFuture(amdd);
        } catch (ArtifactAlreadyExistsException e) {
            throw new RegistryStorageException("Invalid state", e);
        }
    }

    /**
     * @see io.apicurio.registry.storage.impl.AbstractMapRegistryStorage#updateArtifactWithMetadata(java.lang.String, io.apicurio.registry.types.ArtifactType, io.apicurio.registry.content.ContentHandle, io.apicurio.registry.storage.EditableArtifactMetaDataDto)
     */
    @Override
    public CompletionStage<ArtifactMetaDataDto> updateArtifactWithMetadata(String artifactId, ArtifactType artifactType,
                                                                           ContentHandle content, EditableArtifactMetaDataDto metaData) throws ArtifactAlreadyExistsException, RegistryStorageException {
        return updateArtifact(artifactId, artifactType, content).thenApply(amdd -> {
            this.executor.execute(() -> {
                preUpdateSleep();
                runWithErrorSuppression(() -> {
                    super.updateArtifactMetaData(artifactId, metaData);
                });
            });
            return DtoUtil.setEditableMetaDataInArtifact(amdd, metaData);
        });
    }

    /**
     * @see io.apicurio.registry.storage.impl.AbstractMapRegistryStorage#updateArtifactMetaData(java.lang.String, io.apicurio.registry.storage.EditableArtifactMetaDataDto)
     */
    @Override
    public void updateArtifactMetaData(String artifactId, EditableArtifactMetaDataDto metaData)
            throws ArtifactNotFoundException, RegistryStorageException {
        // Check if the artifact exists.
        this.getArtifactMetaData(artifactId);
        this.executor.execute(() -> {
            preUpdateSleep();
            runWithErrorSuppression(() -> {
                super.updateArtifactMetaData(artifactId, metaData);
            });
        });
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.AbstractMapRegistryStorage#updateArtifactState(java.lang.String, io.apicurio.registry.types.ArtifactState, java.lang.Integer)
     */
    @Override
    public void updateArtifactState(String artifactId, ArtifactState state, Integer version) {
        this.executor.execute(() -> {
            preUpdateSleep();
            runWithErrorSuppression(() -> {
                super.updateArtifactState(artifactId, state, version);
            });
        });
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.AbstractMapRegistryStorage#updateArtifactVersionMetaData(java.lang.String, long, io.apicurio.registry.storage.EditableArtifactMetaDataDto)
     */
    @Override
    public void updateArtifactVersionMetaData(String artifactId, long version,
            EditableArtifactMetaDataDto metaData)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        // Check if the artifact exists.
        this.getArtifactVersionMetaData(artifactId, version);
        this.executor.execute(() -> {
            preUpdateSleep();
            runWithErrorSuppression(() -> {
                super.updateArtifactVersionMetaData(artifactId, version, metaData);
            });
        });
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.AbstractMapRegistryStorage#updateArtifactRule(java.lang.String, io.apicurio.registry.types.RuleType, io.apicurio.registry.storage.RuleConfigurationDto)
     */
    @Override
    public void updateArtifactRule(String artifactId, RuleType rule, RuleConfigurationDto config)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        // Check if the artifact exists.
        this.getArtifactMetaData(artifactId);

        this.executor.execute(() -> {
            preUpdateSleep();
            runWithErrorSuppression(() -> {
                super.updateArtifactRule(artifactId, rule, config);
            });
        });
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.AbstractMapRegistryStorage#updateGlobalRule(io.apicurio.registry.types.RuleType, io.apicurio.registry.storage.RuleConfigurationDto)
     */
    @Override
    public void updateGlobalRule(RuleType rule, RuleConfigurationDto config)
            throws RuleNotFoundException, RegistryStorageException {
        this.executor.execute(() -> {
            preUpdateSleep();
            runWithErrorSuppression(() -> {
                super.updateGlobalRule(rule, config);
            });
        });
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.AbstractMapRegistryStorage#getArtifact(java.lang.String)
     */
    @Override
    public StoredArtifact getArtifact(String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        this.checkArtifactCreation(artifactId);
        return super.getArtifact(artifactId);
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.AbstractMapRegistryStorage#getArtifactMetaData(java.lang.String)
     */
    @Override
    public ArtifactMetaDataDto getArtifactMetaData(String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        this.checkArtifactCreation(artifactId);
        return super.getArtifactMetaData(artifactId);
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.AbstractMapRegistryStorage#getArtifactRules(java.lang.String)
     */
    @Override
    public List<RuleType> getArtifactRules(String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        this.checkArtifactCreation(artifactId);
        return super.getArtifactRules(artifactId);
    }

    /**
     * @see io.apicurio.registry.storage.impl.AbstractMapRegistryStorage#getArtifactMetaData(long)
     */
    @Override
    public ArtifactMetaDataDto getArtifactMetaData(long id)
            throws ArtifactNotFoundException, RegistryStorageException {
        this.checkGlobalCreation(id);
        return super.getArtifactMetaData(id);
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.AbstractMapRegistryStorage#getArtifactVersionMetaData(java.lang.String, boolean, io.apicurio.registry.content.ContentHandle)
     */
    @Override
    public ArtifactVersionMetaDataDto getArtifactVersionMetaData(String artifactId, boolean canonical,
            ContentHandle content) throws ArtifactNotFoundException, RegistryStorageException {
        this.checkArtifactCreation(artifactId);
        return super.getArtifactVersionMetaData(artifactId, canonical, content);
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.AbstractMapRegistryStorage#getArtifactRule(java.lang.String, io.apicurio.registry.types.RuleType)
     */
    @Override
    public RuleConfigurationDto getArtifactRule(String artifactId, RuleType rule)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        this.checkArtifactCreation(artifactId);
        return super.getArtifactRule(artifactId, rule);
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.AbstractMapRegistryStorage#getArtifactVersion(long)
     */
    @Override
    public StoredArtifact getArtifactVersion(long id)
            throws ArtifactNotFoundException, RegistryStorageException {
        this.checkGlobalCreation(id);
        return super.getArtifactVersion(id);
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.AbstractMapRegistryStorage#getArtifactVersion(java.lang.String, long)
     */
    @Override
    public StoredArtifact getArtifactVersion(String artifactId, long version)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        this.checkArtifactCreation(artifactId);
        return super.getArtifactVersion(artifactId, version);
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.AbstractMapRegistryStorage#getArtifactVersions(java.lang.String)
     */
    @Override
    public SortedSet<Long> getArtifactVersions(String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        this.checkArtifactCreation(artifactId);
        return super.getArtifactVersions(artifactId);
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.AbstractMapRegistryStorage#getArtifactVersionMetaData(java.lang.String, long)
     */
    @Override
    public ArtifactVersionMetaDataDto getArtifactVersionMetaData(String artifactId, long version)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        this.checkArtifactCreation(artifactId);
        return super.getArtifactVersionMetaData(artifactId, version);
    }
    
    private final void checkArtifactCreation(String artifactId) throws ArtifactNotFoundException {
        if (!this.artifactCreation.containsKey(artifactId)) {
            throw new ArtifactNotFoundException(artifactId);
        }
        long now = System.currentTimeMillis();
        long artifactTime = this.artifactCreation.get(artifactId);
        if (now < artifactTime) {
            throw new ArtifactNotFoundException(artifactId);
        }
    }
    
    private final void checkGlobalCreation(long globalId) throws ArtifactNotFoundException {
        if (!this.globalCreation.containsKey(globalId)) {
            throw new ArtifactNotFoundException(String.valueOf(globalId));
        }
        long now = System.currentTimeMillis();
        long globalTime = this.globalCreation.get(globalId);
        if (now < globalTime) {
            throw new ArtifactNotFoundException(String.valueOf(globalId));
        }
    }
    
    @Override
    public SortedSet<Long> deleteArtifact(String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        SortedSet<Long> rval = this.getArtifactVersions(artifactId);
        this.executor.execute(() -> {
            preDeleteSleep();
            runWithErrorSuppression(() -> {
                super.deleteArtifact(artifactId);
            });
        });
        return rval;
    }
    
    @Override
    public void deleteArtifactVersion(String artifactId, long version)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        // Check if the artifact exists.
        this.getArtifactVersionMetaData(artifactId, version);

        this.executor.execute(() -> {
            preDeleteSleep();
            runWithErrorSuppression(() -> {
                super.deleteArtifactVersion(artifactId, version);
            });
        });
    }
    
    @Override
    public void deleteArtifactVersionMetaData(String artifactId, long version)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        // Check if the artifact exists.
        this.getArtifactVersionMetaData(artifactId, version);

        this.executor.execute(() -> {
            preDeleteSleep();
            runWithErrorSuppression(() -> {
                super.deleteArtifactVersionMetaData(artifactId, version);
            });
        });
    }
    
    @Override
    public void deleteArtifactRule(String artifactId, RuleType rule)
            throws ArtifactNotFoundException, RuleNotFoundException, RegistryStorageException {
        // Check if the artifact exists.
        this.getArtifactMetaData(artifactId);

        this.executor.execute(() -> {
            preDeleteSleep();
            runWithErrorSuppression(() -> {
                super.deleteArtifactRule(artifactId, rule);
            });
        });
    }
    
    @Override
    public void deleteArtifactRules(String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        // Check if the artifact exists.
        this.getArtifactMetaData(artifactId);

        this.executor.execute(() -> {
            preDeleteSleep();
            runWithErrorSuppression(() -> {
                super.deleteArtifactRules(artifactId);
            });
        });
    }
    
    @Override
    public void deleteGlobalRule(RuleType rule) throws RuleNotFoundException, RegistryStorageException {
        // Check if the global rule exists.
        this.getGlobalRule(rule);

        this.executor.execute(() -> {
            preDeleteSleep();
            runWithErrorSuppression(() -> {
                super.deleteGlobalRule(rule);
            });
        });
    }
    
    @Override
    public void deleteGlobalRules() throws RegistryStorageException {
        this.executor.execute(() -> {
            preDeleteSleep();
            runWithErrorSuppression(() -> {
                super.deleteGlobalRules();
            });
        });
    }

    private boolean hasGlobalRule(RuleType rule) {
        try {
            this.getGlobalRule(rule);
            return true;
        } catch (RuleNotFoundException e) {
            return false;
        }
    }

    private boolean hasArtifactRule(String artifactId, RuleType rule) {
        try {
            this.getArtifactRule(artifactId, rule);
            return true;
        } catch (RuleNotFoundException e) {
            return false;
        }
    }

    private void preDeleteSleep() {
        doSleep(this.deleteDelay);
    }

    private void preUpdateSleep() {
        doSleep(this.updateDelay);
    }
    
    private void preCreateSleep() {
        doSleep(this.createDelay);
    }
    
    private void doSleep(long delay) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    protected void runWithErrorSuppression(Runnable command) {
        try {
            command.run();
        } catch (Throwable t) {
            // TODO log the error with e.g. TRACE or DEBUG level
        }
    }

    protected void runWithErrorSuppression(Runnable command, boolean reportError) {
        try {
            command.run();
        } catch (Throwable t) {
            if (reportError) {
                t.printStackTrace();
            }
        }
    }

}
