package io.apicurio.registry.operator.action;

import io.apicurio.registry.operator.action.impl.app.AppEnvApplyAction;
import io.apicurio.registry.operator.action.impl.app.AppPodTemplateSpecAction;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Represents an ordering in which the active actions should be executed.
 * <p>
 * This is important for example for {@link AppPodTemplateSpecAction} (has to be among the first), or
 * {@link AppEnvApplyAction} (has to be after all changes to the env. variables are done).
 */
@AllArgsConstructor
@Getter
public enum ActionOrder {

    // spotless:off
    ORDERING_FIRST(0),
    ORDERING_EARLY(1),
    ORDERING_DEFAULT(2),
    ORDERING_LATE(3),
    ORDERING_LAST(4);
    // spotless:on

    private final int value;
}
