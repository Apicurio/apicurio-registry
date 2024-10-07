package io.apicurio.registry.operator.context;

import io.apicurio.registry.operator.OperatorException;
import io.apicurio.registry.operator.action.Action;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3Status;
import io.apicurio.registry.operator.resource.ResourceKey;
import io.apicurio.registry.operator.state.NoState;
import io.apicurio.registry.operator.state.State;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import lombok.Getter;
import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import static io.apicurio.registry.operator.resource.ResourceKey.REGISTRY_KEY;
import static io.apicurio.registry.operator.utils.Mapper.duplicate;
import static io.apicurio.registry.operator.utils.Mapper.toYAML;
import static io.javaoperatorsdk.operator.processing.dependent.kubernetes.GenericKubernetesResourceMatcher.match;
import static java.util.Objects.requireNonNull;

/**
 * This class represents state and operations related to the CR context, i.e. tied to a specific instance of a
 * primary resource.
 */
public class CRContext {

    private static final Logger log = LoggerFactory.getLogger(CRContext.class);

    final ReentrantLock LOCK = new ReentrantLock();

    boolean isInitialized;

    private final Map<Class<?>, State> actionStateMap = new HashMap<>();

    @Getter
    private ApicurioRegistry3 primary;

    @Getter
    private Context<ApicurioRegistry3> context;

    @Getter
    private ResourceKey<?> desiredKey;

    private Object desired;

    private Object previousDesired;

    @Getter
    private boolean updatePrimary;

    @Getter
    private boolean updateStatus;

    /**
     * Initialize a new (this) CR context.
     */
    void initialize(List<Action<?>> actions, ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {
        log.info("Initializing new CR context");
        this.primary = primary;
        this.context = context;
        for (Action<?> action : actions) {
            log.debug("Initializing action {}", action.getClass());
            var stateClass = action.getStateClass();
            var existing = actionStateMap.get(stateClass);
            var state = action.initialize(this);
            if (state != null) {
                if (existing == null) {
                    actionStateMap.put(stateClass, state);
                } else if (!NoState.class.equals(stateClass)) {
                    log.warn("State {} has already been initialized", stateClass.getCanonicalName());
                }
            } else {
                if (existing == null) {
                    throw new OperatorException(
                            "State " + stateClass.getCanonicalName() + " initialization returned null");
                }
            }
        }
    }

    /**
     * Run actions with this CR context for the given resource.
     */
    <R extends HasMetadata> R runActions(List<Action<?>> actions, ApicurioRegistry3 primary,
            Context<ApicurioRegistry3> context, ResourceKey<R> key) {
        this.primary = primary;
        this.context = context;
        this.desiredKey = key;
        boolean debugDesiredResourceChanges = ConfigProvider.getConfig()
                .getOptionalValue("apicurio.operator.debug-desired-resource-changes", Boolean.class)
                .orElse(false);
        var activeStateClasses = new HashSet<Class<? extends State>>();
        for (Action<?> action : actions) {
            var stateClass = action.getStateClass();
            var state = requireState(stateClass);
            var a = (Action) action;
            if (a.supports().contains(key)) {
                if (a.shouldRun(state, this)) {
                    log.debug("Running action {}", a.getClass().getCanonicalName());
                    if (debugDesiredResourceChanges && desired != null) {
                        previousDesired = duplicate((R) desired, key.getKlass());
                    }
                    a.run(state, this);
                    if (debugDesiredResourceChanges && desired != null) {
                        var res = match((R) desired, (R) previousDesired, false, false, context);
                        if (!res.matched()) {
                            log.debug("Desired resource was updated by action {}:\n{}",
                                    a.getClass().getCanonicalName(), toYAML(desired));
                        }
                    }
                    activeStateClasses.add(stateClass);
                } else {
                    log.debug("Skipping action {}", a.getClass().getCanonicalName());
                }
            } else {
                log.trace("Skipping action {}, because it does not support resource {}", a.getClass(), key);
            }
        }
        for (Class<? extends State> stateClass : activeStateClasses) {
            log.debug("Running afterReconciliation() for {}", stateClass.getCanonicalName());
            requireState(stateClass).afterReconciliation();
        }
        return getDesiredResource(key);
    }

    /**
     * Get an instance of a {@link State} stored in this CR context. If the state does not exist, throws an
     * {@link OperatorException}.
     */
    private <STATE extends State> STATE requireState(Class<STATE> stateClass) {
        requireNonNull(stateClass);
        var state = actionStateMap.get(stateClass);
        if (state == null) {
            throw new OperatorException("State " + stateClass.getCanonicalName() + " not found");
        } else {
            return (STATE) state;
        }
    }

    /**
     * Execute code that requires given resource, if it exists. The code MUST NOT change the resource.
     */
    public <R extends HasMetadata> void withExistingResource(ResourceKey<R> key, Consumer<R> action) {
        if (REGISTRY_KEY.equals(key)) {
            throw new OperatorException("Use CRContext::getPrimary() if you are not updating the CR.");
        } else {
            var r = context.getSecondaryResource(key.getKlass(), key.getDiscriminator());
            if (r.isPresent()) {
                action.accept(r.get());
            } else {
                log.debug("Existing resource {} not found.", key);
            }
        }
    }

    private <R extends HasMetadata> R getDesiredResource(ResourceKey<R> key) {
        if (!desiredKey.equals(key)) {
            throw new OperatorException("Attempting to process multiple desired resources at once. "
                    + "Current is " + desiredKey + " but " + key + " was requested.");
        }
        if (desired == null) {
            desiredKey = key;
            if (REGISTRY_KEY.equals(key)) {
                desired = duplicate(primary, ApicurioRegistry3.class);
            } else {
                log.debug("Getting new {} resource from factory.", key);
                desired = key.getFactory().apply(primary);
                requireNonNull(desired);
            }
        }
        return (R) desired;
    }

    /**
     * Execute code that wants to change the desired state of the current resource.
     */
    public <R extends HasMetadata> void withDesiredResource(ResourceKey<R> key, Consumer<R> action) {
        action.accept(getDesiredResource(key));
        if (REGISTRY_KEY.equals(key)) {
            updatePrimary = true;
        }
    }

    /**
     * Execute code that wants to change the desired state of the CR status.
     */
    public void withDesiredStatus(Consumer<ApicurioRegistry3Status> action) {
        var primary = getDesiredResource(REGISTRY_KEY);
        if (primary.getStatus() == null) {
            primary.setStatus(new ApicurioRegistry3Status());
        }
        action.accept(primary.getStatus());
        updateStatus = true;
    }

    void reset() {
        primary = null;
        context = null;
        desiredKey = null;
        desired = null;
        previousDesired = null;
        updatePrimary = false;
        updateStatus = false;
    }
}
