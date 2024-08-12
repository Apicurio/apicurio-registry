package io.apicurio.registry.operator.action.impl.ui;

import io.apicurio.registry.operator.action.AbstractAction;
import io.apicurio.registry.operator.action.ActionOrder;
import io.apicurio.registry.operator.context.CRContext;
import io.apicurio.registry.operator.resource.ResourceKey;
import io.apicurio.registry.operator.state.impl.UIEnvCache;
import io.fabric8.kubernetes.api.model.networking.v1.HTTPIngressPath;
import io.fabric8.kubernetes.api.model.networking.v1.IngressRule;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

import static io.apicurio.registry.operator.action.ActionOrder.ORDERING_LATE;
import static io.apicurio.registry.operator.resource.ResourceFactory.UI_CONTAINER_NAME;
import static io.apicurio.registry.operator.resource.ResourceKey.*;
import static io.apicurio.registry.operator.state.impl.EnvCachePriority.OPERATOR_LOW;
import static io.apicurio.registry.operator.utils.TraverseUtils.where;

@ApplicationScoped
public class UIEnvApplyAction extends AbstractAction<UIEnvCache> {

    @Override
    public List<ResourceKey<?>> supports() {
        return List.of(UI_DEPLOYMENT_KEY);
    }

    @Override
    public ActionOrder ordering() {
        return ORDERING_LATE;
    }

    @Override
    public Class<UIEnvCache> getStateClass() {
        return UIEnvCache.class;
    }

    @Override
    public void run(UIEnvCache state, CRContext crContext) {

        crContext.withExistingResource(APP_SERVICE_KEY, s -> {
            crContext.withExistingResource(APP_INGRESS_KEY, i -> {
                for (IngressRule rule : i.getSpec().getRules()) {
                    for (HTTPIngressPath path : rule.getHttp().getPaths()) {
                        if (s.getMetadata().getName().equals(path.getBackend().getService().getName())) {
                            state.add("REGISTRY_API_URL", "http://%s/apis/registry/v3" // TODO: http
                                                                                       // vs https?
                                    .formatted(rule.getHost()), OPERATOR_LOW);
                            return;
                        }
                    }
                }
            });
        });

        crContext.withDesiredResource(UI_DEPLOYMENT_KEY, d -> {
            where(d.getSpec().getTemplate().getSpec().getContainers(),
                    c -> UI_CONTAINER_NAME.equals(c.getName()), c -> {
                        c.setEnv(state.getEnvAndReset());
                    });
        });
    }
}
