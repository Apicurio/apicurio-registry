package io.apicurio.registry.utils.impexp;

import java.io.IOException;

public interface EntityInputStream {

    /**
     * Get the next import entity from the stream of entities being imported.
     */
    Entity nextEntity() throws IOException;
}
