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

package io.apicurio.registry.serde.avro;

import org.apache.avro.Schema;

import io.apicurio.registry.serde.SchemaParser;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.IoUtil;

/**
 * @author Fabian Martinez
 */
public class AvroSchemaParser implements SchemaParser<Schema> {

    /**
     * @see io.apicurio.registry.serde.SchemaParser#artifactType()
     */
    @Override
    public ArtifactType artifactType() {
        return ArtifactType.AVRO;
    }

    /**
     * @see io.apicurio.registry.serde.SchemaParser#parseSchema(byte[])
     */
    @Override
    public Schema parseSchema(byte[] rawSchema) {
        return AvroSchemaUtils.parse(IoUtil.toString(rawSchema));
    }

}
