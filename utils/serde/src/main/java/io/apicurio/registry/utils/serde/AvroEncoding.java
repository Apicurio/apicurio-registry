package io.apicurio.registry.utils.serde;

import java.util.Map;

public enum AvroEncoding {

    BINARY,
    JSON;

    public static final String AVRO_ENCODING = "apicurio.avro.encoding";

    public static final String AVRO_JSON = "JSON";

    public static final String AVRO_BINARY = "BINARY";

    public static AvroEncoding fromConfig(Map<String, ?> config){
        AvroEncoding encoding = AvroEncoding.BINARY;
        if(config.containsKey(AVRO_ENCODING)){
            try {
                encoding = AvroEncoding.valueOf((String) config.get(AVRO_ENCODING));
            }
            catch (IllegalArgumentException ex){
            }
        }
        return encoding;
    }
}
