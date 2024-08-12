package io.apicurio.registry.operator.resource.app;

import io.apicurio.registry.operator.api.v3.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.context.GlobalContext;
import io.fabric8.kubernetes.api.model.Service;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import jakarta.inject.Inject;

import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_APP;
import static io.apicurio.registry.operator.resource.ResourceKey.APP_SERVICE_KEY;
import static io.apicurio.registry.operator.utils.FunctionalUtils.returnSecondArg;

@KubernetesDependent(labelSelector = "app.kubernetes.io/name=apicurio-registry,app.kubernetes.io/component="
        + COMPONENT_APP, resourceDiscriminator = AppServiceDiscriminator.class)
public class AppServiceResource extends CRUDKubernetesDependentResource<Service, ApicurioRegistry3> {

    @Inject
    GlobalContext globalContext;

    public AppServiceResource() {
        super(Service.class);
    }

    @Override
    protected Service desired(ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {
        return globalContext.reconcileReturn(APP_SERVICE_KEY, primary, context, returnSecondArg());
    }
}
