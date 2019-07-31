package io.apicurio.registry.storage.inmemory;

import io.apicurio.registry.storage.MetaData;
import io.apicurio.registry.storage.model.MetaValue;

import javax.enterprise.context.ApplicationScoped;
import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
@InMemory
public class IMMetadataStorage extends AbstractIMKeyValueStorage<String, MetaValue>
        implements MetaData {


    @Override
    public Map<String, MetaValue> getAll() {
        return new HashMap<>(storage);
    }
}
