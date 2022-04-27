package io.apicurio.registry.systemtest;

import io.apicurio.registry.systemtest.framework.TestNameGenerator;
import io.apicurio.registry.systemtest.framework.TestSeparator;
import io.apicurio.registry.systemtest.operator.OperatorManager;
import io.apicurio.registry.systemtest.registryinfra.ResourceManager;
import org.junit.jupiter.api.DisplayNameGeneration;

@DisplayNameGeneration(TestNameGenerator.class)
public abstract class TestBase implements TestSeparator {
    protected final ResourceManager resourceManager = ResourceManager.getInstance();
    protected final OperatorManager operatorManager = OperatorManager.getInstance();
}
