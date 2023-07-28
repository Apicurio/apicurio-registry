package io.apicurio.registry.systemtests.operator.types;

import io.apicurio.registry.systemtests.framework.Environment;
import io.apicurio.registry.systemtests.framework.OperatorUtils;
import io.apicurio.registry.systemtests.platform.Kubernetes;
import io.apicurio.registry.systemtests.time.TimeoutBudget;
import io.fabric8.openshift.api.model.operatorhub.v1.OperatorGroup;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.Subscription;

import java.time.Duration;

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

    public boolean waitSubscriptionCurrentCSV(String catalog, TimeoutBudget timeout) {
        String channel = OperatorUtils.getDefaultChannel(catalog, Environment.REGISTRY_PACKAGE);
        String expectedCSV = OperatorUtils.getCurrentCSV(catalog, Environment.REGISTRY_PACKAGE, channel);
        String subscriptionNamespace = subscription.getMetadata().getNamespace();
        String subscriptionName = subscription.getMetadata().getName();
        Subscription operatorSubscription = Kubernetes.getSubscription(subscriptionNamespace, subscriptionName);

        while (!timeout.timeoutExpired()) {
            if (operatorSubscription.getStatus().getCurrentCSV().equals(expectedCSV)) {
                return true;
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();

                return false;
            }

            operatorSubscription = Kubernetes.getSubscription(subscriptionNamespace, subscriptionName);
        }

        return operatorSubscription.getStatus().getCurrentCSV().equals(expectedCSV);
    }

    public boolean waitSubscriptionCurrentCSV(String catalog) {
        return waitSubscriptionCurrentCSV(catalog, TimeoutBudget.ofDuration(Duration.ofMinutes(3)));
    }

    public boolean waitClusterServiceVersionReady(TimeoutBudget timeout) {
        while (!timeout.timeoutExpired()) {
            if (Kubernetes.isClusterServiceVersionReady(getNamespace(), getClusterServiceVersion())) {
                return true;
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();

                return false;
            }
        }

        return Kubernetes.isClusterServiceVersionReady(getNamespace(), getClusterServiceVersion());
    }

    public boolean waitClusterServiceVersionReady() {
        return waitClusterServiceVersionReady(TimeoutBudget.ofDuration(Duration.ofMinutes(3)));
    }
}
