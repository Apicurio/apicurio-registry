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

package io.apicurio.registry.streams.topology.processor;

import io.apicurio.registry.storage.proto.Str;
import org.apache.kafka.streams.processor.AbstractProcessor;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueStore;

public class ContentIdProcessor extends AbstractProcessor<Long, Str.ContentValue> {
    private final String storeName;
    private KeyValueStore<Long, Str.ContentValue> store;

    public ContentIdProcessor(String storeName) {
        this.storeName = storeName;
    }

    @Override
    public void init(ProcessorContext context) {
        super.init(context);
        //noinspection unchecked
        store = (KeyValueStore<Long, Str.ContentValue>) context.getStateStore(storeName);
    }

    @Override
    public void process(Long key, Str.ContentValue value) {
        if (value != null) {
            store.put(key, value);
        } else {
            //Do nothing, delete not supported for contents for now
        }
    }
}
