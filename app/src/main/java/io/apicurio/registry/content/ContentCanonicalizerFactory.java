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

package io.apicurio.registry.content;

import javax.enterprise.context.ApplicationScoped;

import io.apicurio.registry.content.canon.AvroContentCanonicalizer;
import io.apicurio.registry.content.canon.JsonContentCanonicalizer;
import io.apicurio.registry.content.canon.NoOpContentCanonicalizer;
import io.apicurio.registry.types.ArtifactType;

/**
 * Factory for creating canonicalizers.
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
public class ContentCanonicalizerFactory {

    private ContentCanonicalizer avro = new AvroContentCanonicalizer();
    private ContentCanonicalizer noop = new NoOpContentCanonicalizer();
    private ContentCanonicalizer json = new JsonContentCanonicalizer();
    
    /**
     * Creates a canonicalizer for a given artifact type.
     * @param type
     */
    public ContentCanonicalizer create(ArtifactType type) {
        switch (type) {
            case ASYNCAPI:
                return json;
            case AVRO:
                return avro;
            case JSON:
                return json;
            case OPENAPI:
                return json;
            case PROTOBUF:
                return noop;
            case PROTOBUF_FD:
                return noop;
            default:
                break;
        }
        throw new RuntimeException("No content canonicalizer found for: " + type);
    }
}
