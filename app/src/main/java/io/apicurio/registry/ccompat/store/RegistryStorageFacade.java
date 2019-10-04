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
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.RegistryStorageException;
import io.apicurio.registry.storage.VersionNotFoundException;

import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.CompletionStage;

/**
 * @author Ales Justin
 */
public interface RegistryStorageFacade {
    Set<String> listSubjects();

    SortedSet<Long> deleteSubject(String subject) throws ArtifactNotFoundException, RegistryStorageException;

    String getSchema(Integer id) throws ArtifactNotFoundException, RegistryStorageException;;

    Schema getSchema(String subject, String version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException;;

    List<Integer> listVersions(String subject) throws ArtifactNotFoundException, RegistryStorageException;

    Schema findSchemaWithSubject(String subject, boolean checkDeletedSchema, String schema) throws ArtifactNotFoundException, RegistryStorageException;;

    /**
     * @return global id as future
     */
    CompletionStage<Long> registerSchema(String subject, Integer id, Integer version, String schema) throws ArtifactAlreadyExistsException, ArtifactNotFoundException, RegistryStorageException;

    /**
     * @return schema version as long
     */
    long deleteSchema(String subject, String version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException;
}
