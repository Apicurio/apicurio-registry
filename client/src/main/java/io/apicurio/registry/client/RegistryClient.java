/*
 * Copyright 2019 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.client;

import org.eclipse.microprofile.rest.client.RestClientBuilder;

import java.io.Closeable;
import java.io.IOException;
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
import java.util.function.Function;

/**
 * @author Ales Justin
 */
public class RegistryClient {

    private static BiFunction<Method, Throwable, Throwable> UNWRAPPER;

    static {
        try {
            ClassLoader cl = RegistryClient.class.getClassLoader();
            //noinspection unchecked
            UNWRAPPER = (BiFunction<Method, Throwable, Throwable>) cl.loadClass("io.apicurio.registry.client.ext.RestEasyExceptionUnwrapper").newInstance();
            // simple test to see if we have RestEasy on the classpath ...
            UNWRAPPER.apply(null, new Throwable());
        } catch (Throwable ignored) {
            UNWRAPPER = (m, t) -> t;
        }
    }

    // TODO -- more options?
    public static class Builder {
        private URI baseUrl;
        private ExecutorService executor;

        public Builder() {
        }

        public Builder setBaseUrl(String baseUrl) {
            try {
                this.baseUrl = new URI(Objects.requireNonNull(baseUrl));
                return this;
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException(e);
            }
        }

        public Builder setBaseUrl(URI baseUrl) {
            this.baseUrl = Objects.requireNonNull(baseUrl);
            return this;
        }

        public Builder setExecutor(ExecutorService executor) {
            this.executor = executor; // can be null
            return this;
        }

        public RegistryService build() {
            return (RegistryService) Proxy.newProxyInstance(
                RegistryClient.class.getClassLoader(),
                new Class[]{RegistryService.class},
                new ServiceProxy(this)
            );
        }
    }

    private RegistryClient() {
    }

    public static RegistryService create(String baseUrl) {
        return new Builder().setBaseUrl(baseUrl).build();
    }

    public static RegistryService cached(String baseUrl) {
        return cached(create(baseUrl));
    }

    public static RegistryService cached(RegistryService delegate) {
        return new CachedRegistryService(delegate);
    }

    // RestEasy wraps CompletionStage exceptions in some weird HandlerException
    private static Throwable unwrap(Method method, Throwable t) {
        if (t instanceof CompletionException) {
            t = t.getCause();
        }
        return UNWRAPPER.apply(method, t);
    }

    private static class ServiceProxy implements InvocationHandler {

        private final Map<Class<?>, Object> targets = new ConcurrentHashMap<>();
        private final AtomicBoolean closed = new AtomicBoolean();

        private final URI baseUri;
        private final ExecutorService executor;

        private final boolean shutdownExecutor;

        private ServiceProxy(Builder builder) {
            this.baseUri = builder.baseUrl;
            this.shutdownExecutor = (builder.executor == null);
            this.executor = (shutdownExecutor ? Executors.newFixedThreadPool(10) : builder.executor);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            try {
                String methodName = method.getName();
                if ((args == null || args.length == 0)) {
                    if ("close".equals(methodName)) {
                        if (closed.compareAndSet(false, true)) {
                            targets.values().forEach(o -> {
                                try {
                                    ((Closeable) o).close();
                                } catch (IOException ignore) {
                                }
                            });
                            // work-around a bug in RestEasy builder
                            if (shutdownExecutor) {
                                executor.shutdown();
                            }
                        }
                        return null;
                    } else if ("reset".equals(methodName)) {
                        // do nothing
                        return null;
                    }
                }

                if (closed.get()) {
                    throw new IllegalStateException("Registry client already closed!");
                }

                Class<?> targetClass = method.getDeclaringClass();
                Object target = targets.compute(targetClass, (aClass, o) -> RestClientBuilder.newBuilder()
                                                                                             .baseUri(baseUri)
                                                                                             .executorService(executor)
                                                                                             .build(targetClass));
                Object result = method.invoke(target, args);
                if (result instanceof CompletionStage) {
                    CompletionStage cs = (CompletionStage) result;
                    //noinspection unchecked
                    return cs.exceptionally((Function<Throwable, Object>) t -> {
                        throw new CompletionException(unwrap(method, t));
                    });
                }
                return result;
            } catch (InvocationTargetException e) {
                throw unwrap(method, e.getCause()); // unwrap
            }
        }
    }
}
