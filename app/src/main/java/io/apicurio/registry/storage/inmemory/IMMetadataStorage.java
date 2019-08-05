package io.apicurio.registry.storage.inmemory;

import io.apicurio.registry.storage.MetaDataStorage;
import io.apicurio.registry.storage.model.MetaValue;

import javax.enterprise.context.ApplicationScoped;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
@InMemory
public class IMMetadataStorage implements MetaDataStorage {

    private final Map<String, MetaValue> storage = new ConcurrentHashMap<>();

    @Override
    public Map<String, MetaValue> getAll() {
        return new HashMap<>(storage);
    }

    @Override
    public void put(String key, MetaValue value) {
        storage.put(key, value);
    }

    @Override
    public void delete(String key) {
        storage.remove(key);
    }

    @Override
    public MetaValue get(String key) {
        return storage.get(key);
    }
}
