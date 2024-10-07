package io.apicurio.registry.operator.resource.app;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.context.GlobalContext;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.apicurio.registry.operator.resource.LabelDiscriminators.*;
import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_APP;
import static io.apicurio.registry.operator.resource.ResourceKey.APP_DEPLOYMENT_KEY;

// spotless:off
@KubernetesDependent(
        labelSelector = "app.kubernetes.io/name=apicurio-registry,app.kubernetes.io/component=" + COMPONENT_APP,
        resourceDiscriminator = AppDeploymentDiscriminator.class
)
// spotless:on
public class AppDeploymentResource extends CRUDKubernetesDependentResource<Deployment, ApicurioRegistry3> {

    private static final Logger log = LoggerFactory.getLogger(AppDeploymentResource.class);

    public AppDeploymentResource() {
        super(Deployment.class);
    }

    @Override
    protected Deployment desired(ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {
        return GlobalContext.INSTANCE.reconcileReturn(APP_DEPLOYMENT_KEY, primary, context);
    }
}
