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

package io.apicurio.registry.serde;

/**
 * Contains all of the header names used when serde classes are configured to pass information
 * via headers instead of via the message payload.  Note that these header names can be overridden
 * via configuration.
 *
 * @author eric.wittmann@gmail.com
 */
public class SerdeHeaders {

    public static final String HEADER_KEY_ENCODING = "apicurio.key.encoding";
    public static final String HEADER_VALUE_ENCODING = "apicurio.value.encoding";
    public static final String HEADER_KEY_GROUP_ID = "apicurio.key.groupId";
    public static final String HEADER_VALUE_GROUP_ID = "apicurio.value.groupId";
    public static final String HEADER_KEY_ARTIFACT_ID = "apicurio.key.artifactId";
    public static final String HEADER_VALUE_ARTIFACT_ID = "apicurio.value.artifactId";
    public static final String HEADER_KEY_VERSION = "apicurio.key.version";
    public static final String HEADER_VALUE_VERSION = "apicurio.value.version";
    public static final String HEADER_KEY_GLOBAL_ID = "apicurio.key.globalId";
    public static final String HEADER_VALUE_GLOBAL_ID = "apicurio.value.globalId";
    public static final String HEADER_KEY_CONTENT_ID = "apicurio.key.contentId";
    public static final String HEADER_VALUE_CONTENT_ID = "apicurio.value.contentId";
    public static final String HEADER_KEY_MESSAGE_TYPE = "apicurio.key.msgType";
    public static final String HEADER_VALUE_MESSAGE_TYPE = "apicurio.value.msgType";

}

