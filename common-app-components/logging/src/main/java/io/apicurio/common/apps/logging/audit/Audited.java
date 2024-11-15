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

package io.apicurio.common.apps.logging.audit;

import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * This annotation is processed by {@link AuditedInterceptor}
 */
@InterceptorBinding
@Retention(RUNTIME)
@Target({ METHOD, TYPE })
public @interface Audited {

    /**
     * If empty or null the method name will be used as the action identifier
     * 
     * @return the action identifier
     */
    String action() default "";

    /**
     * If a method parameter value should be recorded to the auditing log, but there is no extractor defined
     * (e.g. the value is a type without specific meaning, such as a String), this field can be used by adding
     * two successive values: 1. Position of the given parameter, starting at 0, as String. Parameter name is
     * not used, in case it is not available via reflection. 2. Key under which the value of the parameter
     * should be recorded. There can be more than one such pair. Note that the position can also optionally
     * include an additional property name. For example you could indicate "3.title" as the position. This
     * will get the third parameter from the context and then look for a JavaBean property named "title". If
     * one exists, it will be logged. This allows extraction of properties of complex object parameters to be
     * added to the audit log. So, for example, if the 2nd parameter of a method is type "MyWidget", and the
     * "MyWidget" class has a "description" property, then you could specific "1.description" as the position.
     *
     * @return the array of parameters to extract
     */
    @Nonbinding
    String[] extractParameters() default {};

}
