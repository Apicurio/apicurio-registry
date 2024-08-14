package io.apicurio.registry.operator.resource.app;

import io.apicurio.registry.operator.api.v3.v1.ApicurioRegistry3;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;

public class AppIngressActivationCondition implements Condition<Ingress, ApicurioRegistry3> {

    @Override
    public boolean isMet(DependentResource<Ingress, ApicurioRegistry3> resource, ApicurioRegistry3 primary,
            Context<ApicurioRegistry3> context) {

        return true;
    }
}
