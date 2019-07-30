package io.apicurio.registry.dto;

/**
 * @author Ales Justin
 */
public class SchemaEntity extends Schema {
    private boolean deleted;

    public SchemaEntity(String subject, Integer version, Integer id, String schema) {
        super(subject, version, id, schema);
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
}
