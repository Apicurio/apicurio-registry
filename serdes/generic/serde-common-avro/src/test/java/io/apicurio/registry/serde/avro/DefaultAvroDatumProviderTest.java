package io.apicurio.registry.serde.avro;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DatumWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

public class DefaultAvroDatumProviderTest {

    private DefaultAvroDatumProvider<GenericRecord> provider;

    @BeforeEach
    public void setup() {
        provider = new DefaultAvroDatumProvider<>();
        provider.configure(new AvroSerdeConfig(Collections.emptyMap()));
    }

    @Test
    public void testReaderCacheDistinguishesSchemasDifferingOnlyInProperties() {
        Schema v1 = new Schema.Parser().parse(
                "{\"type\":\"record\",\"name\":\"Value\",\"namespace\":\"test\",\"fields\":["
                        + "{\"name\":\"type\",\"type\":{\"type\":\"string\","
                        + "\"connect.parameters\":{\"allowed\":\"station,post_office\"},"
                        + "\"connect.name\":\"io.debezium.data.Enum\"}}"
                        + "]}");

        Schema v2 = new Schema.Parser().parse(
                "{\"type\":\"record\",\"name\":\"Value\",\"namespace\":\"test\",\"fields\":["
                        + "{\"name\":\"type\",\"type\":{\"type\":\"string\","
                        + "\"connect.parameters\":{\"allowed\":\"station,post_office,plane\"},"
                        + "\"connect.name\":\"io.debezium.data.Enum\"}}"
                        + "]}");

        DatumReader<GenericRecord> reader1 = provider.createDatumReader(v1);
        DatumReader<GenericRecord> reader2 = provider.createDatumReader(v2);

        assertNotSame(reader1, reader2,
                "Schemas differing only in connect.parameters must produce different DatumReaders");
    }

    @Test
    public void testWriterCacheDistinguishesSchemasDifferingOnlyInProperties() {
        Schema v1 = new Schema.Parser().parse(
                "{\"type\":\"record\",\"name\":\"Value\",\"namespace\":\"test\",\"fields\":["
                        + "{\"name\":\"col\",\"type\":{\"type\":\"string\","
                        + "\"connect.default\":\"-6\"}}"
                        + "]}");

        Schema v2 = new Schema.Parser().parse(
                "{\"type\":\"record\",\"name\":\"Value\",\"namespace\":\"test\",\"fields\":["
                        + "{\"name\":\"col\",\"type\":{\"type\":\"string\","
                        + "\"connect.default\":\"-9\"}}"
                        + "]}");

        GenericRecord record1 = new GenericData.Record(v1);
        record1.put("col", "x");
        GenericRecord record2 = new GenericData.Record(v2);
        record2.put("col", "x");

        DatumWriter<GenericRecord> writer1 = provider.createDatumWriter(record1, v1);
        DatumWriter<GenericRecord> writer2 = provider.createDatumWriter(record2, v2);

        assertNotSame(writer1, writer2,
                "Schemas differing only in connect.default must produce different DatumWriters");
    }

    @Test
    public void testCacheReturnsExistingInstanceForIdenticalSchema() {
        Schema schema = new Schema.Parser().parse(
                "{\"type\":\"record\",\"name\":\"Value\",\"namespace\":\"test\",\"fields\":["
                        + "{\"name\":\"id\",\"type\":\"long\"}"
                        + "]}");

        DatumReader<GenericRecord> reader1 = provider.createDatumReader(schema);
        DatumReader<GenericRecord> reader2 = provider.createDatumReader(schema);

        assertSame(reader1, reader2, "Same schema instance should return cached DatumReader");
    }
}
