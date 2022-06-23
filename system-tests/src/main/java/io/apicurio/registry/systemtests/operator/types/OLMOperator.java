package io.apicurio.registry.systemtests.operator.types;

import io.fabric8.openshift.api.model.operatorhub.v1.OperatorGroup;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.Subscription;

public abstract class OLMOperator extends Operator {
    private String clusterServiceVersion;
    private boolean clusterWide;
    private Subscription subscription = null;
    private OperatorGroup operatorGroup = null;

    public OLMOperator(String source, String operatorNamespace, boolean clusterWide) {
        super(source);
        setNamespace(operatorNamespace);
        setClusterWide(clusterWide);
    }

    public String getClusterServiceVersion() {
        return clusterServiceVersion;
    }

    public void setClusterServiceVersion(String clusterServiceVersion) {
        this.clusterServiceVersion = clusterServiceVersion;
    }

    public boolean getClusterWide() {
        return clusterWide;
    }

    public void setClusterWide(boolean clusterWide) {
        this.clusterWide = clusterWide;
    }

    public Subscription getSubscription() {
        return subscription;
    }

    public void setSubscription(Subscription subscription) {
        this.subscription = subscription;
    }

    public OperatorGroup getOperatorGroup() {
        return operatorGroup;
    }

    public void setOperatorGroup(OperatorGroup operatorGroup) {
        this.operatorGroup = operatorGroup;
    }
}
