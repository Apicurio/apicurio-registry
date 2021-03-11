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

package io.apicurio.registry.logging;

import java.util.HashMap;
import java.util.Map;

import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.apicurio.registry.rest.RegistryApplication;

/**
 * @author eric.wittmann@gmail.com
 */
@Interceptor
@Logged
public class LoggingInterceptor {

    private static final Map<Class<?>, Logger> loggers = new HashMap<>();

    @AroundInvoke
    public Object logMethodEntry(InvocationContext context) throws Exception {
        Logger logger = null;
        try {
            Class<?> targetClass = RegistryApplication.class;
            Object target = context.getTarget();
            if (target != null) {
                targetClass = target.getClass();
            }

            logger = getLogger(targetClass);
        } catch (Throwable t) {
        }

        logEnter(context, logger);
        Object rval = context.proceed();
        logLeave(context, logger);
        return rval;
    }

    private void logEnter(InvocationContext context, Logger logger) {
        if (context != null && context.getMethod() != null && context.getMethod().getName() != null && context.getParameters() != null && logger != null) {
            logger.trace("ENTERING method [{}] with {} parameters", context.getMethod().getName(), context.getParameters().length);
        }
    }

    private void logLeave(InvocationContext context, Logger logger) {
        if (context != null && context.getMethod() != null && context.getMethod().getName() != null && context.getParameters() != null && logger != null) {
            logger.trace("LEAVING method [{}]", context.getMethod().getName());
        }
    }

    /**
     * Gets a logger for the given target class.
     * @param targetClass
     */
    private Logger getLogger(Class<?> targetClass) {
        return loggers.computeIfAbsent(targetClass, k -> {
            return LoggerFactory.getLogger(targetClass);
        });
    }

}
