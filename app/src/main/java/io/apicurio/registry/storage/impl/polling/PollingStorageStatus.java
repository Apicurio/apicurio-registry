/*
 * Copyright 2025 Red Hat
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

package io.apicurio.registry.storage.impl.polling;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Tracks the current synchronization status of a polling-based storage.
 * Updated after each poll/load cycle to reflect the latest state.
 */
@Getter
@Builder(toBuilder = true)
public class PollingStorageStatus {

    public enum SyncState {
        /** Storage has not yet completed its first data load. */
        INITIALIZING,
        /** Storage is idle, serving the latest successfully loaded data. */
        IDLE,
        /** A poll detected changes and the storage is loading new data. */
        LOADING,
        /** Data has been loaded and is waiting for the active/inactive switch.
         *  This state indicates the write lock could not be acquired immediately,
         *  typically because read operations are in progress. */
        SWITCHING,
        /** The last load or switch attempt failed;
         *  storage continues serving previous data. */
        ERROR
    }

    /** Current synchronization state. */
    private final SyncState syncState;

    /** When the last successful sync completed. */
    private final Instant lastSuccessfulSync;

    /** When the last sync attempt occurred (successful or not). */
    private final Instant lastSyncAttempt;

    /** Number of groups loaded in the last successful sync. */
    private final int groupCount;

    /** Number of artifacts loaded in the last successful sync. */
    private final int artifactCount;

    /** Number of versions loaded in the last successful sync. */
    private final int versionCount;

    /** Errors from the last failed load attempt,
     *  empty if last load was successful. */
    private final List<PollingError> errors;

    /** Per-source identifiers (source ID -> short marker string, e.g., abbreviated commit SHA). */
    private final Map<String, String> sources;

    /**
     * Creates an initial status in the INITIALIZING state.
     * @return the initial status
     */
    public static PollingStorageStatus initializing() {
        return PollingStorageStatus.builder()
                .syncState(SyncState.INITIALIZING)
                .errors(List.of())
                .sources(Map.of())
                .build();
    }
}
