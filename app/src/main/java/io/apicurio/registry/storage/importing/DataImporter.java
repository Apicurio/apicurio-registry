package io.apicurio.registry.storage.importing;

import io.apicurio.registry.utils.impexp.Entity;
import io.apicurio.registry.utils.impexp.EntityInputStream;

public interface DataImporter {

    /**
     * WARNING: Must be executed within a transaction!
     */
    void importEntity(Entity entity);

    /**
     * WARNING: Must be executed within a transaction!
     */
    void importData(EntityInputStream entities, Runnable postImportAction);
}
