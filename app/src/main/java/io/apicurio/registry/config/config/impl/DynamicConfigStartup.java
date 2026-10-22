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

import io.apicurio.common.apps.config.DynamicConfigStorageAccessor;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
@Startup
public class DynamicConfigStartup {

    private static final Logger log = LoggerFactory.getLogger(DynamicConfigStartup.class);

    @Inject
    DynamicConfigStorageAccessor configStorageAccessor;

    @Inject
    DynamicConfigPropertyIndexImpl configIndex;

    @PostConstruct
    void onStart() {
        log.debug("Initializing dynamic configuration source in thread {}", Thread.currentThread().getName());
        configStorageAccessor.getConfigStorage().isReady();
        DynamicConfigSource.setStorage(configStorageAccessor.getConfigStorage());
        DynamicConfigSource.setConfigurationIndex(configIndex);
        log.debug("Dynamic configuration source initialized in thread {}", Thread.currentThread().getName());
    }
}