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

package io.apicurio.common.apps.logging;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.InjectionPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
public class LoggerProducer {

    private final Map<Class<?>, Logger> loggers = new ConcurrentHashMap<>();

    /**
     * @param targetClass the target class of the logger
     * @return a logger
     */
    public Logger getLogger(Class<?> targetClass) {
        Logger logger = loggers.computeIfAbsent(targetClass, k -> {
            return LoggerFactory.getLogger(targetClass);
        });
        return logger;
    }

    /**
     * Produces a logger for injection.
     * 
     * @param injectionPoint the logger injection point (typically a field)
     * @return a logger
     */
    @Produces
    public Logger produceLogger(InjectionPoint injectionPoint) {
        Class<?> targetClass = injectionPoint.getBean().getBeanClass();
        return getLogger(targetClass);
    }

}
