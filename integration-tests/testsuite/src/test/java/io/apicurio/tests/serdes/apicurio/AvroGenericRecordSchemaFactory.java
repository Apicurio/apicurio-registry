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

package io.apicurio.tests.serdes.apicurio;

import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;

import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.tests.TestUtils;

/**
 * @author Fabian Martinez
 */
public class AvroGenericRecordSchemaFactory {

    private String namespace;
    private String recordName;
    private List<String> schemaKeys;
    private Schema schema;

    public AvroGenericRecordSchemaFactory(String namespace, String recordName, List<String> schemaKeys) {
        Objects.requireNonNull(namespace);
        Objects.requireNonNull(recordName);
        Objects.requireNonNull(schemaKeys);
        this.namespace = namespace;
        this.recordName = recordName;
        this.schemaKeys = schemaKeys;
        assertTrue(this.schemaKeys.size() > 0);
        generateSchema();
    }

    public AvroGenericRecordSchemaFactory(String recordName, List<String> schemaKeys) {
        Objects.requireNonNull(recordName);
        Objects.requireNonNull(schemaKeys);
        this.recordName = recordName;
        this.schemaKeys = schemaKeys;
        assertTrue(this.schemaKeys.size() > 0);
        generateSchema();
    }

    public AvroGenericRecordSchemaFactory(List<String> schemaKeys) {
        Objects.requireNonNull(schemaKeys);
        this.recordName = TestUtils.generateSubject();
        this.schemaKeys = schemaKeys;
        assertTrue(this.schemaKeys.size() > 0);
        generateSchema();
    }

    public Schema generateSchema() {
        if (schema == null) {
            StringBuilder builder = new StringBuilder()
                    .append("{\"type\":\"record\"")
                    .append(",")
                    .append("\"name\":")
                    .append("\"")
                    .append(recordName)
                    .append("\"");
            if (this.namespace != null) {
                builder.append(",")
                    .append("\"namespace\":")
                    .append("\"")
                    .append(this.namespace)
                    .append("\"");
            }
            builder.append(",")
                .append("\"fields\":[");
            boolean first = true;
            for (String schemaKey : schemaKeys) {
                if (!first) {
                    builder.append(",");
                }
                builder.append("{\"name\":\"" + schemaKey + "\",\"type\":\"string\"}");
                first = false;
            }
            builder.append("]}");
            schema = new Schema.Parser().parse(builder.toString());
        }
        return schema;
    }

    public InputStream generateSchemaStream() {
        return IoUtil.toStream(generateSchema().toString());
    }

    public byte[] generateSchemaBytes() {
        return IoUtil.toBytes(generateSchema().toString());
    }

    public GenericRecord generateRecord(int count) {
        Objects.requireNonNull(schema);
        GenericRecord record = new GenericData.Record(schema);
        String message = "value-" + count;
        for (String schemaKey : schemaKeys) {
            record.put(schemaKey, message);
        }
        return record;
    }

    public boolean validateRecord(GenericRecord record) {
        return this.schema.equals(record.getSchema());
    }

}
