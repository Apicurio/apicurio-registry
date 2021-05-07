package io.apicurio.registry.storage.impl.elasticsql.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;


/**
 * @author vvilerio
 */
@AllArgsConstructor
@Builder
@EqualsAndHashCode
@ToString
public class PropertiesDto {

    private String name;
    private String value;

    /**
     * Constructor.
     */
    public PropertiesDto() {
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     *
     * @return the value
     */
    public String getValue() {
        return value;
    }

    /**
     * @param valuethe name to set
     */
    public void setValue(String value) {
        this.value = value;
    }
}
