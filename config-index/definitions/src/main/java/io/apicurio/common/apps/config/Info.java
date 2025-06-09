/*
 * Copyright 2022 Red Hat
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

package io.apicurio.common.apps.config;

import jakarta.enterprise.util.Nonbinding;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Retention(RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface Info {

    @Nonbinding
    ConfigPropertyCategory category();

    @Nonbinding
    String description() default "";

    @Nonbinding
    String availableSince() default "";

    @Nonbinding
    String registryAvailableSince() default "";

    @Nonbinding
    String studioAvailableSince() default "";

    /**
     * Lists related configuration properties. TODO: Not used in docs yet
     */
    @Nonbinding
    String[] seeAlso() default {};

    /**
     * Lists configuration properties that must be configured before using this property. TODO: Not used in
     * docs yet
     */
    @Nonbinding
    String[] dependsOn() default {};
}
