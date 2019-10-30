/*
 * Copyright 2019 Red Hat
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

package io.apicurio.registry.ccompat.store;

import io.apicurio.registry.ccompat.dto.Schema;
import io.apicurio.registry.storage.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.ArtifactMetaDataDto;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.RegistryStorageException;
import io.apicurio.registry.storage.StoredArtifact;
import io.apicurio.registry.storage.VersionNotFoundException;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.Current;

import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

/**
 * @author Ales Justin
 */
@ApplicationScoped
public class RegistryStorageFacadeImpl implements RegistryStorageFacade {

    @Inject
    @Current
    RegistryStorage storage;

    private static Schema toSchema(String subject, StoredArtifact storedArtifact) {
        return new Schema(
            subject,
            storedArtifact.version.intValue(),
            storedArtifact.id.intValue(),
            storedArtifact.content
        );
    }

    public Set<String> listSubjects() {
        return storage.getArtifactIds();
    }

    @Override
    public SortedSet<Long> deleteSubject(String subject) throws ArtifactNotFoundException, RegistryStorageException {
        return storage.deleteArtifact(subject);
    }

    @Override
    public String getSchema(Integer id) throws ArtifactNotFoundException, RegistryStorageException {
        return storage.getArtifactVersion(id).content;
    }

    @Override
    public Schema getSchema(String subject, String versionString) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        try {
            long version = Long.parseLong(versionString);
            return toSchema(subject, storage.getArtifactVersion(subject, version));
        } catch (NumberFormatException e) {
            // return latest
            return toSchema(subject, storage.getArtifact(subject));
        }
    }

    @Override
    public List<Integer> listVersions(String subject) throws ArtifactNotFoundException, RegistryStorageException {
        return storage.getArtifactVersions(subject)
                      .stream()
                      .map(Long::intValue)
                      .collect(Collectors.toList());
    }

    @Override
    public Schema findSchemaWithSubject(String subject, boolean checkDeletedSchema, String schema) throws ArtifactNotFoundException, RegistryStorageException {
        // TODO -- handle deleted?
        ArtifactMetaDataDto amd = storage.getArtifactMetaData(subject, schema);
        StoredArtifact storedArtifact = storage.getArtifactVersion(subject, amd.getVersion());
        return toSchema(subject, storedArtifact);
    }

    @Override
    public CompletionStage<Long> registerSchema(String subject, Integer id, Integer version, String schema) throws ArtifactAlreadyExistsException, ArtifactNotFoundException, RegistryStorageException {
        ArtifactMetaDataDto metadata = null;
        try {
            metadata = storage.getArtifactMetaData(subject);
        } catch (ArtifactNotFoundException ignored) {
        }
        CompletionStage<ArtifactMetaDataDto> cs;
        if (metadata == null) {
            cs = storage.createArtifact(subject, ArtifactType.AVRO, schema);
        } else {
            cs = storage.updateArtifact(subject, ArtifactType.AVRO, schema);
        }
        return cs.thenApply(ArtifactMetaDataDto::getGlobalId);
    }

    @Override
    public long deleteSchema(String subject, String versionString) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        try {
            long version = Long.parseLong(versionString);
            storage.deleteArtifactVersion(subject, version);
            return version;
        } catch (NumberFormatException e) {
            // delete latest
            SortedSet<Long> versions = storage.getArtifactVersions(subject);
            Long latestVersion = versions.last();
            storage.deleteArtifactVersion(subject, latestVersion);
            return latestVersion.intValue();
        }
    }
}
