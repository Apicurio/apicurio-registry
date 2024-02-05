/*
 * Copyright 2024 Red Hat
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

package io.apicurio.registry.storage.impl.kafkasql.upgrade;

import io.apicurio.registry.storage.impl.kafkasql.upgrade.KafkaSqlUpgraderManager.UpgraderManagerHandle;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.Random;

import static io.apicurio.registry.storage.impl.kafkasql.upgrade.KafkaSqlUpgraderManager.scale;

@ApplicationScoped
public class KafkaSqlTestUpgrader implements KafkaSqlUpgrader {

    private static final Random RANDOM = new Random();

    @ConfigProperty(name = "registry.kafkasql.upgrade-test-mode", defaultValue = "false") // Keep undocumented!
    boolean testMode;

    @ConfigProperty(name = "registry.kafkasql.upgrade-test-fail", defaultValue = "false") // Keep undocumented!
    boolean testModeFail;

    @ConfigProperty(name = "registry.kafkasql.upgrade-test-delay", defaultValue = "0ms") // Keep undocumented!
    Duration testModeDelay;

    @Inject
    Logger log;


    @Override
    public boolean supportsVersion(int currentVersion) {
        if (testMode) {
            log.warn("RUNNING IN TEST MODE");
        }
        return testMode;
    }


    @Override
    public void upgrade(UpgraderManagerHandle handle) throws InterruptedException {

        if (testModeFail) {
            throw new RuntimeException("Simulating a failed upgrader.");
        }

        if (testModeDelay.toMillis() > 0) {
            log.debug("Simulating configured delay {} ms.", testModeDelay.toMillis());
            Thread.sleep(testModeDelay.toMillis());
        } else {
            // Simulate Acceptable delay, about 1.125*LOCK_TIMEOUT=11s total
            for (int i = 0; i < 3; i++) {
                // This delay is within expected bounds
                // TODO: JDK 11 does not support nextLong(long, long).
                var delay = random(scale(handle.getLockTimeout(), 0.5f).toMillis(), scale(handle.getLockTimeout(), 0.8f).toMillis());
                log.debug("Delaying for {} ms.", delay);
                Thread.sleep(delay);
                log.debug("handle.heartbeat()");
                handle.heartbeat();
            }
        }
    }


    private static long random(long origin, long bound) {
        if (bound <= origin) {
            throw new IllegalArgumentException("bound must be > origin");
        }
        var diff = bound - origin;
        var base = RANDOM.nextLong() % diff;
        return base + origin;
    }
}
