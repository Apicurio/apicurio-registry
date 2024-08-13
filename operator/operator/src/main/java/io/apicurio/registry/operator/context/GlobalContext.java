package io.apicurio.registry.operator.context;

import io.apicurio.registry.operator.action.Action;
import io.apicurio.registry.operator.api.v3.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.resource.ResourceKey;
import io.apicurio.registry.operator.state.impl.ClusterInfo;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.apicurio.registry.operator.utils.FunctionalUtils.returnSecondArg;
import static io.apicurio.registry.operator.utils.LogUtils.contextPrefix;

/**
 * This class represents state and operations related to the global context, i.e. not tied to a specific
 * instance of a primary resource.
 * <p>
 * Since most global state can be represented using application-scoped beans, e.g. {@link ClusterInfo}, this
 * class should only contain state that cannot conveniently use them.
 */
@ApplicationScoped
public class GlobalContext {

    private static final Logger log = LoggerFactory.getLogger(GlobalContext.class);

    @Inject
    Instance<Action<?>> actions;

    private final Map<ResourceID, CRContext> crContextMap = new ConcurrentHashMap<>();

    private List<Action<?>> getActions() {
        return actions.stream().sorted(Comparator.comparingInt(a -> a.ordering().getValue()))
                .collect(Collectors.toList()); // TODO: Cache?
    }

    private <T> T withCRContextReturn(ApicurioRegistry3 primary, Context<ApicurioRegistry3> context,
            Function<CRContext, T> run) {
        var crContext = crContextMap.computeIfAbsent(ResourceID.fromResource(primary), k -> new CRContext());
        try {
            // TODO: At the moment, we cannot concurrently run multiple reconcile processes for the same CR.
            crContext.LOCK.lock();
            if (!crContext.isInitialized) {
                crContext.initialize(getActions(), primary, context);
                crContext.isInitialized = true;
            }
            return run.apply(crContext);
        } finally {
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
    public <T, R> T reconcileReturn(ResourceKey<R> key, ApicurioRegistry3 primary,
            Context<ApicurioRegistry3> context, BiFunction<CRContext, R, T> postProcess) {
        log.info("{}Reconciling {}", contextPrefix(primary), key);
        return withCRContextReturn(primary, context, crContext -> {
            var r = crContext.runActions(getActions(), primary, context, key);
            var rval = postProcess.apply(crContext, r);
            crContext.reset();
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
    public <R> R reconcileReturn(ResourceKey<R> key, ApicurioRegistry3 primary,
            Context<ApicurioRegistry3> context) {
        return reconcileReturn(key, primary, context, returnSecondArg());
    }

    /**
     * Remove CR context for the given primary resource.
     */
    public void cleanup(ApicurioRegistry3 primary) {
        crContextMap.remove(ResourceID.fromResource(primary));
        log.info("{}CR context deleted", contextPrefix(primary));
    }
}
