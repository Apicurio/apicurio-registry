package io.apicurio.registry.utils.impexp;

import lombok.AllArgsConstructor;

import java.io.IOException;

@AllArgsConstructor
public class EntityInputStreamImpl implements EntityInputStream {

    private EntityReader reader;

    @Override
    public Entity nextEntity() throws IOException {
        return reader.readNextEntity();
    }
}
