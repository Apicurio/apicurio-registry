package io.apicurio.registry.operator.resource.postgresql;

import io.apicurio.registry.operator.api.v3.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.context.GlobalContext;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import jakarta.inject.Inject;

import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_POSTGRESQL;
import static io.apicurio.registry.operator.resource.ResourceKey.POSTGRESQL_DEPLOYMENT_KEY;
import static io.apicurio.registry.operator.utils.FunctionalUtils.returnSecondArg;

@KubernetesDependent(labelSelector = "app.kubernetes.io/name=apicurio-registry,app.kubernetes.io/component="
        + COMPONENT_POSTGRESQL, resourceDiscriminator = PostgresqlDeploymentDiscriminator.class)
public class PostgresqlDeploymentResource
        extends CRUDKubernetesDependentResource<Deployment, ApicurioRegistry3> {

    @Inject
    GlobalContext globalContext;

    public PostgresqlDeploymentResource() {
        super(Deployment.class);
    }

    @Override
    protected Deployment desired(ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {
        return globalContext.reconcileReturn(POSTGRESQL_DEPLOYMENT_KEY, primary, context, returnSecondArg());
    }
}
