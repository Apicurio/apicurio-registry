package io.apicurio.registry.streams.utils;

import org.apache.kafka.streams.kstream.ForeachAction;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * An {@link ForeachAction} implementation that dispatches {@link ForeachAction#apply(Object, Object)}
 * invocation to {@link #register(ForeachAction) registered} actions.
 */
public class ForeachActionDispatcher<K, V> implements ForeachAction<K, V> {
    private final List<ForeachAction<? super K, ? super V>> actions = new CopyOnWriteArrayList<>();

    @Override
    public void apply(K key, V value) {
        for (ForeachAction<? super K, ? super V> action : actions) {
            action.apply(key, value);
        }
    }

    public void register(ForeachAction<? super K, ? super V> action) {
        actions.add(Objects.requireNonNull(action));
    }

    public void deregister(ForeachAction<? super K, ? super V> action) {
        actions.remove(Objects.requireNonNull(action));
    }
}
