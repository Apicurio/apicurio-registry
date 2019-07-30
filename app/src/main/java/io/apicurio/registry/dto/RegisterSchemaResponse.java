package io.apicurio.registry.dto;

import javax.json.bind.annotation.JsonbProperty;

/**
 * @author Ales Justin
 */
public class RegisterSchemaResponse {

    private int id;

    @JsonbProperty("id")
    public int getId() {
        return id;
    }

    @JsonbProperty("id")
    public void setId(int id) {
        this.id = id;
    }
}
