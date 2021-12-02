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

package io.apicurio.registry.mt.logging;

import java.util.Map;

import org.eclipse.microprofile.context.spi.ThreadContextProvider;
import org.eclipse.microprofile.context.spi.ThreadContextSnapshot;
import org.slf4j.MDC;


/**
 * Implementation required for context propagation.
 * This is responsible for moving the MDC configuration from one thread to another when context-propagation is being used.
 * This is important for having correct log output when multitenancy is enabled
 *
 * @author Fabian Martinez
 */
public class MdcContextProvider implements ThreadContextProvider {

    @Override
    public ThreadContextSnapshot currentContext(Map<String, String> props) {
        Map<String, String> propagate = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> old = MDC.getCopyOfContextMap();
            MDC.setContextMap(propagate);
            return () -> {
                MDC.setContextMap(old);
            };
        };
    }

    @Override
    public ThreadContextSnapshot clearedContext(Map<String, String> props) {
        return () -> {
            Map<String, String> old = MDC.getCopyOfContextMap();
            MDC.clear();
            return () -> {
                MDC.setContextMap(old);
            };
        };
    }

    @Override
    public String getThreadContextType() {
        return "SLF4J MDC";
    }
}
