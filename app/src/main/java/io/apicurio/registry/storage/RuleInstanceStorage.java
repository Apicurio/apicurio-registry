package io.apicurio.registry.storage;

import io.apicurio.registry.storage.model.RuleInstance;
import io.apicurio.registry.storage.model.RuleType;

import java.util.Map;

public interface RuleInstanceStorage extends KeyValueStorage<RuleType, RuleInstance> {

    Map<RuleType, RuleInstance> getAll();
}
