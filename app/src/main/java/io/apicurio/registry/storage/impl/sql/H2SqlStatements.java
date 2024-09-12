/*
 * Copyright 2020 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.storage.impl.sql;

/**
 * H2 implementation of the sql statements interface.  Provides sql statements that
 * are specific to H2, where applicable.
 * @author eric.wittmann@gmail.com
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
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#isDatabaseInitialized()
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
        return "MERGE INTO content AS target" +
                " USING (VALUES(?, ?, ?, ?, ?, ?)) AS source (tenantId, contentId, canonicalHash, contentHash, content, artifactreferences)" +
                " ON (target.tenantId = source.tenantId AND target.contentHash = source.contentHash)" +
                " WHEN NOT MATCHED THEN" +
                "     INSERT (tenantId, contentId, canonicalHash, contentHash, content, artifactreferences)" +
                "     VALUES (source.tenantId, source.contentId, source.canonicalHash, source.contentHash, source.content, source.artifactreferences)";
    }

    @Override
    public String upsertGroup() {
        return "MERGE INTO groups (tenantId, groupId, description, artifactsType, createdBy, createdOn, modifiedBy, modifiedOn, properties)" +
                " KEY (tenantId, groupId)" +
                " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#upsertLogConfiguration()
     */
    @Override
    public String upsertLogConfiguration() {
        return "MERGE INTO logconfiguration (logger, loglevel) KEY (logger) VALUES(?, ?)";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#getNextSequenceValue()
     */
    @Override
    public String getNextSequenceValue() {
        throw new RuntimeException("Not supported for H2: getNextSequenceValue");
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#selectCurrentSequenceValue()
     */
    @Override
    public String selectCurrentSequenceValue() {
        throw new RuntimeException("Not supported for H2: selectCurrentSequenceValue");
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#resetSequenceValue()
     */
    @Override
    public String resetSequenceValue() {
        throw new RuntimeException("Not supported for H2: resetSequenceValue");
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#insertSequenceValue()
     */
    @Override
    public String insertSequenceValue() {
        throw new RuntimeException("Not supported for H2: insertSequenceValue");
    }

    /**
     * @see SqlStatements#upsertReference()
     */
    @Override
    public String upsertReference() {
        return "MERGE INTO artifactreferences AS target" +
                " USING (VALUES (?, ?, ?, ?, ?, ?)) AS source (tenantId, contentId, groupId, artifactId, version, name)" +
                " ON (target.tenantId = source.tenantId AND target.contentId = source.contentId AND target.name = source.name)" +
                " WHEN NOT MATCHED THEN" +
                "     INSERT (tenantId, contentId, groupId, artifactId, version, name)" +
                "     VALUES (source.tenantId, source.contentId, source.groupId, source.artifactId, source.version, source.name)";
    }
}
