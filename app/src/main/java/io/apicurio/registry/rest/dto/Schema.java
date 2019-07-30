package io.apicurio.registry.rest.dto;

import java.util.Objects;
import javax.json.bind.annotation.JsonbProperty;

/**
 * @author Ales Justin
 */
public class Schema implements Comparable<Schema> {

    private String subject;
    private Integer version;
    private Integer id;
    private String schema;

    public Schema(@JsonbProperty("subject") String subject,
                  @JsonbProperty("version") Integer version,
                  @JsonbProperty("id") Integer id,
                  @JsonbProperty("schema") String schema) {
        this.subject = subject;
        this.version = version;
        this.id = id;
        this.schema = schema;
    }

    @JsonbProperty("subject")
    public String getSubject() {
        return subject;
    }

    @JsonbProperty("subject")
    public void setSubject(String subject) {
        this.subject = subject;
    }

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
    public int compareTo(Schema that) {
        int result = subject.compareTo(that.subject);
        if (result != 0) {
            return result;
        }
        result = this.version - that.version;
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Schema schema1 = (Schema) o;
        return Objects.equals(subject, schema1.subject) &&
               Objects.equals(version, schema1.version) &&
               Objects.equals(id, schema1.id) &&
               Objects.equals(schema, schema1.schema);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subject, version, id, schema);
    }

    @Override
    public String toString() {
        return "Schema{" +
               "subject='" + subject + '\'' +
               ", version=" + version +
               ", id=" + id +
               ", schema='" + schema + '\'' +
               '}';
    }
}
