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

package io.apicurio.registry.storage.impl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import javax.enterprise.context.ApplicationScoped;

/**
 * @author Ales Justin
 */
@ApplicationScoped
public class InMemoryRegistryStorage extends AbstractMapRegistryStorage {

    private AtomicLong counter = new AtomicLong(1);
    
    @Override
    protected long nextGlobalId() {
        return counter.getAndIncrement();
    }

    @Override
    protected Map<String, Map<Long, Map<String, String>>> createStorageMap() {
        return new ConcurrentHashMap<>();
    }

    @Override
    protected Map<Long, Map<String, String>> createGlobalMap() {
        return new ConcurrentHashMap<>();
    }

    @Override
    protected Map<String, String> createGlobalRulesMap() {
        return new ConcurrentHashMap<>();
    }
    
    @Override
    protected Map<String, Map<String, String>> createArtifactRulesMap() {
        return new ConcurrentHashMap<>();
    }
}
