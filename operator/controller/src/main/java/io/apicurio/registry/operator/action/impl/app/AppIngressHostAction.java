package io.apicurio.registry.operator.action.impl.app;

import io.apicurio.registry.operator.action.AbstractBasicAction;
import io.apicurio.registry.operator.context.CRContext;
import io.apicurio.registry.operator.resource.ResourceKey;
import io.apicurio.registry.operator.state.NoState;

import java.util.List;

import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_APP;
import static io.apicurio.registry.operator.resource.ResourceKey.APP_INGRESS_KEY;
import static io.apicurio.registry.operator.resource.ResourceKey.APP_SERVICE_KEY;
import static io.apicurio.registry.operator.utils.IngressUtils.getHost;
import static io.apicurio.registry.operator.utils.IngressUtils.withIngressRule;

public class AppIngressHostAction extends AbstractBasicAction {

    @Override
    public List<ResourceKey<?>> supports() {
        return List.of(APP_INGRESS_KEY);
    }

    @Override
    public void run(NoState state, CRContext crContext) {

        crContext.withExistingResource(APP_SERVICE_KEY, s -> {
            crContext.withDesiredResource(APP_INGRESS_KEY, i -> {
                withIngressRule(s, i, rule -> rule.setHost(getHost(COMPONENT_APP, crContext.getPrimary())));
            });
        });
    }
}
