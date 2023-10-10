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

package io.apicurio.registry.downloads;

import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.types.Current;
import io.quarkus.scheduler.Scheduled;
import org.slf4j.Logger;

import java.time.Instant;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import static io.quarkus.scheduler.Scheduled.ConcurrentExecution.SKIP;

/**
 * Periodically cleanup data of tenants marked as deleted.
 *
 * @author Jakub Senko <em>m@jsenko.net</em>
 */
@ApplicationScoped
public class DownloadReaper {

    @Inject
    Logger log;

    @Inject
    @Current
    RegistryStorage storage;

    /**
     * Minimal granularity is 1 minute.
     */
    @Scheduled(delay = 2, concurrentExecution = SKIP, every = "{registry.downloads.reaper.every}")
    void run() {
        try {
            if(storage.isReady()) {
                log.debug("Running download reaper job at {}", Instant.now());
                reap();
            } else {
                log.warn("Storage is not alive. Skipping download reaper job for now.");
            }
        } catch (Exception ex) {
            log.error("Exception thrown when running download reaper job", ex);
        }
    }

    /**
     * Delete any rows in the "downloads" table that represent downloads that have expired.
     */
    void reap() {
        storage.deleteAllExpiredDownloads();
    }
}
