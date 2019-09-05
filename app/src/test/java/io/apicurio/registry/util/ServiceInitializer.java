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

package io.apicurio.registry.util;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Destroyed;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;

/**
 * Setup storage type's (JPA, Kafka, ...) services.
 * e.g. database (JPA) or (ZK and Kafka)
 * <p>
 * Or simply check if service is already running.
 *
 * @author Ales Justin
 */
public interface ServiceInitializer {
    default void beforeAll(@Observes @Initialized(ApplicationScoped.class) Object event) throws Exception {}
    default void afterAll(@Observes @Destroyed(ApplicationScoped.class) Object event) {}
}
