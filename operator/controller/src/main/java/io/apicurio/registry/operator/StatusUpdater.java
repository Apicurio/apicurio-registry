package io.apicurio.registry.operator;

import io.apicur.registry.v1.ApicurioRegistry;
import io.apicur.registry.v1.ApicurioRegistryStatus;
import io.apicur.registry.v1.apicurioregistrystatus.Conditions;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class StatusUpdater {

  public static final String ERROR_TYPE = "ERROR";

  private ApicurioRegistry registry;

  public StatusUpdater(ApicurioRegistry registry) {
    this.registry = registry;
  }

  public ApicurioRegistryStatus errorStatus(Exception e) {
    ZonedDateTime lastTransitionTime = ZonedDateTime.now();
    if (registry != null
        && registry.getStatus() != null
        && registry.getStatus().getConditions().size() > 0
        &&
        // TODO: better `lastTransitionTime` handling
        registry.getStatus().getConditions().get(0).getLastTransitionTime() != null) {
      lastTransitionTime = registry.getStatus().getConditions().get(0).getLastTransitionTime();
    }

    var generation = registry.getMetadata() == null ? null : registry.getMetadata().getGeneration();
    var newLastTransitionTime = ZonedDateTime.now();
    var errorCondition = new Conditions();
    errorCondition.setStatus(Conditions.Status.TRUE);
    errorCondition.setType(ERROR_TYPE);
    errorCondition.setObservedGeneration(generation);
    errorCondition.setLastTransitionTime(newLastTransitionTime);
    errorCondition.setMessage(
        Arrays.stream(e.getStackTrace())
            .map(st -> st.toString())
            .collect(Collectors.joining("\n")));
    errorCondition.setReason("reasons");

    var status = new ApicurioRegistryStatus();
    status.setConditions(List.of(errorCondition));

    return status;
  }

  public ApicurioRegistryStatus next(Deployment deployment) {
    var lastTransitionTime = ZonedDateTime.now();
    if (registry != null
        && registry.getStatus() != null
        && registry.getStatus().getConditions().size() > 0
        &&
        // TODO: should we sort the conditions before taking the first?
        registry.getStatus().getConditions().get(0).getLastTransitionTime() != null) {
      lastTransitionTime = registry.getStatus().getConditions().get(0).getLastTransitionTime();
    }

    var generation = registry.getMetadata() == null ? null : registry.getMetadata().getGeneration();
    var nextCondition = new Conditions();
    nextCondition.setStatus(Conditions.Status.TRUE);
    nextCondition.setType(ERROR_TYPE);
    nextCondition.setObservedGeneration(generation);
    nextCondition.setLastTransitionTime(lastTransitionTime);
    nextCondition.setMessage("TODO");
    nextCondition.setReason("reasons");

    var status = new ApicurioRegistryStatus();
    status.setConditions(List.of(nextCondition));

    return status;
  }
}
