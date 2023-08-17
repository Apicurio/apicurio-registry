package io.apicurio.registry.operator;

import io.apicur.registry.v1.ApicurioRegistry;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.ErrorStatusHandler;
import io.javaoperatorsdk.operator.api.reconciler.ErrorStatusUpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;

@ControllerConfiguration(dependents = {@Dependent(type = ApicurioDeployment.class)})
public class DeploymentController
    implements Reconciler<ApicurioRegistry>, ErrorStatusHandler<ApicurioRegistry> {
  @Inject KubernetesClient client;

  @Override
  public UpdateControl<ApicurioRegistry> reconcile(
      ApicurioRegistry apicurioRegistry, Context<ApicurioRegistry> context) {
    Log.infof("Reconciling Apicurio Registry: {}", apicurioRegistry);
    var statusUpdater = new StatusUpdater(apicurioRegistry);

    return context
        .getSecondaryResource(Deployment.class)
        .map(
            deployment -> {
              Log.infof("Updating Apicurio Registry status:");
              apicurioRegistry.setStatus(statusUpdater.next(deployment));
              return UpdateControl.patchStatus(apicurioRegistry);
            })
        .orElseGet(UpdateControl::noUpdate);
  }

  @Override
  public ErrorStatusUpdateControl<ApicurioRegistry> updateErrorStatus(
      ApicurioRegistry apicurioRegistry, Context<ApicurioRegistry> context, Exception e) {
    var statusUpdater = new StatusUpdater(apicurioRegistry);
    apicurioRegistry.setStatus(statusUpdater.errorStatus(e));
    return ErrorStatusUpdateControl.updateStatus(apicurioRegistry);
  }
}
