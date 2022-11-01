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

package io.apicurio.registry.rest.client.request.provider;

/**
 * @author Carles Arnal 'carnalca@redhat.com'
 */
public class Routes {

    protected static final String GROUPS_BASE_PATH = "groups";
    protected static final String GROUP_BASE_PATH = "groups/%s";
    protected static final String ARTIFACT_GROUPS_BASE_PATH = GROUP_BASE_PATH + "/artifacts";
    protected static final String ARTIFACT_BASE_PATH = ARTIFACT_GROUPS_BASE_PATH + "/%s";
    protected static final String IDS_BASE_PATH = "ids";
    protected static final String ADMIN_BASE_PATH = "admin";
    protected static final String SEARCH_BASE_PATH = "search";
    protected static final String USERS_BASE_PATH = "users";
    protected static final String SEARCH_ARTIFACTS = SEARCH_BASE_PATH + "/artifacts";

    protected static final String ARTIFACT_RULES = ARTIFACT_BASE_PATH + "/rules";
    protected static final String ARTIFACT_VERSIONS = ARTIFACT_BASE_PATH + "/versions";
    protected static final String ARTIFACT_VERSION = ARTIFACT_VERSIONS + "/%s";
    protected static final String ARTIFACT_VERSION_REFERENCES = ARTIFACT_VERSION + "/references";


    protected static final String ARTIFACT_METADATA = ARTIFACT_BASE_PATH + "/meta";
    protected static final String ARTIFACT_RULE = ARTIFACT_RULES + "/%s";
    protected static final String ARTIFACT_STATE = ARTIFACT_BASE_PATH + "/state";
    protected static final String ARTIFACT_TEST = ARTIFACT_BASE_PATH + "/test";
    protected static final String ARTIFACT_OWNER = ARTIFACT_BASE_PATH + "/owner";

    protected static final String VERSION_METADATA = ARTIFACT_VERSION + "/meta";
    protected static final String VERSION_STATE = ARTIFACT_VERSION + "/state";

    protected static final String IDS_CONTENT_ID = IDS_BASE_PATH + "/contentIds/%s";
    protected static final String IDS_CONTENT_HASH = IDS_BASE_PATH + "/contentHashes/%s";
    protected static final String IDS_GLOBAL_ID = IDS_BASE_PATH + "/globalIds/%s";

    protected static final String IDS_REFERENCES_CONTENT_ID = IDS_BASE_PATH + "/contentIds/%s/references";
    protected static final String IDS_REFERENCES_CONTENT_HASH = IDS_BASE_PATH + "/contentHashes/%s/references";
    protected static final String IDS_REFERENCES_GLOBAL_ID = IDS_BASE_PATH + "/globalIds/%s/references";

    protected static final String RULES_BASE_PATH = ADMIN_BASE_PATH + "/rules";
    protected static final String RULE_PATH = RULES_BASE_PATH + "/%s";

    protected static final String ROLE_MAPPINGS_BASE_PATH = ADMIN_BASE_PATH + "/roleMappings";
    protected static final String ROLE_MAPPING_PATH = ROLE_MAPPINGS_BASE_PATH + "/%s";

    protected static final String CONFIG_PROPERTIES_BASE_PATH = ADMIN_BASE_PATH + "/config/properties";
    protected static final String CONFIG_PROPERTY_PATH = CONFIG_PROPERTIES_BASE_PATH + "/%s";

    protected static final String LOGS_BASE_PATH = ADMIN_BASE_PATH + "/loggers";
    protected static final String LOG_PATH = LOGS_BASE_PATH + "/%s";

    protected static final String CURRENT_USER_PATH = USERS_BASE_PATH + "/me";

    protected static final String EXPORT_PATH = ADMIN_BASE_PATH + "/export";
    protected static final String IMPORT_PATH = ADMIN_BASE_PATH + "/import";
}
