/*
 * Copyright 2023 Red Hat
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

-- *********************************************************************
-- DDL for the Apicurio Registry - Database: PostgreSQL
-- Upgrades the DB schema from version 4 to version 5.
-- *********************************************************************

UPDATE apicurio SET prop_value = 5 WHERE prop_name = 'db_version';

UPGRADER:io.apicurio.registry.storage.impl.sql.upgrader.ProtobufCanonicalHashUpgrader;