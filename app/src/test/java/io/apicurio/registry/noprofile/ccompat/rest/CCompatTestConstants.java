/*
 * Copyright 2022 Red Hat
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

package io.apicurio.registry.noprofile.ccompat.rest;

public class CCompatTestConstants {



    public static final String SCHEMA_SIMPLE = "{\"type\": \"string\"}";

    public static final String SCHEMA_SIMPLE_DEFAULT_QUOTED = "{\"name\": \"EloquaContactRecordData\", \"type\": \"record\", \"fields\": [{\"name\": \"eloqua_contact_record\", \"type\": {\"name\": \"EloquaContactRecord\", \"type\": \"record\", \"fields\": [{\"name\": \"contact_id\", \"type\": \"string\", \"default\": \"\"}, {\"name\": \"field_map\", \"type\": {\"type\": \"map\", \"values\": \"string\"}, \"default\": \"{}\"}]}}]}";

    public static final String SCHEMA_SIMPLE_JSON_QUOTED = "{\"type\":\"record\",\"name\":\"Envelope\",\"namespace\":\"prefix4.public.my_table\",\"fields\":[{\"name\":\"before\",\"type\":[\"null\",{\"type\":\"record\",\"name\":\"Value\",\"fields\":[{\"name\":\"id\",\"type\":{\"type\":\"long\",\"connect.default\":0},\"default\":0},{\"name\":\"metadata\",\"type\":{\"type\":\"string\",\"connect.version\":1,\"connect.default\":\"{}\",\"connect.name\":\"io.debezium.data.Json\"},\"default\":\"{}\"}],\"connect.name\":\"prefix4.public.my_table.Value\"}],\"default\":null},{\"name\":\"after\",\"type\":[\"null\",\"Value\"],\"default\":null},{\"name\":\"source\",\"type\":{\"type\":\"record\",\"name\":\"Source\",\"namespace\":\"io.debezium.connector.postgresql\",\"fields\":[{\"name\":\"version\",\"type\":\"string\"},{\"name\":\"connector\",\"type\":\"string\"},{\"name\":\"name\",\"type\":\"string\"},{\"name\":\"ts_ms\",\"type\":\"long\"},{\"name\":\"snapshot\",\"type\":[{\"type\":\"string\",\"connect.version\":1,\"connect.parameters\":{\"allowed\":\"true,last,false,incremental\"},\"connect.default\":\"false\",\"connect.name\":\"io.debezium.data.Enum\"},\"null\"],\"default\":\"false\"},{\"name\":\"db\",\"type\":\"string\"},{\"name\":\"sequence\",\"type\":[\"null\",\"string\"],\"default\":null},{\"name\":\"schema\",\"type\":\"string\"},{\"name\":\"table\",\"type\":\"string\"},{\"name\":\"txId\",\"type\":[\"null\",\"long\"],\"default\":null},{\"name\":\"lsn\",\"type\":[\"null\",\"long\"],\"default\":null},{\"name\":\"xmin\",\"type\":[\"null\",\"long\"],\"default\":null}],\"connect.name\":\"io.debezium.connector.postgresql.Source\"}},{\"name\":\"op\",\"type\":\"string\"},{\"name\":\"ts_ms\",\"type\":[\"null\",\"long\"],\"default\":null},{\"name\":\"transaction\",\"type\":[\"null\",{\"type\":\"record\",\"name\":\"block\",\"namespace\":\"event\",\"fields\":[{\"name\":\"id\",\"type\":\"string\"},{\"name\":\"total_order\",\"type\":\"long\"},{\"name\":\"data_collection_order\",\"type\":\"long\"}],\"connect.version\":1,\"connect.name\":\"event.block\"}],\"default\":null}],\"connect.version\":1,\"connect.name\":\"prefix4.public.my_table.Envelope\"}";
    public static final String SCHEMA_SIMPLE_WRAPPED = "{\"schema\":\"{\\\"type\\\": \\\"string\\\"}\"}";

    public static final String SCHEMA_SIMPLE_WRAPPED_WITH_TYPE = "{\"schema\":\"{\\\"type\\\": \\\"string\\\"}\","
            + "\"schemaType\": \"AVRO\"}";

    public static final String PROTOBUF_SCHEMA_SIMPLE_WRAPPED_WITH_TYPE = "{\"schema\":\"message SearchRequest { required string query = 1; optional int32 page_number = 2;  optional int32 result_per_page = 3; }\",\"schemaType\": \"PROTOBUF\"}";

    public static final String JSON_SCHEMA_SIMPLE_WRAPPED_WITH_TYPE = "{\"schema\":\"{\\\"type\\\":\\\"object\\\",\\\"properties\\\":{\\\"f1\\\":{\\\"type\\\":\\\"string\\\"}}}\"}\",\"schemaType\": \"JSON\"}";

    public static final String SCHEMA_SIMPLE_WRAPPED_WITH_DEFAULT_QUOTED = "{\"schema\": \"{\\\"name\\\": \\\"EloquaContactRecordData\\\", \\\"type\\\": \\\"record\\\", \\\"fields\\\": [{\\\"name\\\": \\\"eloqua_contact_record\\\", \\\"type\\\": {\\\"name\\\": \\\"EloquaContactRecord\\\", \\\"type\\\": \\\"record\\\", \\\"fields\\\": [{\\\"name\\\": \\\"contact_id\\\", \\\"type\\\": \\\"string\\\", \\\"default\\\": \\\"\\\"}, {\\\"name\\\": \\\"field_map\\\", \\\"type\\\": {\\\"type\\\": \\\"map\\\", \\\"values\\\": \\\"string\\\"}, \\\"default\\\": \\\"{}\\\"}]}}]}\"}";

    public static final String SCHEMA_SIMPLE_WRAPPED_WITH_JSON_DEFAULT = "{\"schema\":\"{\\\"type\\\":\\\"record\\\",\\\"name\\\":\\\"Envelope\\\",\\\"namespace\\\":\\\"prefix4.public.my_table\\\",\\\"fields\\\":[{\\\"name\\\":\\\"before\\\",\\\"type\\\":[\\\"null\\\",{\\\"type\\\":\\\"record\\\",\\\"name\\\":\\\"Value\\\",\\\"fields\\\":[{\\\"name\\\":\\\"id\\\",\\\"type\\\":{\\\"type\\\":\\\"long\\\",\\\"connect.default\\\":0},\\\"default\\\":0},{\\\"name\\\":\\\"metadata\\\",\\\"type\\\":{\\\"type\\\":\\\"string\\\",\\\"connect.version\\\":1,\\\"connect.default\\\":\\\"{}\\\",\\\"connect.name\\\":\\\"io.debezium.data.Json\\\"},\\\"default\\\":\\\"{}\\\"}],\\\"connect.name\\\":\\\"prefix4.public.my_table.Value\\\"}],\\\"default\\\":null},{\\\"name\\\":\\\"after\\\",\\\"type\\\":[\\\"null\\\",\\\"Value\\\"],\\\"default\\\":null},{\\\"name\\\":\\\"source\\\",\\\"type\\\":{\\\"type\\\":\\\"record\\\",\\\"name\\\":\\\"Source\\\",\\\"namespace\\\":\\\"io.debezium.connector.postgresql\\\",\\\"fields\\\":[{\\\"name\\\":\\\"version\\\",\\\"type\\\":\\\"string\\\"},{\\\"name\\\":\\\"connector\\\",\\\"type\\\":\\\"string\\\"},{\\\"name\\\":\\\"name\\\",\\\"type\\\":\\\"string\\\"},{\\\"name\\\":\\\"ts_ms\\\",\\\"type\\\":\\\"long\\\"},{\\\"name\\\":\\\"snapshot\\\",\\\"type\\\":[{\\\"type\\\":\\\"string\\\",\\\"connect.version\\\":1,\\\"connect.parameters\\\":{\\\"allowed\\\":\\\"true,last,false,incremental\\\"},\\\"connect.default\\\":\\\"false\\\",\\\"connect.name\\\":\\\"io.debezium.data.Enum\\\"},\\\"null\\\"],\\\"default\\\":\\\"false\\\"},{\\\"name\\\":\\\"db\\\",\\\"type\\\":\\\"string\\\"},{\\\"name\\\":\\\"sequence\\\",\\\"type\\\":[\\\"null\\\",\\\"string\\\"],\\\"default\\\":null},{\\\"name\\\":\\\"schema\\\",\\\"type\\\":\\\"string\\\"},{\\\"name\\\":\\\"table\\\",\\\"type\\\":\\\"string\\\"},{\\\"name\\\":\\\"txId\\\",\\\"type\\\":[\\\"null\\\",\\\"long\\\"],\\\"default\\\":null},{\\\"name\\\":\\\"lsn\\\",\\\"type\\\":[\\\"null\\\",\\\"long\\\"],\\\"default\\\":null},{\\\"name\\\":\\\"xmin\\\",\\\"type\\\":[\\\"null\\\",\\\"long\\\"],\\\"default\\\":null}],\\\"connect.name\\\":\\\"io.debezium.connector.postgresql.Source\\\"}},{\\\"name\\\":\\\"op\\\",\\\"type\\\":\\\"string\\\"},{\\\"name\\\":\\\"ts_ms\\\",\\\"type\\\":[\\\"null\\\",\\\"long\\\"],\\\"default\\\":null},{\\\"name\\\":\\\"transaction\\\",\\\"type\\\":[\\\"null\\\",{\\\"type\\\":\\\"record\\\",\\\"name\\\":\\\"block\\\",\\\"namespace\\\":\\\"event\\\",\\\"fields\\\":[{\\\"name\\\":\\\"id\\\",\\\"type\\\":\\\"string\\\"},{\\\"name\\\":\\\"total_order\\\",\\\"type\\\":\\\"long\\\"},{\\\"name\\\":\\\"data_collection_order\\\",\\\"type\\\":\\\"long\\\"}],\\\"connect.version\\\":1,\\\"connect.name\\\":\\\"event.block\\\"}],\\\"default\\\":null}],\\\"connect.version\\\":1,\\\"connect.name\\\":\\\"prefix4.public.my_table.Envelope\\\"}\"}";

    public static final String SCHEMA_1_WRAPPED = "{\"schema\": \"{\\\"type\\\": \\\"record\\\", \\\"name\\\": \\\"test1\\\", " +
            "\\\"fields\\\": [ {\\\"type\\\": \\\"string\\\", \\\"name\\\": \\\"field1\\\"} ] }\"}\"";

    public static final String SCHEMA_2_WRAPPED = "{\"schema\": \"{\\\"type\\\": \\\"record\\\", \\\"name\\\": \\\"test1\\\", " +
            "\\\"fields\\\": [ {\\\"type\\\": \\\"string\\\", \\\"name\\\": \\\"field1\\\"}, " +
            "{\\\"type\\\": \\\"string\\\", \\\"name\\\": \\\"field2\\\"} ] }\"}\"";

    public static final String SCHEMA_3_WRAPPED_TEMPLATE = "{\"schema\": \"{\\\"type\\\": \\\"record\\\", \\\"name\\\": \\\"test2\\\", " +
            "\\\"fields\\\": [ {\\\"type\\\": \\\"string\\\", \\\"name\\\": \\\"field1\\\"}, " +
            "{\\\"type\\\": \\\"string\\\", \\\"name\\\": \\\"field2\\\"} ] }\", \"references\": [{\"name\": \"testname\", \"subject\": \"%s\", \"version\": %d}]}";

    public static final String CONFIG_BACKWARD = "{\"compatibility\": \"BACKWARD\"}";

    public static final String VALID_AVRO_SCHEMA = "{\"schema\": \"{\\\"type\\\": \\\"record\\\",\\\"name\\\": \\\"myrecord1\\\",\\\"fields\\\": [{\\\"name\\\": \\\"foo1\\\",\\\"type\\\": \\\"string\\\"}]}\"}\"";

    public static final String SCHEMA_INVALID_WRAPPED = "{\"schema\":\"{\\\"type\\\": \\\"bloop\\\"}\"}";

    public static final String V6_BASE_PATH = "/ccompat/v6";

    public static final String V7_BASE_PATH = "/ccompat/v7";

}
