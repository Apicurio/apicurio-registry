package io.apicurio.registry.systemtest.operator.types;

import io.apicurio.registry.systemtest.framework.LoggerUtils;
import org.slf4j.Logger;

public abstract class Operator {
    protected static final Logger operatorLogger = LoggerUtils.getLogger();
    protected boolean namespaceCreated = false;

    /*
        Contains path to bundle operator file or OLM operator image.
     */
    protected String source;

    public Operator(String source) {
        this.source = source;
    }

    public void setNamespaceCreated(boolean created) {
        namespaceCreated = created;
    }

    public boolean getNamespaceCreated() {
        return namespaceCreated;
    }
}
