package io.apicurio.registry.client;

import io.apicurio.registry.client.ext.RestEasyExceptionUnwrapper;
import org.eclipse.microprofile.rest.client.RestClientBuilder;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Generic client for multi REST interfaces.
 *
 * @author Ales Justin
 */
public class GenericClient {
    private static final BiFunction<Method, Throwable, Throwable> UNWRAPPER;

    static {
        BiFunction<Method, Throwable, Throwable> unwrapper;
        try {
            unwrapper = new RestEasyExceptionUnwrapper();
            // simple test to see if we have RestEasy on the classpath ...
            unwrapper.apply(null, new Throwable());
        } catch (Throwable ignored) {
            // fall-back to no-op unwrapper
            unwrapper = (m, t) -> t;
        }
        UNWRAPPER = unwrapper;
    }

    /**
     * Create a service client.
     *
     * @param serviceInterface the interface implemented by the service
     * @param baseUrl          the base URL of the service
     * @param <I>              the type of the service interface
     * @return an instance representing the client
     */
    public static <I> I create(Class<I> serviceInterface, String baseUrl) {
        return new Builder<>(serviceInterface).setBaseUrl(baseUrl).build();
    }

    private GenericClient() {
    }

    // TODO -- more options?
    public static class Builder<I> {
        private final Class<I> serviceInterface;
        private URI baseUrl;
        private ExecutorService executor;
        private BiFunction<Method, Object[], Object> customMethods;
        private Consumer<Object> resultConsumer;

        public Builder(Class<I> serviceInterface) {
            if (!serviceInterface.isInterface()) {
                throw new IllegalArgumentException(serviceInterface + " is not an interface");
            }
            this.serviceInterface = serviceInterface;
        }

        public Builder<I> setBaseUrl(String baseUrl) {
            try {
                this.baseUrl = new URI(Objects.requireNonNull(baseUrl));
                return this;
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException(e);
            }
        }

        public Builder<I> setBaseUrl(URI baseUrl) {
            this.baseUrl = Objects.requireNonNull(baseUrl);
            return this;
        }

        public Builder<I> setExecutor(ExecutorService executor) {
            this.executor = executor; // can be null
            return this;
        }

        public Builder<I> setCustomMethods(BiFunction<Method, Object[], Object> customMethods) {
            this.customMethods = customMethods;
            return this;
        }

        public Builder<I> setResultConsumer(Consumer<Object> resultConsumer) {
            this.resultConsumer = resultConsumer;
            return this;
        }

        public I build() {
            return serviceInterface.cast(Proxy.newProxyInstance(
                GenericClient.class.getClassLoader(),
                new Class[]{serviceInterface, AutoCloseable.class},
                new ServiceProxy(this)
            ));
        }
    }

    private static class ServiceProxy implements InvocationHandler {

        private final AtomicBoolean closed = new AtomicBoolean();
        private final URI baseUri;
        private final ExecutorService executor;
        private final boolean shutdownExecutor;
        private final Map<Class<?>, Object> targets = new ConcurrentHashMap<>();
        private final Consumer<Object> resultConsumer;
        private final BiFunction<Method, Object[], Object> customMethods;

        private ServiceProxy(Builder<?> builder) {
            this.baseUri = builder.baseUrl;
            this.shutdownExecutor = (builder.executor == null);
            this.executor = (shutdownExecutor ? Executors.newFixedThreadPool(10) : builder.executor);
            this.resultConsumer = builder.resultConsumer;
            this.customMethods = builder.customMethods;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (Proxy.isProxyClass(obj.getClass())) {
                obj = Proxy.getInvocationHandler(obj);
            }
            return this == obj;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            int paramCount = method.getParameterCount();
            Class<?> rtype = method.getReturnType();

            // AutoCloseable.close()
            if ("close".equals(methodName) && paramCount == 0 && rtype == void.class) {
                if (closed.compareAndSet(false, true)) {
                    Throwable ex = null;
                    for (Object o : targets.values()) {
                        if (o instanceof AutoCloseable) {
                            try {
                                ((AutoCloseable) o).close();
                            } catch (Throwable e) {
                                if (ex == null) ex = e;
                                else ex.addSuppressed(e);
                            }
                        }
                    }
                    // work-around a bug in RestEasy builder
                    if (shutdownExecutor) {
                        try {
                            executor.shutdown();
                        } catch (Throwable e) {
                            if (ex == null) ex = e;
                            else ex.addSuppressed(e);
                        }
                    }
                    if (ex != null) {
                        throw ex;
                    }
                }
                return null;
            }

            if (closed.get()) {
                throw new IllegalStateException("Client already closed!");
            }

            if (customMethods != null) {
                Object customResult = customMethods.apply(method, args);
                if (customResult != Void.class) {
                    return customResult;
                }
            }

            Class<?> targetClass = method.getDeclaringClass();
            Object target;
            if (targetClass.isInterface()) {
                target = targets.computeIfAbsent(
                    targetClass,
                    tc -> RestClientBuilder.newBuilder()
                                           .baseUri(baseUri)
                                           .executorService(executor)
                                           .build(tc)
                );
            } else {
                // Object methods like equals(), hashCode(), wait(), ...
                target = this;
            }

            Object result;

            try {
                result = method.invoke(target, args);
            } catch (InvocationTargetException e) {
                throw UNWRAPPER.apply(method, e.getCause()); // unwrap
            }

            if (result instanceof CompletionStage) {
                CompletionStage<?> cs = (CompletionStage<?>) result;
                result = cs.whenComplete((r, t) -> {
                    if (t != null) {
                        throw new CompletionException(
                            UNWRAPPER.apply(method, t instanceof CompletionException ? t.getCause() : t)
                        );
                    } else {
                        if (resultConsumer != null) {
                            resultConsumer.accept(r);
                        }
                    }
                });
            } else {
                if (resultConsumer != null) {
                    resultConsumer.accept(result);
                }
            }

            return result;
        }
    }
}
