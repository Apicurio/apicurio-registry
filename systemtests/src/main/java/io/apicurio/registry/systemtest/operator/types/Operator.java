package io.apicurio.registry.systemtest.operator.types;

import io.apicurio.registry.systemtest.framework.LoggerUtils;
import org.slf4j.Logger;

public abstract class Operator {
    protected static final Logger LOGGER = LoggerUtils.getLogger();
    /* Contains path to bundle operator file or OLM operator catalog source image. */
    private String source;
    private String clusterServiceVersion;

    public Operator(String source) {
        this.source = source;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getClusterServiceVersion() {
        return clusterServiceVersion;
    }

    public void setClusterServiceVersion(String clusterServiceVersion) {
        this.clusterServiceVersion = clusterServiceVersion;
    }
}
