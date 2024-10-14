package io.apicurio.registry.operator;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.resource.ActivationConditions.UIIngressActivationCondition;
import io.apicurio.registry.operator.resource.app.AppDeploymentResource;
import io.apicurio.registry.operator.resource.app.AppIngressResource;
import io.apicurio.registry.operator.resource.app.AppServiceResource;
import io.apicurio.registry.operator.resource.ui.UIDeploymentResource;
import io.apicurio.registry.operator.resource.ui.UIIngressResource;
import io.apicurio.registry.operator.resource.ui.UIServiceResource;
import io.apicurio.registry.operator.utils.ResourceUtils;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.apicurio.registry.operator.resource.ActivationConditions.AppIngressActivationCondition;
import static io.apicurio.registry.operator.resource.ResourceKey.*;
import static io.apicurio.registry.operator.utils.Cell.cell;

// spotless:off
@ControllerConfiguration(
        dependents = {
                // App
                @Dependent(
                        type = AppDeploymentResource.class,
                        name = APP_DEPLOYMENT_ID
                ),
                @Dependent(
                        type = AppServiceResource.class,
                        name = APP_SERVICE_ID,
                        dependsOn = {APP_DEPLOYMENT_ID}
                ),
                @Dependent(
                        type = AppIngressResource.class,
                        name = APP_INGRESS_ID,
                        dependsOn = {APP_SERVICE_ID},
                        activationCondition = AppIngressActivationCondition.class
                ),
                // UI
                @Dependent(
                        type = UIDeploymentResource.class,
                        name = UI_DEPLOYMENT_ID
                ),
                @Dependent(
                        type = UIServiceResource.class,
                        name = UI_SERVICE_ID,
                        dependsOn = {UI_DEPLOYMENT_ID}
                ),
                @Dependent(
                        type = UIIngressResource.class,
                        name = UI_INGRESS_ID,
                        dependsOn = {UI_SERVICE_ID},
                        activationCondition = UIIngressActivationCondition.class
                )
        }
)
// spotless:on
public class ApicurioRegistry3Reconciler implements Reconciler<ApicurioRegistry3>,
        ErrorStatusHandler<ApicurioRegistry3>, Cleaner<ApicurioRegistry3> {

    private static final Logger log = LoggerFactory.getLogger(ApicurioRegistry3Reconciler.class);

    public UpdateControl<ApicurioRegistry3> reconcile(ApicurioRegistry3 primary,
            Context<ApicurioRegistry3> context) {
        try (var ru = new ResourceUtils<>(primary, context, REGISTRY_KEY)) {

            var statusUpdated = cell(false);

            ru.withExistingResource(APP_DEPLOYMENT_KEY, d -> {
                ru.withDesiredResource(p -> {

                    var statusUpdater = new StatusUpdater(p);
                    log.info("Updating Apicurio Registry status:");
                    p.setStatus(statusUpdater.next(d));
                    statusUpdated.setValue(true);
                });
            });

            if (statusUpdated.getValue()) {
                return UpdateControl.patchStatus(ru.returnDesiredResource());
            } else {
                return UpdateControl.noUpdate();
            }
        }
    }

    @Override
    public ErrorStatusUpdateControl<ApicurioRegistry3> updateErrorStatus(ApicurioRegistry3 apicurioRegistry,
            Context<ApicurioRegistry3> context, Exception ex) {
        log.error("Status error", ex);
        var statusUpdater = new StatusUpdater(apicurioRegistry);
        apicurioRegistry.setStatus(statusUpdater.errorStatus(ex));
        return ErrorStatusUpdateControl.updateStatus(apicurioRegistry);
    }

    @Override
    public DeleteControl cleanup(ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {
        return DeleteControl.defaultDelete();
    }
}
