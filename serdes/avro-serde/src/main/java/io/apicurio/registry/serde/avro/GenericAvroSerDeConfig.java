package io.apicurio.registry.serde.avro;

import io.apicurio.registry.serde.generic.GenericConfig;
import io.apicurio.registry.serde.generic.GenericSerDeConfig;

import java.util.Map;

import static io.apicurio.registry.serde.avro.AvroKafkaSerdeConfig.AVRO_DATUM_PROVIDER;
import static io.apicurio.registry.serde.avro.AvroKafkaSerdeConfig.AVRO_ENCODING;

public class GenericAvroSerDeConfig extends GenericSerDeConfig {


    public GenericAvroSerDeConfig(Map<String, Object> rawConfig) {
        super(rawConfig);
    }


    public GenericAvroSerDeConfig(GenericConfig config) {
        super(config.getRawConfig());
    }


    public AvroEncoding getAvroEncoding() {
        return AvroEncoding.valueOf(getString(AVRO_ENCODING));
    }


    public AvroDatumProvider getAvroDatumProvider() {
        return getInstance(AVRO_DATUM_PROVIDER, AvroDatumProvider.class);
    }
}
