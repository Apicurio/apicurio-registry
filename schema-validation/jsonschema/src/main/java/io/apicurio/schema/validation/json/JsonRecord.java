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

package io.apicurio.schema.validation.json;

import io.apicurio.registry.resolver.data.Record;

/**
 * @author Fabian Martinez
 */
public class JsonRecord implements Record<Object> {

    private final Object payload;
    private final JsonMetadata metadata;

    public JsonRecord(Object payload, JsonMetadata metadata) {
        this.payload = payload;
        this.metadata = metadata;
    }

    @Override
    public JsonMetadata metadata() {
        return this.metadata;
    }

    @Override
    public Object payload() {
        return this.payload;
    }

}
