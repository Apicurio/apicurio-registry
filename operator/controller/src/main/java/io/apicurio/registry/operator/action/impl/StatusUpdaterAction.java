package io.apicurio.registry.operator.action.impl;

import io.apicurio.registry.operator.StatusUpdater;
import io.apicurio.registry.operator.action.AbstractBasicAction;
import io.apicurio.registry.operator.context.CRContext;
import io.apicurio.registry.operator.state.NoState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.apicurio.registry.operator.resource.ResourceKey.APP_DEPLOYMENT_KEY;

public class StatusUpdaterAction extends AbstractBasicAction {

    private static final Logger log = LoggerFactory.getLogger(StatusUpdaterAction.class);

    @Override
    public void run(NoState state, CRContext crContext) {
        crContext.withExistingResource(APP_DEPLOYMENT_KEY, d -> {
            crContext.withDesiredStatus(s -> {
                var statusUpdater = new StatusUpdater(crContext.getPrimary());
                log.info("Updating Apicurio Registry status:");
                statusUpdater.next(s, d);
            });
        });
    }
}
