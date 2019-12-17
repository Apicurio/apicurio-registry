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

package io.apicurio.registry.connector;

import io.apicurio.registry.utils.PropertiesUtil;
import io.apicurio.registry.utils.RegistryProperties;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;

import java.util.Properties;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.InjectionPoint;

/**
 * @author Ales Justin
 */
@ApplicationScoped
public class ConnectorConfiguration {

    @Produces
    public Properties properties(InjectionPoint ip) {
        RegistryProperties kp = ip.getAnnotated().getAnnotation(RegistryProperties.class);
        return PropertiesUtil.properties(kp);
    }

    public void init(@Observes StartupEvent event, ConnectorApplication app) {
        app.start();
    }

    public void destroy(@Observes ShutdownEvent event, ConnectorApplication app) {
        app.stop();
    }
}
