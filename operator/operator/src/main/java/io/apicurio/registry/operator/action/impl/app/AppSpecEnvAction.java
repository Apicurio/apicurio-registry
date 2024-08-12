package io.apicurio.registry.operator.action.impl.app;

import io.apicurio.registry.operator.action.AbstractAction;
import io.apicurio.registry.operator.action.ActionOrder;
import io.apicurio.registry.operator.context.CRContext;
import io.apicurio.registry.operator.resource.ResourceKey;
import io.apicurio.registry.operator.state.impl.AppEnvCache;
import io.apicurio.registry.operator.state.impl.EnvCachePriority;
import io.fabric8.kubernetes.api.model.EnvVar;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

import static io.apicurio.registry.operator.action.ActionOrder.ORDERING_EARLY;
import static io.apicurio.registry.operator.resource.ResourceKey.APP_DEPLOYMENT_KEY;
import static io.apicurio.registry.operator.utils.TraverseUtils.with;

@ApplicationScoped
public class AppSpecEnvAction extends AbstractAction<AppEnvCache> {

    @Override
    public List<ResourceKey<?>> supports() {
        return List.of(APP_DEPLOYMENT_KEY);
    }

    @Override
    public ActionOrder ordering() {
        return ORDERING_EARLY;
    }

    @Override
    public Class<AppEnvCache> getStateClass() {
        return AppEnvCache.class;
    }

    @Override
    public AppEnvCache initialize(CRContext crContext) {
        return new AppEnvCache();
    }

    @Override
    public void run(AppEnvCache state, CRContext crContext) {

        with(crContext.getPrimary().getSpec().getApp(), appSpec -> {
            with(appSpec.getEnv(), env -> {
                EnvVar last = null;
                for (EnvVar e : env) {
                    state.add(e, EnvCachePriority.SPEC_HIGH,
                            last == null ? new String[] {} : new String[] { last.getName() });
                    last = e;
                }
            });
        });
    }
}
