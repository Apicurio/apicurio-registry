package io.apicurio.registry.operator.status;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3Status;
import io.apicurio.registry.operator.api.v1.status.ConditionStatus;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static io.apicurio.registry.operator.api.v1.status.ConditionConstants.TYPE_READY;

/**
 * Entry point for reporting events that affect operator status.
 * To support multiple CR instances, you first have to get (and later dispose of) the current instance using the static method.
 * Then, based on the event, get the relevant condition manager.
 */
public class StatusManager {

    private static final Logger log = LoggerFactory.getLogger(StatusManager.class);

    private static final Map<ResourceID, StatusManager> instances = new ConcurrentHashMap<>();

    public static StatusManager get(ApicurioRegistry3 primary) {
        // We're assuming no concurrent reconciliations per primary resource instance.
        return instances.computeIfAbsent(ResourceID.fromResource(primary), ignored -> new StatusManager());
    }

    public static void clean(ApicurioRegistry3 primary) {
        instances.remove(ResourceID.fromResource(primary));
    }

    private final List<AbstractConditionManager> conditionManagers;

    private StatusManager() {
        conditionManagers = List.of(
                new OperatorErrorConditionManager(),
                new ValidationErrorConditionManager(),
                new ReadyConditionManager()
        );
    }

    @SuppressWarnings("unchecked")
    public <C extends AbstractConditionManager> C getConditionManager(Class<C> klass) {
        return conditionManagers.stream()
                .filter(klass::isInstance)
                .map(cm -> (C) cm)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("ConditionManager of type " + klass.getCanonicalName() + " not found."));
    }

    public ApicurioRegistry3 applyStatus(ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {

        var status = new ApicurioRegistry3Status();

        for (AbstractConditionManager conditionManager : conditionManagers) {
            conditionManager.updateCondition(primary, context);
            if (conditionManager.show()) {
                var condition = conditionManager.getCondition();
                if (!TYPE_READY.equals(condition.getType()) && ConditionStatus.TRUE.equals(condition.getStatus())) {
                    log.warn("Setting condition: {}", condition);
                }
                status.getConditions().add(condition);
            }
        }

        status.setObservedGeneration(primary.getMetadata().getGeneration());

        primary.setStatus(status);
        return primary;
    }
}
