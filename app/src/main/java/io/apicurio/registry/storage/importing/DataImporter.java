package io.apicurio.registry.storage.importing;

import io.apicurio.registry.storage.error.ReadOnlyStorageException;
import io.apicurio.registry.storage.impexp.EntityInputStream;
import io.apicurio.registry.utils.impexp.Entity;

public interface DataImporter {

    /**
     * WARNING: Must be executed within a transaction!
     */
    void importEntity(Entity entity) throws ReadOnlyStorageException;

    /**
     * WARNING: Must be executed within a transaction!
     */
    void importData(EntityInputStream entities, Runnable postImportAction) throws ReadOnlyStorageException;
}
