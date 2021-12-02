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
 * are specific to PostgreSQL, where applicable.
 * @author eric.wittmann@gmail.com
 */
public class CloudSpannerSqlStatements extends CommonSqlStatements {

    /**
     * Constructor.
     * @param config
     */
    public CloudSpannerSqlStatements() {
    }

    /**
     * @see SqlStatements#dbType()
     */
    @Override
    public String dbType() {
        return "postgresql";
    }

    /**
     * @see SqlStatements#isPrimaryKeyViolation(Exception)
     */
    @Override
    public boolean isPrimaryKeyViolation(Exception error) {
        return error.getMessage().contains("violates unique constraint");
    }

    /**
     * @see SqlStatements#isForeignKeyViolation(Exception)
     */
    @Override
    public boolean isForeignKeyViolation(Exception error) {
        return error.getMessage().contains("violates foreign key constraint");
    }

    /**
     * @see SqlStatements.core.storage.jdbc.ISqlStatements#isDatabaseInitialized()
     */
    @Override
    public String isDatabaseInitialized() {
        return "SELECT count(*) AS count FROM information_schema.tables WHERE table_name = 'artifacts' LIMIT 1";
    }

    /**
     * @see SqlStatements#upsertContent()
     */
    @Override
    public String upsertContent() {
        return "INSERT INTO content (canonicalHash, contentHash, content) VALUES (?, ?, ?) ON CONFLICT (contentHash) DO NOTHING";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#upsertLogConfiguration()
     */
    @Override
    public String upsertLogConfiguration() {
        return "MERGE INTO logconfiguration (logger, loArtifactTypeUtilglevel) KEY (logger) VALUES(?, ?)";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#getNextSequenceValue()
     */
    @Override
    public String getNextSequenceValue() {
        return "UPDATE sequences sa SET value = (SELECT sb.value + 1 FROM sequences sb WHERE sb.tenantId = sa.tenantId AND sb.name = sa.name) WHERE sa.tenantId = ? AND sa.name = ?";

    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.SqlStatements#resetSequenceValue()
     */
    @Override
    public String resetSequenceValue() {
        return "MERGE INTO sequences (tenantId, name, value) KEY (tenantId, name) VALUES(?, ?, ?)";
    }

//    @Override
//    public String upsertLogConfiguration() {
//        return null;
//    }
//
//    @Override
//    public String getNextSequenceValue() {
//        return null;
//    }
//
//    @Override
//    public String resetSequenceValue() {
//        return null;
//    }

}
