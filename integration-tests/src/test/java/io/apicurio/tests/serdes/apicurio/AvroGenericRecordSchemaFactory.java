package io.apicurio.tests.serdes.apicurio;

import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.tests.TestUtils;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;

import java.io.InputStream;
import java.util.List;
import java.util.Objects;

import static org.junit.Assert.assertTrue;


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
