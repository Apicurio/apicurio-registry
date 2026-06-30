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

package io.apicurio.registry.serde.tracing;

import io.opentelemetry.api.common.AttributeKey;

import static io.opentelemetry.api.common.AttributeKey.booleanKey;
import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;

public final class SerDesAttributes {

    public static final AttributeKey<String> TOPIC = stringKey("messaging.destination.name");
    public static final AttributeKey<String> OPERATION = stringKey("apicurio.registry.serde.operation");
    public static final AttributeKey<Long> DATA_SIZE = longKey("apicurio.registry.serde.data_size");
    public static final AttributeKey<Boolean> CACHE_HIT = booleanKey("apicurio.registry.serde.cache_hit");

    private SerDesAttributes() {
    }
}
