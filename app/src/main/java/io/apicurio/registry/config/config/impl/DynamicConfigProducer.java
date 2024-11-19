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

package io.apicurio.registry.config.config.impl;

import io.apicurio.common.apps.config.Dynamic;
import io.smallrye.config.inject.ConfigProducer;
import io.smallrye.config.inject.ConfigProducerUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.InjectionPoint;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.function.Supplier;

/**
 * CDI producer used when injecting dynamic configuration properties into application injection points.
 * 
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
public class DynamicConfigProducer extends ConfigProducer {

    @Override
    @Dependent
    @Produces
    @ConfigProperty
    @Dynamic
    protected <T> Supplier<T> produceSupplierConfigProperty(InjectionPoint ip) {
        return () -> ConfigProducerUtil.getValue(ip, getConfig());
    }
}
