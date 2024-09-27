package io.apicurio.registry.operator.util;

import io.apicurio.registry.operator.OperatorException;
import io.quarkus.arc.Arc;
import io.quarkus.runtime.StartupEvent;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * This is needed because QOSDK does not support bean injection into dependent resource classes. We work
 * around this by retrieving `@ApplicationScoped` beans with `Arc.container()`. This is a helper class for
 * closing resources.
 * <p>
 * See also {@link io.apicurio.registry.operator.util.HostUtil#startup(StartupEvent)}
 */
public class BeanUtil {

    private BeanUtil() {
    }

    public static <T, R> R withBeanR(Class<T> klass, Function<T, R> action) {
        try (var instance = Arc.container().instance(klass)) {
            if (!instance.isAvailable()) {
                throw new OperatorException(
                        "Bean of type %s is not available".formatted(klass.getCanonicalName()));
            }
            return action.apply(instance.get());
        }
    }

    public static <T> void withBean(Class<T> klass, Consumer<T> action) {
        withBeanR(klass, bean -> {
            action.accept(bean);
            return null;
        });
    }
}
