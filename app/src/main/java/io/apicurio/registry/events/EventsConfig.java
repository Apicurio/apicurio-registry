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

package io.apicurio.registry.events;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;

import io.apicurio.registry.config.RegistryConfigProperty;
import io.apicurio.registry.config.RegistryConfigService;

/**
 * @author eric.wittmann@gmail.com
 */
@Singleton
public class EventsConfig {

    @Inject
    Logger log;

    @Inject
    RegistryConfigService configService;

    @PostConstruct
    void onConstruct() {
    }

    public String getTopic() {
        return configService.get(RegistryConfigProperty.REGISTRY_EVENTS_KAFKA_TOPIC);
    }

    public Integer getTopicPartition() {
        return configService.get(RegistryConfigProperty.REGISTRY_EVENTS_KAFKA_TOPIC_PARTITION, Integer.class);
    }

    public boolean hasTopic() {
        return this.getTopic() != null;
    }

}
