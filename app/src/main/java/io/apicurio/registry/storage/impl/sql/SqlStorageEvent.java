package io.apicurio.registry.storage.impl.sql;


public class SqlStorageEvent {
    
    private SqlStorageEventType type;
    
    /**
     * Constructor.
     */
    public SqlStorageEvent() {
    }

    /**
     * @return the type
     */
    public SqlStorageEventType getType() {
        return type;
    }

    /**
     * @param type the type to set
     */
    public void setType(SqlStorageEventType type) {
        this.type = type;
    }

}
