/*
 * Copyright 2023 Red Hat
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
package io.apicurio.registry.resolver;

import io.apicurio.registry.resolver.data.Metadata;
import io.apicurio.registry.resolver.data.Record;
import io.apicurio.registry.resolver.strategy.ArtifactReference;

public class MockRecord<T> implements Record<T> {

    private T payload;
    private ArtifactReference reference;

    public MockRecord(T payload, ArtifactReference reference) {
        this.payload = payload;
        this.reference = reference;
    }

    @Override
    public Metadata metadata() {
        return new Metadata() {
            @Override
            public ArtifactReference artifactReference() {
                return reference;
            }
        };
    }

    @Override
    public T payload() {
        return payload;
    }
}
