package io.apicurio.registry.operator;

import io.apicur.registry.v1.ApicurioRegistry;
import io.apicur.registry.v1.ApicurioRegistryStatus;
import io.apicur.registry.v1.ApicurioRegistryStatusBuilder;
import io.apicur.registry.v1.apicurioregistrystatus.Conditions;
import io.apicur.registry.v1.apicurioregistrystatus.ConditionsBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import java.util.Arrays;
import java.util.Date;
import java.util.stream.Collectors;

public class StatusUpdater {

  public static final String ERROR_TYPE = "ERROR";

  private ApicurioRegistry registry;

  public StatusUpdater(ApicurioRegistry registry) {
    this.registry = registry;
  }

  public ApicurioRegistryStatus errorStatus(Exception e) {
    var lastTransitionTime = new Date().toString();
    if (registry != null
        && registry.getStatus() != null
        && registry.getStatus().getConditions().size() > 0
        &&
        // TODO: better `lastTransitionTime` handling
        registry.getStatus().getConditions().get(0).getLastTransitionTime() != null) {
      lastTransitionTime = registry.getStatus().getConditions().get(0).getLastTransitionTime();
    }

    var generation = registry.getMetadata() == null ? null : registry.getMetadata().getGeneration();
    var errorCondition =
        new ConditionsBuilder()
            .withStatus(Conditions.Status.TRUE)
            .withType(ERROR_TYPE)
            .withObservedGeneration(generation)
            .withLastTransitionTime("2017-07-21T17:32:28Z") // TODO: fixme
            // https://github.com/fabric8io/kubernetes-client/pull/5279
            .withMessage(
                Arrays.stream(e.getStackTrace())
                    .map(st -> st.toString())
                    .collect(Collectors.joining("\n")))
            .withReason("reasons")
            .build();

    return new ApicurioRegistryStatusBuilder().withConditions(errorCondition).build();
  }

  public ApicurioRegistryStatus next(Deployment deployment) {
    var lastTransitionTime = new Date().toString();
    if (registry != null
        && registry.getStatus() != null
        && registry.getStatus().getConditions().size() > 0
        &&
        // TODO: should we sort the conditions before taking the first?
        registry.getStatus().getConditions().get(0).getLastTransitionTime() != null) {
      lastTransitionTime = registry.getStatus().getConditions().get(0).getLastTransitionTime();
    }

    var generation = registry.getMetadata() == null ? null : registry.getMetadata().getGeneration();
    var nextCondition =
        new ConditionsBuilder()
            .withStatus(Conditions.Status.TRUE)
            .withType(ERROR_TYPE)
            .withObservedGeneration(generation)
            .withLastTransitionTime("2017-07-21T17:32:28Z") // TODO: fixme
            // https://github.com/fabric8io/kubernetes-client/pull/5279
            .withMessage("TODO")
            .withReason("reasons")
            .build();

    return new ApicurioRegistryStatusBuilder().withConditions(nextCondition).build();
  }
}
