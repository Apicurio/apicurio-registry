package io.apicurio.registry.storage.impexp;

import io.apicurio.registry.utils.impexp.Entity;

import java.io.Closeable;
import java.io.IOException;



public interface EntityInputStream extends Closeable {

    /**
     * Get the next import entity from the stream of entities being imported.
     */
    Entity nextEntity() throws IOException;
}
