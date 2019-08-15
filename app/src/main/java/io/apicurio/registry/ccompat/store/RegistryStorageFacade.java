package io.apicurio.registry.ccompat.store;

import io.apicurio.registry.ccompat.dto.RegisterSchemaRequest;
import io.apicurio.registry.ccompat.dto.Schema;
import io.apicurio.registry.storage.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.RegistryStorageException;
import io.apicurio.registry.storage.VersionNotFoundException;

import java.util.List;
import java.util.Set;
import java.util.SortedSet;

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
     * @return global id
     */
    long registerSchema(String subject, Integer id, Integer version, String schema) throws ArtifactAlreadyExistsException, ArtifactNotFoundException, RegistryStorageException;

    /**
     * @return schema version as long
     */
    long deleteSchema(String subject, String version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException;

    boolean testCompatibility(String subject, String version, RegisterSchemaRequest request) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException;
}
