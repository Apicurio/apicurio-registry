package io.apicurio.registry.operator.action.impl.ui;

import io.apicurio.registry.operator.action.AbstractAction;
import io.apicurio.registry.operator.action.ActionOrder;
import io.apicurio.registry.operator.context.CRContext;
import io.apicurio.registry.operator.resource.ResourceKey;
import io.apicurio.registry.operator.state.impl.EnvCachePriority;
import io.apicurio.registry.operator.state.impl.UIEnvCache;
import io.fabric8.kubernetes.api.model.EnvVar;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

import static io.apicurio.registry.operator.action.ActionOrder.ORDERING_EARLY;
import static io.apicurio.registry.operator.resource.ResourceKey.UI_DEPLOYMENT_KEY;
import static io.apicurio.registry.operator.utils.TraverseUtils.with;

@ApplicationScoped
public class UISpecEnvAction extends AbstractAction<UIEnvCache> {

    @Override
    public List<ResourceKey<?>> supports() {
        return List.of(UI_DEPLOYMENT_KEY);
    }

    @Override
    public ActionOrder ordering() {
        return ORDERING_EARLY;
    }

    @Override
    public Class<UIEnvCache> getStateClass() {
        return UIEnvCache.class;
    }

    @Override
    public UIEnvCache initialize(CRContext crContext) {
        return new UIEnvCache();
    }

    @Override
    public void run(UIEnvCache state, CRContext crContext) {

        with(crContext.getPrimary().getSpec().getUi(), uiSpec -> {
            with(uiSpec.getEnv(), env -> {
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
