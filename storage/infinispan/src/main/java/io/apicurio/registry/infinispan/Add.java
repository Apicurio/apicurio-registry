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

package io.apicurio.registry.infinispan;

import org.infinispan.util.function.SerializableBiFunction;

/**
 * Add function.
 *
 * @author Ales Justin
 */
public class Add implements SerializableBiFunction<String, Long, Long> {

    private static final long serialVersionUID = 1L;

    @Override
    public Long apply(String k, Long v) {
        return v == null ? 1L : v + 1L;
    }
}
