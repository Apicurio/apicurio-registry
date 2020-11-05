/*
 * Copyright 2019 JBoss Inc
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

package io.apicurio.registry.storage.util;

import io.apicurio.registry.util.ServiceInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Destroyed;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;

/**
 * @author Ales Justin
 */
public class H2DatabaseServiceInitializer implements ServiceInitializer {
    private static final Logger log = LoggerFactory.getLogger(H2DatabaseServiceInitializer.class);

    private static H2DatabaseService service; // needs to be static

    @Override
    public void beforeAll(@Observes @Initialized(ApplicationScoped.class) Object event) throws Exception {
        log.info("Starting H2 ...");
        H2DatabaseService tmp = new H2DatabaseService();
        tmp.start();
        service = tmp;
    }

    @Override
    public void afterAll(@Observes @Destroyed(ApplicationScoped.class) Object event) {
        log.info("Stopping H2 ...");
        if (service != null) {
            service.stop();
        }
    }
}
