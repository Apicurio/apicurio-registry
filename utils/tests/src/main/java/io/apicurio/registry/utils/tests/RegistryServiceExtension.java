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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * @author Ales Justin
 */
public class RegistryServiceExtension implements TestTemplateInvocationContextProvider {
    @Override
    public boolean supportsTestTemplate(ExtensionContext context) {
        return context.getTestMethod().map(method -> {
            Class<?>[] parameterTypes = method.getParameterTypes();
            return Arrays.asList(parameterTypes).contains(RegistryService.class);
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
            k -> new RegistryServiceWrapper(k, RegistryClient.create(registryUrl)),
            RegistryServiceWrapper.class
        );
        RegistryServiceWrapper cached = store.getOrComputeIfAbsent(
            "cached_client",
            k -> new RegistryServiceWrapper(k, RegistryClient.cached(registryUrl)),
            RegistryServiceWrapper.class
        );

        return Stream.of(
            new RegistryServiceTestTemplateInvocationContext(plain),
            new RegistryServiceTestTemplateInvocationContext(cached)
        );
    }

    private static class RegistryServiceWrapper implements ExtensionContext.Store.CloseableResource {
        private String key;
        private RegistryService service;

        public RegistryServiceWrapper(String key, RegistryService service) {
            this.key = key;
            this.service = service;
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
        public boolean supportsParameter(ParameterContext pc, ExtensionContext extensionContext) throws ParameterResolutionException {
            return (pc.getParameter().getType() == RegistryService.class);
        }

        @Override
        public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
            return wrapper.service;
        }
    }
}
