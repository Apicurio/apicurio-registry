/*
 * Copyright 2019 Red Hat
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

package io.apicurio.registry.client.ext;

import org.jboss.resteasy.microprofile.client.ExceptionMapping;

import java.lang.reflect.Method;
import java.util.function.BiFunction;

/**
 * RestEasy wraps CompletionStage exceptions in some weird HandlerException
 *
 * @author Ales Justin
 */
public class RestEasyExceptionUnwrapper implements BiFunction<Method, Throwable, Throwable> {
    @Override
    public Throwable apply(Method method, Throwable t) {
        if (t instanceof ExceptionMapping.HandlerException) {
            ExceptionMapping.HandlerException he = (ExceptionMapping.HandlerException) t;
            try {
                he.mapException(method);
            } catch (Exception e) {
                return e;
            }
        }
        return t;
    }
}
