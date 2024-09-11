package io.apicurio.registry.operator;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.resource.ResourceKey;
import io.apicurio.registry.operator.resource.app.AppDeploymentDiscriminator;
import io.apicurio.registry.operator.resource.app.AppDeploymentResource;
import io.apicurio.registry.operator.resource.ui.UIDeploymentResource;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// spotless:off
@ControllerConfiguration(
        dependents = {
                @Dependent(
                        type = AppDeploymentResource.class,
                        name = ResourceKey.APP_DEPLOYMENT_ID
                ),
                @Dependent(
                        type = UIDeploymentResource.class,
                        name = ResourceKey.UI_DEPLOYMENT_ID,
                        dependsOn = {ResourceKey.APP_DEPLOYMENT_ID}
                )
        }
)
// spotless:on
public class ApicurioRegistry3Reconciler implements Reconciler<ApicurioRegistry3>,
        ErrorStatusHandler<ApicurioRegistry3>, Cleaner<ApicurioRegistry3> {

    private static final Logger log = LoggerFactory.getLogger(ApicurioRegistry3Reconciler.class);

    public UpdateControl<ApicurioRegistry3> reconcile(ApicurioRegistry3 primary,
            Context<ApicurioRegistry3> context) {

        log.info("Reconciling Apicurio Registry: {}", primary);
        var statusUpdater = new StatusUpdater(primary);

        return context.getSecondaryResource(Deployment.class, AppDeploymentDiscriminator.INSTANCE)
                .map(deployment -> {
                    log.info("Updating Apicurio Registry status:");
                    primary.setStatus(statusUpdater.next(deployment));
                    return UpdateControl.patchStatus(primary);
                }).orElseGet(UpdateControl::noUpdate);
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
