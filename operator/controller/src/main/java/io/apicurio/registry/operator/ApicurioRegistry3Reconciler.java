package io.apicurio.registry.operator;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.resource.ActivationConditions;
import io.apicurio.registry.operator.resource.app.AppDeploymentResource;
import io.apicurio.registry.operator.resource.app.AppIngressResource;
import io.apicurio.registry.operator.resource.app.AppNetworkPolicyResource;
import io.apicurio.registry.operator.resource.app.AppPodDisruptionBudgetResource;
import io.apicurio.registry.operator.resource.app.AppServiceResource;
import io.apicurio.registry.operator.resource.ui.UIDeploymentResource;
import io.apicurio.registry.operator.resource.ui.UIIngressResource;
import io.apicurio.registry.operator.resource.ui.UINetworkPolicyResource;
import io.apicurio.registry.operator.resource.ui.UIPodDisruptionBudgetResource;
import io.apicurio.registry.operator.resource.ui.UIServiceResource;
import io.apicurio.registry.operator.status.OperatorErrorConditionManager;
import io.apicurio.registry.operator.status.StatusManager;
import io.apicurio.registry.operator.updater.IngressCRUpdater;
import io.apicurio.registry.operator.updater.KafkaSqlCRUpdater;
import io.apicurio.registry.operator.updater.SqlCRUpdater;
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
import static io.apicurio.registry.operator.resource.ActivationConditions.AppNetworkPolicyActivationCondition;
import static io.apicurio.registry.operator.resource.ActivationConditions.AppPodDisruptionBudgetActivationCondition;
import static io.apicurio.registry.operator.resource.ActivationConditions.UIIngressActivationCondition;
import static io.apicurio.registry.operator.resource.ActivationConditions.UINetworkPolicyActivationCondition;
import static io.apicurio.registry.operator.resource.ActivationConditions.UIPodDisruptionBudgetActivationCondition;
import static io.apicurio.registry.operator.resource.ResourceKey.APP_DEPLOYMENT_ID;
import static io.apicurio.registry.operator.resource.ResourceKey.APP_INGRESS_ID;
import static io.apicurio.registry.operator.resource.ResourceKey.APP_NETWORK_POLICY_ID;
import static io.apicurio.registry.operator.resource.ResourceKey.APP_POD_DISRUPTION_BUDGET_ID;
import static io.apicurio.registry.operator.resource.ResourceKey.APP_SERVICE_ID;
import static io.apicurio.registry.operator.resource.ResourceKey.UI_DEPLOYMENT_ID;
import static io.apicurio.registry.operator.resource.ResourceKey.UI_INGRESS_ID;
import static io.apicurio.registry.operator.resource.ResourceKey.UI_NETWORK_POLICY_ID;
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
                @Dependent(
                        type = AppNetworkPolicyResource.class,
                        name = APP_NETWORK_POLICY_ID,
                        dependsOn = {APP_DEPLOYMENT_ID},
                        activationCondition = AppNetworkPolicyActivationCondition.class
                ),
                // ===== Registry UI
                @Dependent(
                        type = UIDeploymentResource.class,
                        name = UI_DEPLOYMENT_ID,
                        activationCondition = ActivationConditions.UIDeploymentActivationCondition.class
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
                @Dependent(
                        type = UINetworkPolicyResource.class,
                        name = UI_NETWORK_POLICY_ID,
                        dependsOn = {UI_DEPLOYMENT_ID},
                        activationCondition = UINetworkPolicyActivationCondition.class
                )
        }
)
// TODO: When renaming, do not forget to update application.properties (until we have a test for this).
public class ApicurioRegistry3Reconciler implements Reconciler<ApicurioRegistry3>,
        ErrorStatusHandler<ApicurioRegistry3>, Cleaner<ApicurioRegistry3> {

    private static final Logger log = LoggerFactory.getLogger(ApicurioRegistry3Reconciler.class);

    public UpdateControl<ApicurioRegistry3> reconcile(ApicurioRegistry3 primary,
                                                      Context<ApicurioRegistry3> context) {

        log.trace("Reconciling Apicurio Registry: {}", primary);

        // Some of the fields in the CR have been deprecated and another fields should be used instead.
        // Operator will attempt to update the CR to use the newer fields if possible.
        // This has to be done first, so subsequent functionality can deal with new fields only.
        var update = IngressCRUpdater.update(primary);
        update = SqlCRUpdater.update(primary, context) || update;
        update = KafkaSqlCRUpdater.update(primary) || update;
        if (update) {
            return UpdateControl.updateResource(primary);
        }

        return UpdateControl.patchStatus(StatusManager.get(primary).applyStatus(primary, context));
    }

    @Override
    public ErrorStatusUpdateControl<ApicurioRegistry3> updateErrorStatus(ApicurioRegistry3 primary,
                                                                         Context<ApicurioRegistry3> context, Exception ex) {
        StatusManager.get(primary).getConditionManager(OperatorErrorConditionManager.class).recordException(ex);
        return ErrorStatusUpdateControl.updateStatus(StatusManager.get(primary).applyStatus(primary, context));
    }

    @Override
    public DeleteControl cleanup(ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {
        StatusManager.clean(primary);
        return DeleteControl.defaultDelete();
    }
}
