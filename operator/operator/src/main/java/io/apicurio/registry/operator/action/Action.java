package io.apicurio.registry.operator.action;

import io.apicurio.registry.operator.context.CRContext;
import io.apicurio.registry.operator.resource.ResourceKey;
import io.apicurio.registry.operator.state.State;

import java.util.List;

public interface Action<STATE extends State> {

    List<ResourceKey<?>> supports();

    ActionOrder ordering();

    Class<STATE> getStateClass();

    STATE initialize(CRContext crContext);

    boolean shouldRun(STATE state, CRContext crContext);

    void run(STATE state, CRContext crContext);
}
