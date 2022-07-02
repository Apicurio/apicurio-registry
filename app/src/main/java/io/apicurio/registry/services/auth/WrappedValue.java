/*
 * Copyright 2022 Red Hat
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

package io.apicurio.registry.services.auth;

import java.time.Duration;
import java.time.Instant;

public class WrappedValue<V> {

    private final Duration lifetime;
    private final Instant lastUpdate;
    private final V value;

    public WrappedValue(Duration lifetime, Instant lastUpdate, V value) {
        this.lifetime = lifetime;
        this.lastUpdate = lastUpdate;
        this.value = value;
    }

    public V getValue() {
        return value;
    }

    public boolean isExpired() {
        return lastUpdate.plus(lifetime).isBefore(Instant.now());
    }
}