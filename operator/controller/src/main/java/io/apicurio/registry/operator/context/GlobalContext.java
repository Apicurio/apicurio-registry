package io.apicurio.registry.operator.context;

import io.apicurio.registry.operator.action.Action;
import io.apicurio.registry.operator.action.impl.ExampleAction;
import io.apicurio.registry.operator.action.impl.StatusUpdaterAction;
import io.apicurio.registry.operator.action.impl.app.AppIngressHostAction;
import io.apicurio.registry.operator.action.impl.app.DefaultAppEnvAction;
import io.apicurio.registry.operator.action.impl.ui.DefaultUIEnvAction;
import io.apicurio.registry.operator.action.impl.ui.UIIngressHostAction;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.resource.ResourceKey;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

import static io.apicurio.registry.operator.utils.FunctionalUtils.returnSecondArg;
import static io.apicurio.registry.operator.utils.Mapper.toYAML;

/**
 * This class represents state and operations related to the global context, i.e. not tied to a specific
 * instance of a primary resource.
 */
public class GlobalContext {

    public static final GlobalContext INSTANCE = new GlobalContext();

    private static final Logger log = LoggerFactory.getLogger(GlobalContext.class);

    private final List<Action<?>> actions;

    private final Map<ResourceID, CRContext> crContextMap = new ConcurrentHashMap<>();

    private GlobalContext() {
        // spotless:off
        this.actions = List.of(
                new ExampleAction(),
                new DefaultAppEnvAction(),
                new AppIngressHostAction(),
                new DefaultUIEnvAction(),
                new UIIngressHostAction(),
                new StatusUpdaterAction()
        );
        // spotless:on
    }

    private <T> T withCRContextReturn(ApicurioRegistry3 primary, Context<ApicurioRegistry3> context,
            Function<CRContext, T> run) {
        var crContext = crContextMap.computeIfAbsent(ResourceID.fromResource(primary), k -> new CRContext());
        try {
            crContext.LOCK.lock();
            MDC.put("cr-context", " [%s:%s]".formatted(primary.getMetadata().getNamespace(),
                    primary.getMetadata().getName()));
            if (!crContext.isInitialized) {
                crContext.initialize(actions, primary, context);
                crContext.isInitialized = true;
            }
            return run.apply(crContext);
        } finally {
            MDC.remove("cr-context");
            crContext.reset();
            crContext.LOCK.unlock();
        }
    }

    /**
     * Run reconcile actions for the given resource, and return post-processed desired state of the resource.
     * If you do not need post-processing, use
     * {@link GlobalContext#reconcileReturn(ResourceKey, ApicurioRegistry3, Context)}.
     *
     * @param key Represents type of the resource to be reconciled.
     * @param primary Primary resource (CR) associated with the given resource.
     * @param context Current JOSDK context.
     * @param postProcess Action that will post-process the desired resource, after which the result is
     *            returned.
     */
    public <T, R extends HasMetadata> T reconcileReturn(ResourceKey<R> key, ApicurioRegistry3 primary,
            Context<ApicurioRegistry3> context, BiFunction<CRContext, R, T> postProcess) {
        log.info("Reconciling {}", key);
        return withCRContextReturn(primary, context, crContext -> {
            var r = crContext.runActions(actions, primary, context, key);
            log.debug("Desired {} is {}", key.getId(), toYAML(r));
            var rval = postProcess.apply(crContext, r);
            return rval;
        });
    }

    /**
     * Run reconcile actions for the given resource, and return desired state of the resource.
     *
     * @param key Represents type of the resource to be reconciled.
     * @param primary Primary resource (CR) associated with the given resource.
     * @param context Current JOSDK context.
     */
    public <R extends HasMetadata> R reconcileReturn(ResourceKey<R> key, ApicurioRegistry3 primary,
            Context<ApicurioRegistry3> context) {
        return reconcileReturn(key, primary, context, returnSecondArg());
    }

    /**
     * Remove CR context for the given primary resource.
     */
    public void cleanup(ApicurioRegistry3 primary) {
        crContextMap.remove(ResourceID.fromResource(primary));
        log.info("CR context deleted");
    }
}
