package io.apicurio.registry.operator.action;

import io.apicurio.registry.operator.context.CRContext;
import io.apicurio.registry.operator.resource.ResourceKey;
import io.apicurio.registry.operator.state.State;

import java.util.List;

import static io.apicurio.registry.operator.action.ActionOrder.ORDERING_DEFAULT;
import static io.apicurio.registry.operator.resource.ResourceKey.REGISTRY_KEY;

public abstract class AbstractAction<STATE extends State> implements Action<STATE> {

    @Override
    public List<ResourceKey<?>> supports() {
        return List.of(REGISTRY_KEY);
    }

    @Override
    public ActionOrder ordering() {
        return ORDERING_DEFAULT;
    }

    @Override
    public STATE initialize(CRContext crContext) {
        return null;
    }

    @Override
    public boolean shouldRun(STATE state, CRContext crContext) {
        return true;
    }
}
