package io.apicurio.registry.storage.impl.sql;

/**
 * H2 implementation of the sql statements interface.  Provides sql statements that
 * are specific to H2, where applicable.
 */
public class H2SqlStatements extends CommonSqlStatements {

    /**
     * Constructor.
     */
    public H2SqlStatements() {
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#dbType()
     */
    @Override
    public String dbType() {
        return "h2";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#isPrimaryKeyViolation(java.lang.Exception)
     */
    @Override
    public boolean isPrimaryKeyViolation(Exception error) {
        return error.getMessage() != null && error.getMessage().contains("primary key violation");
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#isForeignKeyViolation(java.lang.Exception)
     */
    @Override
    public boolean isForeignKeyViolation(Exception error) {
        return error.getMessage() != null && error.getMessage().contains("Referential integrity constraint violation");
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements.core.storage.jdbc.ISqlStatements#isDatabaseInitialized()
     */
    @Override
    public String isDatabaseInitialized() {
        return "SELECT COUNT(*) AS count FROM information_schema.tables WHERE table_name = 'APICURIO'";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#upsertContent()
     */
    @Override
    public String upsertContent() {
        return "INSERT INTO content (contentId, canonicalHash, contentHash, contentType, content, refs) VALUES (?, ?, ?, ?, ?, ?)";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#getNextSequenceValue()
     */
    @Override
    public String getNextSequenceValue() {
        throw new RuntimeException("Not applicable when using H2 as the database kind.");
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#resetSequenceValue()
     */
    @Override
    public String resetSequenceValue() {
        return "MERGE INTO sequences (seqName, seqValue) KEY (seqName) VALUES(?, ?)";
    }

    /**
     * @see SqlStatements#upsertContentReference()
     */
    @Override
    public String upsertContentReference() {
        return "INSERT INTO content_references (contentId, groupId, artifactId, version, name) VALUES (?, ?, ?, ?, ?)";
    }

    @Override
    public String createDataSnapshot() {
        return "SCRIPT TO ?";
    }

    @Override
    public String restoreFromSnapshot() {
        return "RUNSCRIPT FROM ?";
    }
}
