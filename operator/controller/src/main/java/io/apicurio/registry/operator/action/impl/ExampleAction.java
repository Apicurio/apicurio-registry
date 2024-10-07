package io.apicurio.registry.operator.action.impl;

import io.apicurio.registry.operator.action.AbstractAction;
import io.apicurio.registry.operator.context.CRContext;
import io.apicurio.registry.operator.resource.ResourceKey;
import io.apicurio.registry.operator.state.impl.ExampleState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static io.apicurio.registry.operator.resource.ResourceKey.*;

public class ExampleAction extends AbstractAction<ExampleState> {

    private static final Logger log = LoggerFactory.getLogger(ExampleAction.class);

    @Override
    public List<ResourceKey<?>> supports() {
        return List.of(
                // spotless:off
                REGISTRY_KEY,
                APP_DEPLOYMENT_KEY,
                APP_SERVICE_KEY,
                APP_INGRESS_KEY,
                UI_DEPLOYMENT_KEY,
                UI_SERVICE_KEY,
                UI_INGRESS_KEY
                // spotless:on
        );
    }

    @Override
    public Class<ExampleState> getStateClass() {
        return ExampleState.class;
    }

    @Override
    public ExampleState initialize(CRContext crContext) {
        return new ExampleState();
    }

    @Override
    public void run(ExampleState state, CRContext crContext) {
        log.info("Reconciliation count for {} is {}", crContext.getDesiredKey(),
                state.next(crContext.getDesiredKey()));
    }
}
