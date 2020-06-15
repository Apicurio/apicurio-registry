/*
 * Copyright 2020 Red Hat Inc
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

import static io.apicurio.registry.metrics.MetricIDs.STORAGE_CONCURRENT_OPERATION_COUNT;
import static io.apicurio.registry.metrics.MetricIDs.STORAGE_CONCURRENT_OPERATION_COUNT_DESC;
import static io.apicurio.registry.metrics.MetricIDs.STORAGE_GROUP_TAG;
import static io.apicurio.registry.metrics.MetricIDs.STORAGE_OPERATION_COUNT;
import static io.apicurio.registry.metrics.MetricIDs.STORAGE_OPERATION_COUNT_DESC;
import static io.apicurio.registry.metrics.MetricIDs.STORAGE_OPERATION_TIME;
import static io.apicurio.registry.metrics.MetricIDs.STORAGE_OPERATION_TIME_DESC;
import static org.eclipse.microprofile.metrics.MetricUnits.MILLISECONDS;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import javax.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.metrics.annotation.ConcurrentGauge;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Timed;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.PersistenceExceptionLivenessApply;
import io.apicurio.registry.metrics.PersistenceTimeoutReadinessApply;
import io.apicurio.registry.storage.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.ArtifactMetaDataDto;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.RegistryStorageException;
import io.apicurio.registry.storage.RuleConfigurationDto;
import io.apicurio.registry.storage.RuleNotFoundException;
import io.apicurio.registry.storage.StoredArtifact;
import io.apicurio.registry.storage.VersionNotFoundException;
import io.apicurio.registry.storage.impl.SimpleMapRegistryStorage;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;

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

    @ConfigProperty(name = "registry.asyncmem.delays.create", defaultValue = "1000")
    long createDelay;
    @ConfigProperty(name = "registry.asyncmem.delays.update", defaultValue = "1000")
    long updateDelay;
    @ConfigProperty(name = "registry.asyncmem.delays.delete", defaultValue = "1000")
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
     * @see io.apicurio.registry.storage.impl.AbstractMapRegistryStorage#getArtifactMetaData(java.lang.String, io.apicurio.registry.content.ContentHandle)
     */
    @Override
    public ArtifactMetaDataDto getArtifactMetaData(String artifactId, ContentHandle content)
            throws ArtifactNotFoundException, RegistryStorageException {
        this.checkArtifactCreation(artifactId);
        return super.getArtifactMetaData(artifactId, content);
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
        this.executor.execute(() -> {
            preDeleteSleep();
            runWithErrorSuppression(() -> {
                super.deleteArtifactRules(artifactId);
            });
        });
    }
    
    @Override
    public void deleteGlobalRule(RuleType rule) throws RuleNotFoundException, RegistryStorageException {
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

    private void preDeleteSleep() {
        try {
            Thread.sleep(this.deleteDelay);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
//
//    private void preUpdateSleep() {
//        try {
//            Thread.sleep(this.updateDelay);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//    }
    
    private void runWithErrorSuppression(Runnable command) {
        try {
            command.run();
        } catch (Throwable t) {
            // TODO log the error with e.g. TRACE or DEBUG level
        }
    }
    
}
