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

package io.apicurio.multitenant.logging.sentry;

import java.util.logging.LogManager;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.apicurio.multitenant.api.TenantManagerSystem;
import io.quarkus.runtime.StartupEvent;
import io.sentry.Sentry;
import io.sentry.jul.SentryHandler;

/**
 * @author Fabian Martinez
 */
@ApplicationScoped
public class SentryConfiguration {

    Logger log = LoggerFactory.getLogger(this.getClass());

    @ConfigProperty(name = "tenant-manager.enable.sentry", defaultValue = "false")
    Boolean enableSentry;

    @Inject
    TenantManagerSystem system;

    void onStart(@Observes StartupEvent ev) throws Exception {
        if (enableSentry) {
            java.lang.System.setProperty("sentry.release", system.getVersion());
            //Sentry will pick it's configuration from env variables
            Sentry.init();
            LogManager manager = org.jboss.logmanager.LogManager.getLogManager();
            manager.getLogger("").addHandler(new SentryHandler());
            log.info("Sentry initialized");
        }
    }

}
