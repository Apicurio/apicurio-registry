package io.apicurio.registry.utils.tests;

import io.apicurio.registry.utils.IoUtil;
import io.registry.RegistryRestClient;
import io.registry.RegistryRestService;
import org.junit.jupiter.api.extension.*;
import org.junit.platform.commons.util.AnnotationUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.util.Collections.singletonList;

/**
 * @author carles Arnal <carnalca@redhat.com>
 */
public class RegistryRestServiceExtension implements TestTemplateInvocationContextProvider {

    private static final String REGISTRY_CLIENT_CREATE = "create";
    private static final String REGISTRY_CLIENT_ALL = "all";

    private enum ParameterType {
        REGISTRY_SERVICE,
        SUPPLIER,
        UNSUPPORTED
    }

    private static ParameterType getParameterType(Type type) {
        if (type instanceof Class) {
            if (type == RegistryRestService.class) {
                return ParameterType.REGISTRY_SERVICE;
            }
        } else if (type instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) type;
            Type rawType = pt.getRawType();
            if (rawType == RegistryRestService.class) {
                return ParameterType.REGISTRY_SERVICE;
            } else if (rawType == Supplier.class) {
                Type[] arguments = pt.getActualTypeArguments();
                if (arguments[0] == RegistryRestService.class) {
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
        AnnotationUtils.findAnnotation(context.getRequiredTestMethod(), RegistryRestServiceTest.class)
                .orElseThrow(IllegalStateException::new); // should be there

        String registryUrl = TestUtils.getRegistryApiUrl();

        ExtensionContext.Store store = context.getStore(ExtensionContext.Namespace.GLOBAL);

        List<TestTemplateInvocationContext> invocationCtxts = new ArrayList<>();

        if (testRegistryClient(REGISTRY_CLIENT_CREATE)) {
            RegistryRestServiceWrapper plain = store.getOrComputeIfAbsent(
                    "plain_client",
                    k -> new RegistryRestServiceWrapper(k, REGISTRY_CLIENT_CREATE, registryUrl),
                    RegistryRestServiceWrapper.class
            );
            invocationCtxts.add(new RegistryServiceTestTemplateInvocationContext(plain, context.getRequiredTestMethod()));
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

    private static class RegistryRestServiceWrapper implements ExtensionContext.Store.CloseableResource {
        private final String key;
        private final String method;
        private final String registryUrl;
        private volatile AutoCloseable service;

        public RegistryRestServiceWrapper(String key, String method, String registryUrl) {
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
        private final RegistryRestServiceWrapper wrapper;
        private final Method testMethod;

        public RegistryServiceTestTemplateInvocationContext(RegistryRestServiceWrapper wrapper, Method testMethod) {
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
                                    Class<?> clientClass = tccl.loadClass(RegistryRestClient.class.getName());
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

        private RegistryRestService createRegistryService() {
            if (REGISTRY_CLIENT_CREATE.equals(wrapper.method)) {
                return RegistryRestClient.create(wrapper.registryUrl);
            }
            throw new IllegalArgumentException("Unsupported registry client method: " + wrapper.method);
        }
    }
}
