package io.apicurio.registry.operator.action.impl.ui;

import io.apicurio.registry.operator.action.AbstractBasicAction;
import io.apicurio.registry.operator.context.CRContext;
import io.apicurio.registry.operator.resource.ResourceKey;
import io.apicurio.registry.operator.state.NoState;

import java.util.List;

import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_UI;
import static io.apicurio.registry.operator.resource.ResourceKey.UI_INGRESS_KEY;
import static io.apicurio.registry.operator.resource.ResourceKey.UI_SERVICE_KEY;
import static io.apicurio.registry.operator.utils.IngressUtils.getHost;
import static io.apicurio.registry.operator.utils.IngressUtils.withIngressRule;

public class UIIngressHostAction extends AbstractBasicAction {

    @Override
    public List<ResourceKey<?>> supports() {
        return List.of(UI_INGRESS_KEY);
    }

    @Override
    public void run(NoState state, CRContext crContext) {

        crContext.withExistingResource(UI_SERVICE_KEY, s -> {
            crContext.withDesiredResource(UI_INGRESS_KEY, i -> {
                withIngressRule(s, i, rule -> rule.setHost(getHost(COMPONENT_UI, crContext.getPrimary())));
            });
        });
    }
}
