package io.apicurio.registry.operator.action;

import io.apicurio.registry.operator.context.CRContext;
import io.apicurio.registry.operator.state.NoState;

import static io.apicurio.registry.operator.state.NoState.INSTANCE;

public abstract class AbstractBasicAction extends AbstractAction<NoState> {

    @Override
    public Class<NoState> getStateClass() {
        return NoState.class;
    }

    @Override
    public NoState initialize(CRContext crContext) {
        return INSTANCE;
    }
}
