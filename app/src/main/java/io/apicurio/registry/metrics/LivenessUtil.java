/*
 * Copyright 2020 Red Hat Inc
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

package io.apicurio.registry.metrics;

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.apicurio.registry.rest.RegistryExceptionMapper;

/**
 * @author eric.wittmann@gmail.com
 */
public class LivenessUtil {

    private static final Logger log = LoggerFactory.getLogger(PersistenceExceptionLivenessInterceptor.class);

    private static final Set<String> IGNORED_CLASSES = new HashSet<>();
    static {
        IGNORED_CLASSES.add("io.grpc.StatusRuntimeException");
        IGNORED_CLASSES.add("org.apache.kafka.streams.errors.InvalidStateStoreException");
    }


    public static boolean isIgnoreError(Throwable ex) {
        boolean ignored = false;
        
        if (ex instanceof LivenessIgnoredException) {
            ignored = true;
        }
        
        Set<Class<? extends Exception>> ignoredClasses = RegistryExceptionMapper.getIgnored();
        if (ignoredClasses.contains(ex.getClass())) {
            ignored = true;
        }
            
        if (IGNORED_CLASSES.contains(ex.getClass().getName())) {
            ignored = true;
        }
        
        if (ignored) {
            log.debug("Ignored intercepted exception: " + ex.getClass().getName() + " :: " + ex.getMessage());
        }
        return ignored;
    }

}
