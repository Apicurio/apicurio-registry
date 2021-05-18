/*
 * Copyright 2021 Red Hat
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

package io.apicurio.registry.mt;

import java.util.Map;

import org.eclipse.microprofile.context.spi.ThreadContextProvider;
import org.eclipse.microprofile.context.spi.ThreadContextSnapshot;

/**
 * Implementation required for context propagation.
 * This is responsible for moving our tenant context from one thread to another when context-propagation is being used.
 *
 * @author Fabian Martinez
 */
public class TenantThreadContextProvider implements ThreadContextProvider {

    @Override
    public ThreadContextSnapshot currentContext(Map<String, String> props) {
        RegistryTenantContext captured = TenantContextImpl.current();
        return () -> {
            RegistryTenantContext current = restore(captured);
            return () -> restore(current);
        };
    }

    @Override
    public ThreadContextSnapshot clearedContext(Map<String, String> props) {
        return () -> {
            RegistryTenantContext current = restore(null);
            return () -> restore(current);
        };
    }

    @Override
    public String getThreadContextType() {
        return "Apicurio Registry Tenant";
    }

    private RegistryTenantContext restore(RegistryTenantContext context) {
        RegistryTenantContext currentContext = TenantContextImpl.current();
        if (context == null) {
            TenantContextImpl.clearCurrentContext();
        } else {
            TenantContextImpl.setCurrentContext(context);
        }
        return currentContext;
    }
}
