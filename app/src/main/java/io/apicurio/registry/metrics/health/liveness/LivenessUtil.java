/*
 * Copyright 2020 Red Hat Inc
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

package io.apicurio.registry.metrics.health.liveness;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.services.http.RegistryExceptionMapperService;

/**
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
public class LivenessUtil {

    @Inject
    Logger log;

    @Inject
    @ConfigProperty(name = "registry.liveness.errors.ignored")
    @Info(category = "health", description = "Ignored liveness errors", availableSince = "1.2.3.Final")
    Optional<List<String>> ignored;

    public boolean isIgnoreError(Throwable ex) {
        boolean ignored = this.isIgnored(ex);
        if (ignored) {
            log.debug("Ignored intercepted exception: " + ex.getClass().getName() + " :: " + ex.getMessage());
        }
        return ignored;
    }

    private boolean isIgnored(Throwable ex) {
        Set<Class<? extends Exception>> ignoredClasses = RegistryExceptionMapperService.getIgnored();
        if (ignoredClasses.contains(ex.getClass())) {
            return true;
        }
        return this.ignored.isPresent() && this.ignored.get().contains(ex.getClass().getName());
    }

}
