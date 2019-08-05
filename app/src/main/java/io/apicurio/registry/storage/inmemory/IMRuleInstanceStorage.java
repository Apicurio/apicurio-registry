package io.apicurio.registry.storage.inmemory;

import io.apicurio.registry.storage.RuleInstanceStorage;
import io.apicurio.registry.storage.model.RuleInstance;
import io.apicurio.registry.storage.model.RuleType;

import javax.enterprise.context.ApplicationScoped;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
@InMemory
public class IMRuleInstanceStorage implements RuleInstanceStorage {

    private final Map<RuleType, RuleInstance> storage = new ConcurrentHashMap<>();

    @Override
    public Map<RuleType, RuleInstance> getAll() {
        return new HashMap<>(storage);
    }

    @Override
    public void put(RuleType key, RuleInstance value) {
        storage.put(key, value);
    }

    @Override
    public void delete(RuleType key) {
        storage.remove(key);
    }

    @Override
    public RuleInstance get(RuleType key) {
        return storage.get(key);
    }
}
