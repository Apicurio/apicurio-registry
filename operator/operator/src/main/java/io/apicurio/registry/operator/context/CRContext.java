package io.apicurio.registry.operator.context;

import io.apicurio.registry.operator.OperatorException;
import io.apicurio.registry.operator.action.Action;
import io.apicurio.registry.operator.api.v3.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.resource.ResourceKey;
import io.apicurio.registry.operator.state.NoState;
import io.apicurio.registry.operator.state.State;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import static io.apicurio.registry.operator.resource.ResourceKey.REGISTRY_KEY;
import static io.apicurio.registry.operator.utils.LogUtils.contextPrefix;
import static io.apicurio.registry.operator.utils.ResourceUtils.duplicate;
import static java.util.Objects.requireNonNull;

/**
 * This class represents state and operations related to the CR context, i.e. tied to a specific instance of a
 * primary resource.
 * <p>
 * Since state related to the CR context cannot be represented using CDI beans without implementing a custom
 * CDI scope, this class contains state that is used by {@link Action}.
 */
public class CRContext {

    private static final Logger log = LoggerFactory.getLogger(CRContext.class);

    final ReentrantLock LOCK = new ReentrantLock();

    boolean isInitialized;

    private final Map<Class<?>, State> actionStateMap = new HashMap<>();

    private final Map<String, Object> desired = new HashMap<>();

    @Getter
    private ApicurioRegistry3 primary;

    @Getter
    private Context<ApicurioRegistry3> context;

    @Getter
    private boolean updatePrimary;

    @Getter
    private boolean updateStatus;

    @Getter
    private Duration reschedule;

    /**
     * Initialize a new (this) CR context. This includes:
     * <ul>
     * <li>Initialize and store action states, by running {@link Action#initialize(CRContext)}</li>
     * </ul>
     */
    void initialize(List<Action<?>> actions, ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {
        log.info("{}Initializing new CR context", contextPrefix(primary));
        this.primary = primary;
        this.context = context;
        for (Action<?> action : actions) {
            log.debug("{}Initializing action {}", contextPrefix(primary), action.getClass());
            var key = action.getStateClass();
            var existing = actionStateMap.get(key);
            var state = action.initialize(this);
            if (state != null) {
                if (existing == null) {
                    actionStateMap.put(key, state);
                } else if (!NoState.class.equals(key)) {
                    log.warn("{}State {} has already been initialized", contextPrefix(primary), key);
                }
            } else {
                if (existing == null) {
                    throw new OperatorException("State " + key + " initialization returned null");
                }
            }
        }
    }

    /**
     * Run actions with this CR context. This includes:
     * <ul>
     * <li>Selecting which actions should be executed</li>
     * <li>Providing {@link State} to each action</li>
     * <li>Call the {@link State#afterReconciliation()} method</li>
     * </ul>
     */
    <R> R runActions(List<Action<?>> actions, ApicurioRegistry3 primary, Context<ApicurioRegistry3> context,
            ResourceKey<R> key) {
        this.primary = primary;
        this.context = context;
        var activeStateClasses = new HashSet<Class<? extends State>>();
        for (Action<?> action : actions) {
            var stateClass = action.getStateClass();
            var state = requireState(stateClass);
            var a = (Action) action;
            if (a.supports().contains(key)) {
                if (a.shouldRun(state, this)) {
                    log.debug("{}Running action {}", contextPrefix(primary), a.getClass());
                    a.run(state, this);
                    activeStateClasses.add(stateClass);
                } else {
                    log.trace("{}Skipping action {}", contextPrefix(primary), a.getClass());
                }
            } else {
                log.trace("{}Skipping action {}, because it does not support resource {}",
                        contextPrefix(primary), a.getClass(), key);
            }
        }
        for (Class<? extends State> stateClass : activeStateClasses) {
            log.trace("{}Running afterReconciliation for {}", contextPrefix(primary), stateClass);
            requireState(stateClass).afterReconciliation();
        }
        return getDesiredResource(key);
    }

    /**
     * Get an instance of a {@link State} stored in this CR context. If the state does not exist, throws an
     * {@link OperatorException}.
     */
    public <STATE extends State> STATE requireState(Class<STATE> stateClass) {
        requireNonNull(stateClass);
        var state = actionStateMap.get(stateClass);
        if (state == null) {
            throw new OperatorException("State " + stateClass.getCanonicalName() + " not found");
        } else {
            return (STATE) state;
        }
    }

    /**
     * Execute code that requires given resource, if it exists. The code MUST NOT change the resource state.
     * <p>
     * This function MUST NOT be used for the primary resource, use {@link CRContext#getPrimary()} instead.
     */
    public <R> void withExistingResource(ResourceKey<R> key, Consumer<R> action) {
        if (REGISTRY_KEY.equals(key)) {
            throw new OperatorException("Use CRContext::getPrimary() if you are not updating the CR.");
        } else {
            var r = context.getSecondaryResource(key.getKlass(), key.getDiscriminator());
            if (r.isPresent()) {
                action.accept(r.get());
            } else {
                log.debug("{}Existing resource {} not found.", contextPrefix(primary), key);
            }
        }
    }

    private <R> R getDesiredResource(ResourceKey<R> key) {
        var r = desired.get(key.getId());
        if (r == null) {
            if (REGISTRY_KEY.equals(key)) {
                r = duplicate(primary, ApicurioRegistry3.class);
            } else {
                log.debug("{}Getting fresh {} resource from factory.", contextPrefix(primary), key);
                r = key.getFactory().apply(primary);
                requireNonNull(r);
            }
            desired.put(key.getId(), r);
        }
        return (R) r;
    }

    /**
     * Execute code that wants to change the desired state of a resource.
     * <p>
     * This function MAY be used to update the primary resource.
     */
    public <R> void withDesiredResource(ResourceKey<R> key, Consumer<R> action) {
        action.accept(getDesiredResource(key));
        if (REGISTRY_KEY.equals(key)) {
            updatePrimary = true;
        }
    }

    /**
     * Request to reschedule the reconciliation loop with the given timeout.
     */
    public void rescheduleSeconds(int seconds) {
        var d = Duration.ofSeconds(seconds);
        if (reschedule == null || reschedule.compareTo(d) > 0) {
            reschedule = d;
        }
    }

    /**
     * Request to reschedule the reconciliation loop soon.
     */
    public void reschedule() {
        rescheduleSeconds(5);
    }

    void reset() {
        primary = null;
        updatePrimary = false;
        updateStatus = false;
        desired.clear();
        reschedule = null;
    }
}
