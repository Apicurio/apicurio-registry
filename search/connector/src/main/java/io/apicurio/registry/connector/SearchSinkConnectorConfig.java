/*
 * Copyright 2019 Red Hat
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

package io.apicurio.registry.connector;

import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;

import static io.apicurio.registry.search.client.SearchClientFactory.SEARCH_CLIENT_CLASS;
import static io.apicurio.registry.search.client.SearchClientFactory.SEARCH_CLIENT_CLASSES;

import java.util.Map;

/**
 * @author Ales Justin
 */
public class SearchSinkConnectorConfig extends AbstractConfig {

    public static final String VERSION = "connector.version";

    private static final String SEARCH_CLIENT_CLASS_DOC = "Search client class";
    private static final String SEARCH_CLIENT_CLASSES_DOC = "Search client classes";

    public static final String SEARCH_CLIENT_INITIALIZE = "search.client-initialize";
    private static final boolean SEARCH_CLIENT_INITIALIZE_DEFAULT = false;
    private static final String SEARCH_CLIENT_INITIALIZE_DOC = "Initialize search client";

    public static final String SEARCH_HOST = "search.host";
    private static final String SEARCH_HOST_DEFAULT = "localhost";
    private static final String SEARCH_HOST_DOC = "Infinispan server host";

    public static final String SEARCH_PORT = "search.port";
    private static final int SEARCH_PORT_DEFAULT = 11222;
    private static final String SEARCH_PORT_DOC = "Infinispan server port";

    public static final String SEARCH_USERNAME = "search.username";
    private static final String SEARCH_USERNAME_DEFAULT = "user";
    private static final String SEARCH_USERNAME_DOC = "Infinispan server username";

    public static final String SEARCH_PASSWORD = "search.password";
    private static final String SEARCH_PASSWORD_DEFAULT = "pass";
    private static final String SEARCH_PASSWORD_DOC = "Infinispan server password";

    public static final String SEARCH_CACHE_NAME = "search.cache-name";
    private static final String SEARCH_CACHE_NAME_DEFAULT = "__search_cache";
    private static final String SEARCH_CACHE_NAME_DOC = "Infinispan cache name";

    public static final String SEARCH_REALM = "search.realm";
    private static final String SEARCH_REALM_DEFAULT = "default";
    private static final String SEARCH_REALM_DOC = "Infinispan server realm";

    public static final String SEARCH_SERVER_NAME = "search.server-name";
    private static final String SEARCH_SERVER_NAME_DEFAULT = "infinispan";
    private static final String SEARCH_SERVER_NAME_DOC = "Infinispan server name";

    public static final String SEARCH_CLIENT_INTELLIGENCE = "search.client-intelligence";
    private static final String SEARCH_CLIENT_INTELLIGENCE_DEFAULT = "BASIC";
    private static final String SEARCH_CLIENT_INTELLIGENCE_DOC = "Infinispan client intelligence";

    public static ConfigDef config() {
        ConfigDef configDef = new ConfigDef();

        configDef.define(
            SEARCH_CLIENT_CLASS,
            ConfigDef.Type.CLASS,
            null,
            ConfigDef.Importance.LOW,
            SEARCH_CLIENT_CLASS_DOC
        ).define(
            SEARCH_CLIENT_CLASSES,
            ConfigDef.Type.STRING,
            null,
            ConfigDef.Importance.LOW,
            SEARCH_CLIENT_CLASSES_DOC
        ).define(
            SEARCH_CLIENT_INITIALIZE,
            ConfigDef.Type.BOOLEAN,
            SEARCH_CLIENT_INITIALIZE_DEFAULT,
            ConfigDef.Importance.LOW,
            SEARCH_CLIENT_INITIALIZE_DOC
        ).define(
            SEARCH_HOST,
            ConfigDef.Type.STRING,
            SEARCH_HOST_DEFAULT,
            ConfigDef.Importance.HIGH,
            SEARCH_HOST_DOC
        ).define(
            SEARCH_PORT,
            ConfigDef.Type.INT,
            SEARCH_PORT_DEFAULT,
            ConfigDef.Importance.HIGH,
            SEARCH_PORT_DOC
        ).define(
            SEARCH_USERNAME,
            ConfigDef.Type.STRING,
            SEARCH_USERNAME_DEFAULT,
            ConfigDef.Importance.MEDIUM, 
            SEARCH_USERNAME_DOC
        ).define(
            SEARCH_PASSWORD,
            ConfigDef.Type.STRING,
            SEARCH_PASSWORD_DEFAULT,
            ConfigDef.Importance.HIGH,
            SEARCH_PASSWORD_DOC
        ).define(
            SEARCH_CACHE_NAME,
            ConfigDef.Type.STRING,
            SEARCH_CACHE_NAME_DEFAULT,
            ConfigDef.Importance.LOW,
            SEARCH_CACHE_NAME_DOC
        ).define(
            SEARCH_REALM,
            ConfigDef.Type.STRING,
            SEARCH_REALM_DEFAULT,
            ConfigDef.Importance.LOW,
            SEARCH_REALM_DOC
        ).define(
            SEARCH_SERVER_NAME,
            ConfigDef.Type.STRING,
            SEARCH_SERVER_NAME_DEFAULT,
            ConfigDef.Importance.LOW,
            SEARCH_SERVER_NAME_DOC
        ).define(
            SEARCH_CLIENT_INTELLIGENCE,
            ConfigDef.Type.STRING,
            SEARCH_CLIENT_INTELLIGENCE_DEFAULT,
            ConfigDef.Importance.MEDIUM,
            SEARCH_CLIENT_INTELLIGENCE_DOC
        );

        return configDef;
    }

    public SearchSinkConnectorConfig(Map<?, ?> originals) {
        super(config(), originals);
    }
}
