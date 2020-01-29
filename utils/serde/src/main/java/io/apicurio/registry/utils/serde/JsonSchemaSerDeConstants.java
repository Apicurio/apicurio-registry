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

package io.apicurio.registry.utils.serde;

/**
 * @author eric.wittmann@gmail.com
 */
public final class JsonSchemaSerDeConstants {
    
    public static final String HEADER_ARTIFACT_ID = "apicurio.artifactId";
    public static final String HEADER_VERSION = "apicurio.version";
    public static final String HEADER_GLOBAL_ID = "apicurio.globalId";
    public static final String HEADER_MSG_TYPE = "apicurio.messageType";

    public static final String REGISTRY_JSON_SCHEMA_VALIDATION_ENABLED = "apicurio.registry.serdes.json-schema.validation-enabled";

}
