package io.apicurio.registry.systemtest.operator.types;

import io.apicurio.registry.systemtest.framework.Constants;
import io.apicurio.registry.systemtest.framework.LoggerUtils;
import org.slf4j.Logger;

public abstract class Operator {
    protected static final Logger operatorLogger = LoggerUtils.getLogger();

    protected String path;

    public Operator(String path) {
        this.path = path;
    }
}
