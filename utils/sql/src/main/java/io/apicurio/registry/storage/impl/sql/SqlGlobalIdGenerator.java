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

import org.jdbi.v3.core.Handle;

/**
 * @author Fabian Martinez
 */

public class SqlGlobalIdGenerator {

    public static GlobalIdGenerator withHandle(Handle handle) {
        return () -> {

            if(!handle.createQuery("SELECT next_value FROM sequences where name = \"globalId\";").mapTo(Long.class).findFirst().isPresent()){

                handle.createUpdate("INSERT into sequences (next_value, name) VALUES (?,?)").bind(0, 0).bind(1, "globalId").execute();
            } else {
                Long globalId = handle.createQuery("SELECT next_value FROM sequences where name = \"globalId\";").mapTo(Long.class).findFirst().get();
                handle.createUpdate("UPDATE sequences SET next_value = ? WHERE name = ?").bind(0, globalId + 1).bind(1, "globalId").execute();
            }

            Long globalId = handle.createQuery("SELECT next_value FROM sequences where name = \"globalId\";").mapTo(Long.class).findFirst().get();

            return globalId;
        };
    }

}
