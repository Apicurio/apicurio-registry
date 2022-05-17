package io.apicurio.registry.systemtest.operator.types;

import io.apicurio.registry.systemtest.framework.LoggerUtils;
import org.slf4j.Logger;

public abstract class Operator {
    protected static final Logger LOGGER = LoggerUtils.getLogger();
    /* Contains path to bundle operator file or OLM operator catalog source image. */
    private String source;
    private String namespace;

    public Operator(String source) {
        this.source = source;
    }

    public Operator(String source, String namespace) {
        this.source = source;
        this.namespace = namespace;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }
}
