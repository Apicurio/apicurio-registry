package io.apicurio.registry.serde.avro;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GenericAvroSerializerConfig {

    AvroEncoding encoding;
    AvroDatumProvider<?> avroDatumProvider;
}
