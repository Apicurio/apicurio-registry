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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared base class for all sql statements.
 * @author eric.wittmann@gmail.com
 */
public abstract class CommonSqlStatements implements ISqlStatements {

    /**
     * Constructor.
     */
    public CommonSqlStatements() {
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.sql.hub.core.storage.jdbc.ISqlStatements#databaseInitialization()
     */
    @Override
    public List<String> databaseInitialization() {
        DdlParser parser = new DdlParser();
        try (InputStream input = getClass().getResourceAsStream(dbType() + ".ddl")) {
            return parser.parse(input);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.hub.core.storage.jdbc.ISqlStatements#databaseUpgrade(int, int)
     */
    @Override
    public List<String> databaseUpgrade(int fromVersion, int toVersion) {
        List<String> statements = new ArrayList<>();
        DdlParser parser = new DdlParser();
        
        for (int version = fromVersion + 1; version <= toVersion; version++) {
            try (InputStream input = getClass().getResourceAsStream("upgrades/" + version + "/" + dbType() + ".ddl")) {
                statements.addAll(parser.parse(input));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        
        return statements;
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.sql.ISqlStatements#isPrimaryKeyViolation(java.lang.Exception)
     */
    @Override
    public boolean isPrimaryKeyViolation(Exception error) {
        return error.getMessage().contains("primary key violation");
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.ISqlStatements#getDatabaseVersion()
     */
    @Override
    public String getDatabaseVersion() {
        return "SELECT a.prop_value FROM apicurio a WHERE a.prop_name = ?";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.ISqlStatements#insertGlobalRule()
     */
    @Override
    public String insertGlobalRule() {
        return "INSERT INTO globalrules (type, configuration) VALUES (?, ?)";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.ISqlStatements#selectGlobalRules()
     */
    @Override
    public String selectGlobalRules() {
        return "SELECT r.type FROM globalrules r";
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.sql.ISqlStatements#selectGlobalRuleByType()
     */
    @Override
    public String selectGlobalRuleByType() {
        return "SELECT r.* FROM globalrules r WHERE r.type = ?";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.ISqlStatements#deleteGlobalRule()
     */
    @Override
    public String deleteGlobalRule() {
        return "DELETE FROM globalrules WHERE type = ?";
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.sql.ISqlStatements#deleteGlobalRules()
     */
    @Override
    public String deleteGlobalRules() {
        return "DELETE FROM globalrules";
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.ISqlStatements#updateGlobalRule()
     */
    @Override
    public String updateGlobalRule() {
        return "UPDATE globalrules SET configuration = ? WHERE type = ?";
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.sql.ISqlStatements#insertArtifact()
     */
    @Override
    public String insertArtifact() {
        return "INSERT INTO artifacts (artifactId, type, createdBy, createdOn) VALUES (?, ?, ?, ?)";
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.sql.ISqlStatements#updateArtifactLatestGlobalId()
     */
    @Override
    public String updateArtifactLatestGlobalId() {
        return "UPDATE artifacts SET latest = ? WHERE artifactId = ?";
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.sql.ISqlStatements#insertVersion()
     */
    @Override
    public String insertVersion(boolean firstVersion) {
        if (firstVersion) {
            return "INSERT INTO versions (globalId, artifactId, version, state, name, description, createdBy, createdOn, labels, properties) VALUES (?, ?, 1, ?, ?, ?, ?, ?, ?, ?)";
        } else {
            return "INSERT INTO versions (globalId, artifactId, version, state, name, description, createdBy, createdOn, labels, properties) VALUES (?, ?, (SELECT MAX(version) + 1 FROM versions WHERE artifactId = ?), ?, ?, ?, ?, ?, ?, ?)";
        }
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.sql.ISqlStatements#selectArtifactVersionMetaDataByGlobalId()
     */
    @Override
    public String selectArtifactVersionMetaDataByGlobalId() {
        return "SELECT * FROM versions WHERE globalId = ?";
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.sql.ISqlStatements#selectArtifactVersions()
     */
    @Override
    public String selectArtifactVersions() {
        return "SELECT version FROM versions WHERE artifactId = ?";
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.sql.ISqlStatements#selectArtifactVersionMetaData()
     */
    @Override
    public String selectArtifactVersionMetaData() {
        return "SELECT * FROM versions WHERE artifactId = ? AND version = ?";
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.sql.ISqlStatements#selectArtifactVersionContentByGlobalId()
     */
    @Override
    public String selectArtifactVersionContentByGlobalId() {
        return "SELECT v.globalId, v.version, c.content FROM versions v JOIN content c ON v.globalId = c.globalId WHERE v.globalId = ?";
    }
    
    /**
     * @see io.apicurio.registry.storage.impl.sql.ISqlStatements#selectArtifactVersionContent()
     */
    @Override
    public String selectArtifactVersionContent() {
        return "SELECT v.globalId, v.version, c.content FROM versions v JOIN content c ON v.globalId = c.globalId WHERE v.artifactId = ? AND v.version = ?";
    }
}
