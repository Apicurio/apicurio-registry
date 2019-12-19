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

package io.apicurio.registry.search.client.kafka;

import io.apicurio.registry.search.client.SearchResponse;
import org.apache.kafka.clients.producer.RecordMetadata;

/**
 * @author Ales Justin
 */
public class KafkaSearchResponse implements SearchResponse {
    private RecordMetadata rmd;

    public KafkaSearchResponse(RecordMetadata rmd) {
        this.rmd = rmd;
    }

    @Override
    public boolean ok() {
        return (rmd != null);
    }

    @Override
    public int status() {
        return (int) rmd.offset();
    }
}
