package io.apicurio.registry.systemtest.operator.types;

import io.apicurio.registry.systemtest.framework.LoggerUtils;
import org.slf4j.Logger;

public abstract class Operator {
    protected static final Logger LOGGER = LoggerUtils.getLogger();
    private boolean namespaceCreated = false;
    /* Contains path to bundle operator file or OLM operator catalog source image. */
    private String source;

    public Operator(String source) {
        this.source = source;
    }

    public void setNamespaceCreated(boolean created) {
        namespaceCreated = created;
    }

    public boolean getNamespaceCreated() {
        return namespaceCreated;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
