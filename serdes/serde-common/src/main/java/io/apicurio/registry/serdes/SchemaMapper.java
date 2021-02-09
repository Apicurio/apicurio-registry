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

package io.apicurio.registry.serdes;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.IoUtil;

/**
 * @author Fabian Martinez
 */
public interface SchemaMapper<S, D> {

    public ArtifactType artifactType();

    public S schemaFromData(D data);

    public S parseSchema(InputStream rawSchema);

    /**
     * Create InputStream from schema.
     * By default we just take string bytes.
     *
     * @param schema the schema
     * @return schema's input stream
     */
    default InputStream toRawSchema(S schema) {
        if (schema instanceof byte[]) {
            return new ByteArrayInputStream((byte[]) schema);
        } else if (schema instanceof InputStream) {
            return (InputStream) schema;
        } else {
            // TODO Calling "toString()" here will work for Avro, but is unlikely to work for other schemas
            return new ByteArrayInputStream(IoUtil.toBytes(schema.toString()));
        }
    }

}
