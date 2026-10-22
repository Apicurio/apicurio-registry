package io.apicurio.common.apps.config;

import java.util.List;

/**
 * Contains a list of experimental configuration properties discovered at build time via
 * {@code @Info(experimental=true)} annotations on {@code @ConfigProperty} fields.
 */
public class ExperimentalConfigPropertyList {

    private List<ExperimentalConfigPropertyDef> experimentalConfigProperties;

    public ExperimentalConfigPropertyList() {
    }

    public ExperimentalConfigPropertyList(List<ExperimentalConfigPropertyDef> experimentalConfigProperties) {
        this.experimentalConfigProperties = experimentalConfigProperties;
    }

    public List<ExperimentalConfigPropertyDef> getExperimentalConfigProperties() {
        return experimentalConfigProperties;
    }

    public void setExperimentalConfigProperties(
            List<ExperimentalConfigPropertyDef> experimentalConfigProperties) {
        this.experimentalConfigProperties = experimentalConfigProperties;
    }
}
