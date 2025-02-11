package io.apicurio.registry.operator;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.resource.LabelDiscriminators.AppDeploymentDiscriminator;
import io.apicurio.registry.operator.resource.app.AppDeploymentResource;
import io.apicurio.registry.operator.resource.app.AppIngressResource;
import io.apicurio.registry.operator.resource.app.AppPodDisruptionBudgetResource;
import io.apicurio.registry.operator.resource.app.AppServiceResource;
import io.apicurio.registry.operator.resource.studioui.StudioUIDeploymentResource;
import io.apicurio.registry.operator.resource.studioui.StudioUIIngressResource;
import io.apicurio.registry.operator.resource.studioui.StudioUIPodDisruptionBudgetResource;
import io.apicurio.registry.operator.resource.studioui.StudioUIServiceResource;
import io.apicurio.registry.operator.resource.ui.UIDeploymentResource;
import io.apicurio.registry.operator.resource.ui.UIIngressResource;
import io.apicurio.registry.operator.resource.ui.UIPodDisruptionBudgetResource;
import io.apicurio.registry.operator.resource.ui.UIServiceResource;
import io.apicurio.registry.operator.updater.IngressCRUpdater;
import io.apicurio.registry.operator.updater.KafkaSqlCRUpdater;
import io.apicurio.registry.operator.updater.SqlCRUpdater;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.ErrorStatusHandler;
import io.javaoperatorsdk.operator.api.reconciler.ErrorStatusUpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.apicurio.registry.operator.resource.ActivationConditions.AppIngressActivationCondition;
import static io.apicurio.registry.operator.resource.ActivationConditions.AppPodDisruptionBudgetActivationCondition;
import static io.apicurio.registry.operator.resource.ActivationConditions.StudioUIDeploymentActivationCondition;
import static io.apicurio.registry.operator.resource.ActivationConditions.StudioUIIngressActivationCondition;
import static io.apicurio.registry.operator.resource.ActivationConditions.StudioUIPodDisruptionBudgetActivationCondition;
import static io.apicurio.registry.operator.resource.ActivationConditions.UIIngressActivationCondition;
import static io.apicurio.registry.operator.resource.ActivationConditions.UIPodDisruptionBudgetActivationCondition;
import static io.apicurio.registry.operator.resource.ResourceKey.APP_DEPLOYMENT_ID;
import static io.apicurio.registry.operator.resource.ResourceKey.APP_INGRESS_ID;
import static io.apicurio.registry.operator.resource.ResourceKey.APP_POD_DISRUPTION_BUDGET_ID;
import static io.apicurio.registry.operator.resource.ResourceKey.APP_SERVICE_ID;
import static io.apicurio.registry.operator.resource.ResourceKey.STUDIO_UI_DEPLOYMENT_ID;
import static io.apicurio.registry.operator.resource.ResourceKey.STUDIO_UI_INGRESS_ID;
import static io.apicurio.registry.operator.resource.ResourceKey.STUDIO_UI_POD_DISRUPTION_BUDGET_ID;
import static io.apicurio.registry.operator.resource.ResourceKey.STUDIO_UI_SERVICE_ID;
import static io.apicurio.registry.operator.resource.ResourceKey.UI_DEPLOYMENT_ID;
import static io.apicurio.registry.operator.resource.ResourceKey.UI_INGRESS_ID;
import static io.apicurio.registry.operator.resource.ResourceKey.UI_POD_DISRUPTION_BUDGET_ID;
import static io.apicurio.registry.operator.resource.ResourceKey.UI_SERVICE_ID;

@ControllerConfiguration(
        dependents = {
                // ===== Registry App
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
                @Dependent(
                        type = AppPodDisruptionBudgetResource.class,
                        name = APP_POD_DISRUPTION_BUDGET_ID,
                        dependsOn = {APP_DEPLOYMENT_ID},
                        activationCondition = AppPodDisruptionBudgetActivationCondition.class
                ),
                // ===== Registry UI
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
                ),
                @Dependent(
                        type = UIPodDisruptionBudgetResource.class,
                        name = UI_POD_DISRUPTION_BUDGET_ID,
                        dependsOn = {UI_DEPLOYMENT_ID},
                        activationCondition = UIPodDisruptionBudgetActivationCondition.class
                ),
                // ===== Studio UI
                @Dependent(
                        type = StudioUIDeploymentResource.class,
                        name = STUDIO_UI_DEPLOYMENT_ID,
                        activationCondition = StudioUIDeploymentActivationCondition.class
                ),
                @Dependent(
                        type = StudioUIServiceResource.class,
                        name = STUDIO_UI_SERVICE_ID,
                        dependsOn = {STUDIO_UI_DEPLOYMENT_ID}
                ),
                @Dependent(
                        type = StudioUIIngressResource.class,
                        name = STUDIO_UI_INGRESS_ID,
                        dependsOn = {STUDIO_UI_SERVICE_ID},
                        activationCondition = StudioUIIngressActivationCondition.class
                ),
                @Dependent(
                        type = StudioUIPodDisruptionBudgetResource.class,
                        name = STUDIO_UI_POD_DISRUPTION_BUDGET_ID,
                        dependsOn = {STUDIO_UI_DEPLOYMENT_ID},
                        activationCondition = StudioUIPodDisruptionBudgetActivationCondition.class
                ),
        }
)
// TODO: When renaming, do not forget to update application.properties (until we have a test for this).
public class ApicurioRegistry3Reconciler implements Reconciler<ApicurioRegistry3>,
        ErrorStatusHandler<ApicurioRegistry3>, Cleaner<ApicurioRegistry3> {

    private static final Logger log = LoggerFactory.getLogger(ApicurioRegistry3Reconciler.class);

    public UpdateControl<ApicurioRegistry3> reconcile(ApicurioRegistry3 primary,
            Context<ApicurioRegistry3> context) {

        log.info("Reconciling Apicurio Registry: {}", primary);

        // Some of the fields in the CR have been deprecated and another fields should be used instead.
        // Operator will attempt to update the CR to use the newer fields if possible.
        // This has to be done first, so subsequent functionality can deal with new fields only.
        var update = IngressCRUpdater.update(primary);
        update = SqlCRUpdater.update(primary) || update;
        update = KafkaSqlCRUpdater.update(primary) || update;
        if (update) {
            return UpdateControl.updateResource(primary);
        }

        var statusUpdater = new StatusUpdater(primary);

        return context.getSecondaryResource(Deployment.class, AppDeploymentDiscriminator.INSTANCE)
                .map(deployment -> {
                    statusUpdater.update(deployment);
                    return UpdateControl.patchStatus(primary);
                }).orElseGet(UpdateControl::noUpdate);
    }

    @Override
    public ErrorStatusUpdateControl<ApicurioRegistry3> updateErrorStatus(ApicurioRegistry3 apicurioRegistry,
            Context<ApicurioRegistry3> context, Exception ex) {
        log.error("Status error", ex);
        var statusUpdater = new StatusUpdater(apicurioRegistry);
        statusUpdater.updateWithException(ex);
        return ErrorStatusUpdateControl.updateStatus(apicurioRegistry);
    }

    @Override
    public DeleteControl cleanup(ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {
        return DeleteControl.defaultDelete();
    }
}
