package io.apicurio.registry.operator;

import io.apicurio.registry.operator.api.v3.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.context.GlobalContext;
import io.apicurio.registry.operator.resource.ResourceKey;
import io.apicurio.registry.operator.resource.app.AppDeploymentResource;
import io.apicurio.registry.operator.resource.app.AppIngressActivationCondition;
import io.apicurio.registry.operator.resource.app.AppIngressResource;
import io.apicurio.registry.operator.resource.app.AppServiceResource;
import io.apicurio.registry.operator.resource.postgresql.PostgresqlDeploymentResource;
import io.apicurio.registry.operator.resource.postgresql.PostgresqlServiceResource;
import io.apicurio.registry.operator.resource.ui.UIDeploymentResource;
import io.apicurio.registry.operator.resource.ui.UIIngressResource;
import io.apicurio.registry.operator.resource.ui.UIServiceResource;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import jakarta.inject.Inject;

import static io.apicurio.registry.operator.resource.ResourceKey.*;

// spotless:off
@ControllerConfiguration(
        dependents = {
                @Dependent(
                        type = PostgresqlDeploymentResource.class,
                        name = ResourceKey.POSTGRESQL_DEPLOYMENT_ID
                ),
                @Dependent(
                        type = PostgresqlServiceResource.class,
                        name = ResourceKey.POSTGRESQL_SERVICE_ID,
                        dependsOn = {ResourceKey.POSTGRESQL_DEPLOYMENT_ID}
                ),
                @Dependent(
                        type = AppDeploymentResource.class,
                        name = ResourceKey.APP_DEPLOYMENT_ID,
                        dependsOn = {ResourceKey.POSTGRESQL_SERVICE_ID}
                ),
                @Dependent(
                        type = AppServiceResource.class,
                        name = ResourceKey.APP_SERVICE_ID,
                        dependsOn = {ResourceKey.APP_DEPLOYMENT_ID}
                ),
                @Dependent(
                        type = AppIngressResource.class,
                        name = ResourceKey.APP_INGRESS_ID,
                        dependsOn = {ResourceKey.APP_SERVICE_ID},
                        activationCondition = AppIngressActivationCondition.class
                ),
                @Dependent(
                        type = UIDeploymentResource.class,
                        name = ResourceKey.UI_DEPLOYMENT_ID,
                        dependsOn = {ResourceKey.APP_DEPLOYMENT_ID}
                ),
                @Dependent(
                        type = UIServiceResource.class,
                        name = ResourceKey.UI_SERVICE_ID,
                        dependsOn = {ResourceKey.UI_DEPLOYMENT_ID}
                ),
                @Dependent(
                        type = UIIngressResource.class,
                        name = UI_INGRESS_ID,
                        dependsOn = {UI_SERVICE_ID}
                )
        }
)
// spotless:on
public class ApicurioRegistryOperatorReconciler
        implements Reconciler<ApicurioRegistry3>, Cleaner<ApicurioRegistry3> {

    @Inject
    GlobalContext globalContext;

    public UpdateControl<ApicurioRegistry3> reconcile(ApicurioRegistry3 primary,
            Context<ApicurioRegistry3> context) {

        return globalContext.reconcileReturn(REGISTRY_KEY, primary, context, (crContext, p) -> {
            UpdateControl<ApicurioRegistry3> uc;
            if (crContext.isUpdatePrimary()) {
                // This should only happen rarely:
                uc = UpdateControl.updateResourceAndPatchStatus(p);
            } else if (crContext.isUpdateStatus()) {
                uc = UpdateControl.patchStatus(p);
            } else {
                uc = UpdateControl.noUpdate();
            }
            if (crContext.getReschedule() != null) {
                uc.rescheduleAfter(crContext.getReschedule());
            }
            return uc;
        });
    }

    @Override
    public DeleteControl cleanup(ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {
        globalContext.cleanup(primary);
        return DeleteControl.defaultDelete();
    }
}
