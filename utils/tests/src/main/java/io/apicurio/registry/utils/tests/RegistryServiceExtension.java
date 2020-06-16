/*
 * Copyright 2020 Red Hat
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

package io.apicurio.registry.utils.tests;

import io.apicurio.registry.client.RegistryClient;
import io.apicurio.registry.client.RegistryService;
import io.apicurio.registry.utils.IoUtil;
import org.junit.jupiter.api.extension.*;
import org.junit.platform.commons.util.AnnotationUtils;

import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.util.Collections.singletonList;

/**
 * @author Ales Justin
 */
public class RegistryServiceExtension implements TestTemplateInvocationContextProvider {

    private static final String REGISTRY_CLIENT_CREATE = "create";
    private static final String REGISTRY_CLIENT_CACHED = "cached";
    private static final String REGISTRY_CLIENT_ALL = "all";

    private enum ParameterType {
        REGISTRY_SERVICE,
        SUPPLIER,
        UNSUPPORTED
    }

    private static ParameterType getParameterType(Type type) {
        if (type instanceof Class) {
            if (type == RegistryService.class) {
                return ParameterType.REGISTRY_SERVICE;
            }
        } else if (type instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) type;
            Type rawType = pt.getRawType();
            if (rawType == RegistryService.class) {
                return ParameterType.REGISTRY_SERVICE;
            } else if (rawType == Supplier.class) {
                Type[] arguments = pt.getActualTypeArguments();
                if (arguments[0] == RegistryService.class) {
                    return ParameterType.SUPPLIER;
                }
            }
        }
        return ParameterType.UNSUPPORTED;
    }

    @Override
    public boolean supportsTestTemplate(ExtensionContext context) {
        return context.getTestMethod().map(method -> {
            Class<?>[] parameterTypes = method.getParameterTypes();
            for (int i = 0; i < parameterTypes.length; i++) {
                if (getParameterType(method.getGenericParameterTypes()[i]) != ParameterType.UNSUPPORTED) {
                    return true;
                }
            }
            return false;
        }).orElse(false);
    }

    @Override
    public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(ExtensionContext context) {
        RegistryServiceTest rst = AnnotationUtils.findAnnotation(context.getRequiredTestMethod(), RegistryServiceTest.class)
                                                 .orElseThrow(IllegalStateException::new); // should be there

        String registryUrl = TestUtils.getRegistryUrl(rst);

        ExtensionContext.Store store = context.getStore(ExtensionContext.Namespace.GLOBAL);

        List<TestTemplateInvocationContext> invocationCtxts = new ArrayList<>();

        if (testRegistryClient(REGISTRY_CLIENT_CREATE)) {
            RegistryServiceWrapper plain = store.getOrComputeIfAbsent(
                    "plain_client",
                    k -> new RegistryServiceWrapper(k, REGISTRY_CLIENT_CREATE, registryUrl),
                    RegistryServiceWrapper.class
                );
            invocationCtxts.add(new RegistryServiceTestTemplateInvocationContext(plain, context.getRequiredTestMethod()));
        }

        if (testRegistryClient(REGISTRY_CLIENT_CACHED)) {
            RegistryServiceWrapper cached = store.getOrComputeIfAbsent(
                    "cached_client",
                    k -> new RegistryServiceWrapper(k, REGISTRY_CLIENT_CACHED, registryUrl),
                    RegistryServiceWrapper.class
                );
            invocationCtxts.add(new RegistryServiceTestTemplateInvocationContext(cached, context.getRequiredTestMethod()));
        }

        return invocationCtxts.stream();
    }

    private boolean testRegistryClient(String clientType) {
        String testRegistryClients = TestUtils.getTestRegistryClients();
        return testRegistryClients == null || testRegistryClients.equalsIgnoreCase(REGISTRY_CLIENT_ALL)
                || testRegistryClients.equalsIgnoreCase(clientType);
    }

    private static boolean isTestAllClientTypes() {
        String testRegistryClients = TestUtils.getTestRegistryClients();
        return testRegistryClients == null || testRegistryClients.equalsIgnoreCase(REGISTRY_CLIENT_ALL);
    }

    private static class RegistryServiceWrapper implements ExtensionContext.Store.CloseableResource {
        private String key;
        private String method;
        private String registryUrl;
        private volatile AutoCloseable service;

        public RegistryServiceWrapper(String key, String method, String registryUrl) {
            this.key = key;
            this.method = method;
            this.registryUrl = registryUrl;
        }

        @Override
        public void close() throws Throwable {
            IoUtil.close(service);
        }
    }

    private static class RegistryServiceTestTemplateInvocationContext implements TestTemplateInvocationContext, ParameterResolver {
        private RegistryServiceWrapper wrapper;
        private Method testMethod;

        public RegistryServiceTestTemplateInvocationContext(RegistryServiceWrapper wrapper, Method testMethod) {
            this.wrapper = wrapper;
            this.testMethod = testMethod;
        }

        @Override
        public String getDisplayName(int invocationIndex) {
            if (isTestAllClientTypes()) {
                return String.format("%s (%s) [%s]", testMethod.getName(), wrapper.key, invocationIndex);
            } else {
                return testMethod.getName();
            }
        }

        @Override
        public List<Extension> getAdditionalExtensions() {
            return singletonList(this);
        }

        @Override
        public boolean supportsParameter(ParameterContext pc, ExtensionContext ec) throws ParameterResolutionException {
            Parameter parameter = pc.getParameter();
            return getParameterType(parameter.getParameterizedType()) != ParameterType.UNSUPPORTED;
        }

        @Override
        public Object resolveParameter(ParameterContext pc, ExtensionContext ec) throws ParameterResolutionException {
            Parameter parameter = pc.getParameter();
            ParameterType type = getParameterType(parameter.getParameterizedType());
            switch (type) {
                case REGISTRY_SERVICE: {
                    return (wrapper.service = createRegistryService());
                }
                case SUPPLIER: {
                    return (Supplier<Object>) () -> {
                        if (wrapper.service == null) {
                            try {
                                ClassLoader tccl = Thread.currentThread().getContextClassLoader();
                                if (tccl == null || tccl == ExtensionContext.class.getClassLoader()) {
                                    wrapper.service = createRegistryService();
                                } else {
                                    Class<?> clientClass = tccl.loadClass(RegistryClient.class.getName());
                                    Method factoryMethod = clientClass.getMethod(wrapper.method, String.class);
                                    wrapper.service = (AutoCloseable) factoryMethod.invoke(null, wrapper.registryUrl);
                                }
                            } catch (Exception e) {
                                throw new IllegalStateException(e);
                            }
                        }
                        return wrapper.service;
                    };
                }
                default:
                    throw new IllegalStateException("Invalid parameter type: " + type);
            }
        }

        private RegistryService createRegistryService() {
            switch (wrapper.method) {
                case REGISTRY_CLIENT_CREATE:
                    return RegistryClient.create(wrapper.registryUrl);
                case REGISTRY_CLIENT_CACHED:
                    return RegistryClient.cached(wrapper.registryUrl);
                default:
                    throw new IllegalArgumentException("Unsupported registry client method: " + wrapper.method);
            }
        }
    }
}
