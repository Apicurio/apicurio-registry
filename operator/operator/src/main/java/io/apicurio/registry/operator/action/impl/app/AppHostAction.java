package io.apicurio.registry.operator.action.impl.app;

import io.apicurio.registry.operator.action.AbstractBasicAction;
import io.apicurio.registry.operator.context.CRContext;
import io.apicurio.registry.operator.resource.ResourceKey;
import io.apicurio.registry.operator.state.NoState;
import io.fabric8.kubernetes.api.model.networking.v1.HTTPIngressPath;
import io.fabric8.kubernetes.api.model.networking.v1.IngressRule;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

import static io.apicurio.registry.operator.resource.ResourceKey.APP_INGRESS_KEY;
import static io.apicurio.registry.operator.resource.ResourceKey.APP_SERVICE_KEY;
import static io.apicurio.registry.operator.utils.TraverseUtils.with;

@ApplicationScoped
public class AppHostAction extends AbstractBasicAction {

    @Override
    public List<ResourceKey<?>> supports() {
        return List.of(APP_INGRESS_KEY);
    }

    @Override
    public void run(NoState state, CRContext crContext) {

        with(crContext.getPrimary().getSpec().getApp(), appSpec -> {
            with(appSpec.getHost(), host -> {
                crContext.withExistingResource(APP_SERVICE_KEY, s -> {
                    crContext.withDesiredResource(APP_INGRESS_KEY, i -> {
                        for (IngressRule rule : i.getSpec().getRules()) {
                            for (HTTPIngressPath path : rule.getHttp().getPaths()) {
                                if (s.getMetadata().getName()
                                        .equals(path.getBackend().getService().getName())) {
                                    rule.setHost(crContext.getPrimary().getSpec().getApp().getHost());
                                    return;
                                }
                            }
                        }
                    });
                });
            });
        });
    }
}
