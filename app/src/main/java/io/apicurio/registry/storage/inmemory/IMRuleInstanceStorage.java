package io.apicurio.registry.storage.inmemory;

import io.apicurio.registry.storage.RuleInstanceStorage;
import io.apicurio.registry.storage.model.RuleInstance;
import io.apicurio.registry.storage.model.RuleType;

import javax.enterprise.context.ApplicationScoped;
import java.util.HashSet;
import java.util.Set;

@ApplicationScoped
@InMemory
public class IMRuleInstanceStorage extends AbstractIMKeyValueStorage<RuleType, RuleInstance>
        implements RuleInstanceStorage {


    @Override
    public Set<RuleInstance> getAll() {
        return new HashSet<>(storage.values());
    }
}
