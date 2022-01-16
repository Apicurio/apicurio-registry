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

package io.apicurio.registry.serde.data;

import io.apicurio.registry.resolver.data.Record;

/**
 * @author Fabian Martinez
 */
public class KafkaSerdesRecord<T> implements Record<T> {

    private KafkaSerdesMetadata metadata;
    private T payload;

    public KafkaSerdesRecord(KafkaSerdesMetadata metadata, T payload) {
        this.metadata = metadata;
        this.payload = payload;
    }

    /**
     * @see io.apicurio.registry.resolver.data.Record#metadata()
     */
    @Override
    public KafkaSerdesMetadata metadata() {
        return metadata;
    }

    /**
     * @see io.apicurio.registry.resolver.data.Record#payload()
     */
    @Override
    public T payload() {
        // TODO Auto-generated method stub
        return payload;
    }

}
