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

package io.apicurio.multitenant.logging;

import java.util.HashMap;
import java.util.Map;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.InjectionPoint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
public class LoggerProducer {

    private final Map<Class<?>, Logger> loggers = new HashMap<>();

    /**
     * @param targetClass
     */
    public Logger getLogger(Class<?> targetClass) {
        Logger logger = loggers.computeIfAbsent(targetClass, k -> {
            return LoggerFactory.getLogger(targetClass);
        });
        return logger;
    }

    /**
     * Produces a logger for injection.
     * @param injectionPoint
     */
    @Produces
    public Logger produceLogger(InjectionPoint injectionPoint) {
        Class<?> targetClass = injectionPoint.getBean().getBeanClass();
        return getLogger(targetClass);
    }

}
