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

package io.apicurio.common.apps.storage.sql;

import io.apicurio.common.apps.storage.exceptions.StorageException;
import io.apicurio.common.apps.storage.sql.jdbi.Handle;
import io.apicurio.common.apps.storage.sql.jdbi.query.Update;

import java.sql.SQLException;

/**
 * Returns SQL statements used by BaseSqlStorageComponent. There can be different implementations of this
 * interface depending on the database being used.
 *
 * @author eric.wittmann@gmail.com
 * @author Jakub Senko <em>m@jsenko.net</em>
 */
public interface SqlStatements {

    /**
     * Gets the database type associated with these statements.
     */
    String dbType();

    /**
     * Returns true if the given exception represents a primary key violation.
     */
    boolean isPrimaryKeyViolation(SQLException ex);

    /**
     * Returns true if the given exception represents a foreign key violation.
     */
    boolean isForeignKeyViolation(SQLException ex);

    /**
     * A statement that returns 'true' if the database has already been initialized.
     */
    boolean isDatabaseInitialized(Handle handle) throws StorageException;

    String getStorageProperty();

    Update setStorageProperty(String key, String value);

    String getNextSequenceValue();

    String getSequenceValue();

    String casSequenceValue();

    String insertSequenceValue();
}
