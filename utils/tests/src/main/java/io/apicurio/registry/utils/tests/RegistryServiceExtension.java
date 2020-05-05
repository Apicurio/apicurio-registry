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
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;
import org.junit.platform.commons.util.AnnotationUtils;

import static java.util.Collections.singletonList;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * @author Ales Justin
 */
public class RegistryServiceExtension implements TestTemplateInvocationContextProvider {

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
        RegistryServiceWrapper plain = store.getOrComputeIfAbsent(
            "plain_client",
            k -> new RegistryServiceWrapper(k, "create", registryUrl),
            RegistryServiceWrapper.class
        );
        RegistryServiceWrapper cached = store.getOrComputeIfAbsent(
            "cached_client",
            k -> new RegistryServiceWrapper(k, "cached", registryUrl),
            RegistryServiceWrapper.class
        );

        return Stream.of(
            new RegistryServiceTestTemplateInvocationContext(plain),
            new RegistryServiceTestTemplateInvocationContext(cached)
        );
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
            service.close();
        }
    }

    private static class RegistryServiceTestTemplateInvocationContext implements TestTemplateInvocationContext, ParameterResolver {
        private RegistryServiceWrapper wrapper;

        public RegistryServiceTestTemplateInvocationContext(RegistryServiceWrapper wrapper) {
            this.wrapper = wrapper;
        }

        @Override
        public String getDisplayName(int invocationIndex) {
            return String.format("%s [%s]", wrapper.key, invocationIndex);
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
                case "create":
                    return RegistryClient.create(wrapper.registryUrl);
                case "cached":
                    return RegistryClient.cached(wrapper.registryUrl);
                default:
                    throw new IllegalArgumentException("Unsupported registry client method: " + wrapper.method);
            }
        }
    }
}
