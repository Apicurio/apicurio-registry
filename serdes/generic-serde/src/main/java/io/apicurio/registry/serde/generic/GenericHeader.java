package io.apicurio.registry.serde.generic;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@EqualsAndHashCode
@ToString
public class GenericHeader {

    @Getter
    private String key;

    @Getter
    private String value;


    public GenericHeader(String key, String value) {
        this.key = key;
        this.value = value;
    }
}
