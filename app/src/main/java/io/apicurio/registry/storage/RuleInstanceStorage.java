package io.apicurio.registry.storage;

import io.apicurio.registry.storage.model.RuleInstance;
import io.apicurio.registry.storage.model.RuleType;

import java.util.Set;

public interface RuleInstanceStorage extends KeyValueStorage<RuleType, RuleInstance> {

    Set<RuleInstance> getAll();
}
