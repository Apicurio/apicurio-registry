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
package io.apicurio.registry.events;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.events.dto.RegistryEventType;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.slf4j.Logger;

import jakarta.annotation.PostConstruct;
import java.util.Optional;

/**
 * @author Fabian Martinez
 */
@ApplicationScoped
public class EventsServiceImpl implements EventsService {

    private static final String INTERNAL_EVENTS_ADDRESS = "registry-events";

    private ObjectMapper mapper;
    private boolean initDone = false;
    private boolean configuredSinks = false;

    @Inject
    Logger log;

    @Inject
    Vertx vertx;

    @Inject
    EventBus eventBus;

    @Inject
    Instance<EventSink> sinks;

    @PostConstruct
    public void init() {
        for (EventSink sink : sinks) {
            if (sink.isConfigured()) {
                log.info("Subscribing sink " + sink.name());
                eventBus.consumer(INTERNAL_EVENTS_ADDRESS, sink::handle);
                configuredSinks = true;
            }
        }
        initDone = true;
    }

    @Override
    public boolean isReady() {
        return initDone;
    }

    @Override
    public boolean isConfigured() {
        return configuredSinks;
    }

    @Override
    public void triggerEvent(RegistryEventType type, Optional<String> artifactId, Object data) {
        if (configuredSinks && data != null) {
            Buffer buffer;
            try {
                buffer = Buffer.buffer(getMapper().writeValueAsBytes(data));
            } catch (JsonProcessingException e) {
                log.error("Error serializing event data", e);
                return;
            }
            DeliveryOptions opts = new DeliveryOptions()
                    .addHeader("type", type.cloudEventType());
            if (artifactId.isPresent()) {
                opts.addHeader("artifactId", artifactId.get());
            }
            eventBus.publish(INTERNAL_EVENTS_ADDRESS, buffer, opts);
        }
    }

    private synchronized ObjectMapper getMapper() {
        if (mapper == null) {
            mapper = new ObjectMapper();
            mapper.setSerializationInclusion(Include.NON_NULL);
        }
        return mapper;
    }

}