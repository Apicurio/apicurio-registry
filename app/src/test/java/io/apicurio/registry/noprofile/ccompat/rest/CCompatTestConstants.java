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
    public static final String SCHEMA_SIMPLE_WRAPPED = "{\"schema\":\"{\\\"type\\\": \\\"string\\\"}\"}";

    public static final String SCHEMA_SIMPLE_WRAPPED_WITH_TYPE = "{\"schema\":\"{\\\"type\\\": \\\"string\\\"}\","
            + "\"schemaType\": \"AVRO\"}";

    public static final String PROTOBUF_SCHEMA_SIMPLE_WRAPPED_WITH_TYPE = "{\"schema\":\"message SearchRequest { required string query = 1; optional int32 page_number = 2;  optional int32 result_per_page = 3; }\",\"schemaType\": \"PROTOBUF\"}";

    public static final String JSON_SCHEMA_SIMPLE_WRAPPED_WITH_TYPE = "{\"schema\":\"{\\\"type\\\":\\\"object\\\",\\\"properties\\\":{\\\"f1\\\":{\\\"type\\\":\\\"string\\\"}}}\"}\",\"schemaType\": \"JSON\"}";

    public static final String SCHEMA_SIMPLE_WRAPPED_WITH_DEFAULT_QUOTED = "{\"schema\": \"{\\\"name\\\": \\\"EloquaContactRecordData\\\", \\\"type\\\": \\\"record\\\", \\\"fields\\\": [{\\\"name\\\": \\\"eloqua_contact_record\\\", \\\"type\\\": {\\\"name\\\": \\\"EloquaContactRecord\\\", \\\"type\\\": \\\"record\\\", \\\"fields\\\": [{\\\"name\\\": \\\"contact_id\\\", \\\"type\\\": \\\"string\\\", \\\"default\\\": \\\"\\\"}, {\\\"name\\\": \\\"field_map\\\", \\\"type\\\": {\\\"type\\\": \\\"map\\\", \\\"values\\\": \\\"string\\\"}, \\\"default\\\": \\\"{}\\\"}]}}]}\"}";

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
