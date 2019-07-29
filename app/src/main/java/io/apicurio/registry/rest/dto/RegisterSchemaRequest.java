package io.apicurio.registry.rest.dto;

import java.util.Objects;
import javax.json.bind.annotation.JsonbProperty;

/**
 * @author Ales Justin
 */
public class RegisterSchemaRequest {

    private Integer version;
    private Integer id;
    private String schema;

    @JsonbProperty("version")
    public Integer getVersion() {
        return this.version;
    }

    @JsonbProperty("version")
    public void setVersion(Integer version) {
        this.version = version;
    }

    @JsonbProperty("id")
    public Integer getId() {
        return this.id;
    }

    @JsonbProperty("id")
    public void setId(Integer id) {
        this.id = id;
    }

    @JsonbProperty("schema")
    public String getSchema() {
        return this.schema;
    }

    @JsonbProperty("schema")
    public void setSchema(String schema) {
        this.schema = schema;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RegisterSchemaRequest that = (RegisterSchemaRequest) o;
        return Objects.equals(version, that.version)
               && Objects.equals(id, that.id)
               && Objects.equals(schema, that.schema);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, id, schema);
    }

    @Override
    public String toString() {
        return "RegisterSchemaRequest{" +
               "version=" + version +
               ", id=" + id +
               ", schema='" + schema + '\'' +
               '}';
    }
}
