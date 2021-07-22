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

package io.apicurio.registry.rest.client.config;

public class ClientConfig {
    public static final String REGISTRY_REQUEST_HEADERS_PREFIX = "apicurio.registry.request.headers.";
    public static final String REGISTRY_REQUEST_TRUSTSTORE_PREFIX = "apicurio.registry.request.ssl.truststore";
    public static final String REGISTRY_REQUEST_TRUSTSTORE_LOCATION = REGISTRY_REQUEST_TRUSTSTORE_PREFIX + ".location";
    public static final String REGISTRY_REQUEST_TRUSTSTORE_TYPE = REGISTRY_REQUEST_TRUSTSTORE_PREFIX + ".type";
    public static final String REGISTRY_REQUEST_TRUSTSTORE_PASSWORD = REGISTRY_REQUEST_TRUSTSTORE_PREFIX + ".password";
    public static final String REGISTRY_REQUEST_KEYSTORE_PREFIX = "apicurio.registry.request.ssl.keystore";
    public static final String REGISTRY_REQUEST_KEYSTORE_LOCATION = REGISTRY_REQUEST_KEYSTORE_PREFIX + ".location";
    public static final String REGISTRY_REQUEST_KEYSTORE_TYPE = REGISTRY_REQUEST_KEYSTORE_PREFIX + ".type";
    public static final String REGISTRY_REQUEST_KEYSTORE_PASSWORD = REGISTRY_REQUEST_KEYSTORE_PREFIX + ".password";
    public static final String REGISTRY_REQUEST_KEY_PASSWORD = "apicurio.registry.request.ssl.key.password";
    public static final String REGISTRY_CLIENT_DISABLE_AUTO_BASE_PATH_APPEND = "apicurio.registry.client.disable-auto-basepath-append";
    public static final String REGISTRY_CLIENT_AUTO_BASE_PATH = "apicurio.registry.rest.client.auto-base-path";
}
