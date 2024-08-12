package io.apicurio.registry.operator.action.impl.ui;

import io.apicurio.registry.operator.action.AbstractBasicAction;
import io.apicurio.registry.operator.action.ActionOrder;
import io.apicurio.registry.operator.context.CRContext;
import io.apicurio.registry.operator.resource.ResourceKey;
import io.apicurio.registry.operator.state.NoState;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

import static io.apicurio.registry.operator.action.ActionOrder.ORDERING_FIRST;
import static io.apicurio.registry.operator.resource.ResourceFactory.UI_CONTAINER_NAME;
import static io.apicurio.registry.operator.resource.ResourceKey.UI_DEPLOYMENT_KEY;
import static io.apicurio.registry.operator.utils.PodTemplateSpecUtils.process;

@ApplicationScoped
public class UIPodTemplateSpecAction extends AbstractBasicAction {

    @Override
    public List<ResourceKey<?>> supports() {
        return List.of(UI_DEPLOYMENT_KEY);
    }

    @Override
    public ActionOrder ordering() {
        return ORDERING_FIRST;
    }

    @Override
    public boolean shouldRun(NoState state, CRContext crContext) {
        return crContext.getPrimary().getSpec().getUi() != null
                && crContext.getPrimary().getSpec().getUi().getPodTemplate() != null;
    }

    @Override
    public void run(NoState state, CRContext crContext) {
        crContext.withDesiredResource(UI_DEPLOYMENT_KEY, d -> {
            var base = d.getSpec().getTemplate();
            var spec = crContext.getPrimary().getSpec().getUi().getPodTemplate().edit().build();
            process(spec, base, UI_CONTAINER_NAME);
            d.getSpec().setTemplate(spec);
        });
    }
}
