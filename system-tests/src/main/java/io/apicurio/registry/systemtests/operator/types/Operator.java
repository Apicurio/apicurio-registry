package io.apicurio.registry.systemtests.operator.types;

public abstract class Operator {
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
