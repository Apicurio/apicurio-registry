package io.apicurio.registry.ccompat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * @author Ales Justin
 */
public class Schema implements Comparable<Schema> {

    private String subject;
    private Integer version;
    private Integer id;
    private String schema;

    public Schema(@JsonProperty("subject") String subject,
                  @JsonProperty("version") Integer version,
                  @JsonProperty("id") Integer id,
                  @JsonProperty("schema") String schema) {
        this.subject = subject;
        this.version = version;
        this.id = id;
        this.schema = schema;
    }

    @JsonProperty("subject")
    public String getSubject() {
        return subject;
    }

    @JsonProperty("subject")
    public void setSubject(String subject) {
        this.subject = subject;
    }

    @JsonProperty("version")
    public Integer getVersion() {
        return this.version;
    }

    @JsonProperty("version")
    public void setVersion(Integer version) {
        this.version = version;
    }

    @JsonProperty("id")
    public Integer getId() {
        return this.id;
    }

    @JsonProperty("id")
    public void setId(Integer id) {
        this.id = id;
    }

    @JsonProperty("schema")
    public String getSchema() {
        return this.schema;
    }

    @JsonProperty("schema")
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
               ", content='" + schema + '\'' +
               '}';
    }
}
