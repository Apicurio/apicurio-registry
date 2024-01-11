package io.apicurio.registry.serde.generic;

import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.SchemaParser;
import org.apache.kafka.common.header.Headers;

import java.io.OutputStream;
import java.nio.ByteBuffer;

public interface GenericSerDeDatatype<SCHEMA, DATA> extends Configurable {


    void writeData(Headers headers, ParsedSchema<SCHEMA> schema, DATA data, OutputStream out) throws Exception;


    DATA readData(Headers headers, ParsedSchema<SCHEMA> schema, ByteBuffer buffer);


    SchemaParser<SCHEMA, DATA> getSchemaParser();
}
