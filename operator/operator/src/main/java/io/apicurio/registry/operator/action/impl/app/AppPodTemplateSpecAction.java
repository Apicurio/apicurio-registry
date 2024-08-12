package io.apicurio.registry.operator.action.impl.app;

import io.apicurio.registry.operator.action.AbstractBasicAction;
import io.apicurio.registry.operator.action.ActionOrder;
import io.apicurio.registry.operator.context.CRContext;
import io.apicurio.registry.operator.resource.ResourceKey;
import io.apicurio.registry.operator.state.NoState;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

import static io.apicurio.registry.operator.action.ActionOrder.ORDERING_FIRST;
import static io.apicurio.registry.operator.resource.ResourceFactory.APP_CONTAINER_NAME;
import static io.apicurio.registry.operator.resource.ResourceKey.APP_DEPLOYMENT_KEY;
import static io.apicurio.registry.operator.utils.PodTemplateSpecUtils.process;

@ApplicationScoped
public class AppPodTemplateSpecAction extends AbstractBasicAction {

    @Override
    public List<ResourceKey<?>> supports() {
        return List.of(APP_DEPLOYMENT_KEY);
    }

    @Override
    public ActionOrder ordering() {
        return ORDERING_FIRST;
    }

    @Override
    public boolean shouldRun(NoState state, CRContext crContext) {
        return crContext.getPrimary().getSpec().getApp() != null
                && crContext.getPrimary().getSpec().getApp().getPodTemplate() != null;
    }

    @Override
    public void run(NoState state, CRContext crContext) {
        crContext.withDesiredResource(APP_DEPLOYMENT_KEY, d -> {
            var base = d.getSpec().getTemplate();
            var spec = crContext.getPrimary().getSpec().getApp().getPodTemplate().edit().build();
            process(spec, base, APP_CONTAINER_NAME);
            d.getSpec().setTemplate(spec);
        });
    }
}
