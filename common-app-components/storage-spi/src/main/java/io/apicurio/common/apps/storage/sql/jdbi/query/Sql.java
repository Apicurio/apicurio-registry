/*
 * Copyright 2021 Red Hat
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

package io.apicurio.common.apps.storage.sql.jdbi.query;

import io.apicurio.common.apps.content.handle.ContentHandle;
import io.apicurio.common.apps.storage.exceptions.StorageException;
import io.apicurio.common.apps.storage.sql.jdbi.Handle;

import java.time.Instant;
import java.util.Date;

/**
 * @author eric.wittmann@gmail.com
 * @author Jakub Senko <em>m@jsenko.net</em>
 */
public interface Sql<Q> {

    String RESOURCE_CONTEXT_KEY = "resource";
    String RESOURCE_IDENTIFIER_CONTEXT_KEY = "resource_id";

    /**
     * There are some use cases when it is useful to create a new query and only later provide the handle
     * before it is executed. One such use case is supporting queries in the *SqlStatements interfaces which
     * differ in the number or order of positional arguments.
     */
    Q setHandleOnce(Handle handle) throws StorageException;

    /**
     * Attach additional information to the query being executed. This is currently used for providing
     * additional information to StorageException(s).
     */
    Q setContext(String key, String value);

    Q bind(int position, String value);

    Q bind(int position, Long value);

    Q bind(int position, Integer value);

    Q bind(int position, Enum<?> value);

    Q bind(int position, Date value);

    Q bind(int position, Instant value);

    Q bind(int position, byte[] value);

    Q bind(int position, ContentHandle value);
}
