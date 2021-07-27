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

package io.apicurio.registry.mt;

import io.quarkus.scheduler.Scheduled;
import org.slf4j.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Make sure that the tenant reaper is run more rapidly during testing.
 *
 * @author Jakub Senko <jsenko@redhat.com>
 */
@ApplicationScoped
public class TenantReaperTestHelper {

    @Inject
    Logger log;

    @Inject
    TenantReaper reaper;

    @Inject
    TenantContextLoader tcl;

    @Inject
    MultitenancyProperties properties;

    @Scheduled(every = "5s")
    void run() {
        if (properties.isReaperTestMode()) {
            log.debug("Running tenant reaper in test mode");
            // Ensure execution, remove stagger
            reaper.setNext(Instant.now().minusSeconds(1));
            reaper.run();
            // Prevent execution
            reaper.setNext(Instant.now().plus(1, ChronoUnit.DAYS));
            tcl.invalidateTenantCache();
        }
    }
}
