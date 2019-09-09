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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Ales Justin
 */
public class RegistryClient {

    // TODO -- more options?
    public static RegistryService create(String baseUrl) throws Exception {
        return (RegistryService) Proxy.newProxyInstance(
            RegistryClient.class.getClassLoader(),
            new Class[]{RegistryService.class},
            new ServiceProxy(new URI(baseUrl))
        );
    }

    private static class ServiceProxy implements InvocationHandler {

        private final Map<Class, Object> targets = new ConcurrentHashMap<>();
        private final AtomicBoolean closed = new AtomicBoolean();

        private final URI baseUri;
        private final RegistryFilter filter;

        public ServiceProxy(URI baseUri) {
            this.baseUri = baseUri;
            this.filter = new RegistryFilter();
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            try {
                String methodName = method.getName();
                if ((args == null || args.length == 0) && "close".equals(methodName)) {
                    if (closed.compareAndSet(false, true)) {
                        targets.values().forEach(o -> {
                            try {
                                ((Closeable) o).close();
                            } catch (IOException ignore) {
                            }
                        });
                    }
                    return null; // close() is void
                }

                if (closed.get()) {
                    throw new IllegalStateException("Registry client already closed!");
                }

                Class<?> targetClass = method.getDeclaringClass();
                Object target = targets.compute(targetClass, (aClass, o) -> RestClientBuilder.newBuilder()
                                                                                             .baseUri(baseUri)
                                                                                             .register(filter)
                                                                                             .build(targetClass));
                return method.invoke(target, args);
            } catch (InvocationTargetException e) {
                throw e.getCause(); // unwrap
            }
        }
    }
}
