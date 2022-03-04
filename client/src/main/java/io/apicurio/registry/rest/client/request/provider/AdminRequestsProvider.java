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


import static io.apicurio.registry.rest.client.request.provider.Routes.CONFIG_PROPERTIES_BASE_PATH;
import static io.apicurio.registry.rest.client.request.provider.Routes.CONFIG_PROPERTY_PATH;
import static io.apicurio.registry.rest.client.request.provider.Routes.EXPORT_PATH;
import static io.apicurio.registry.rest.client.request.provider.Routes.IMPORT_PATH;
import static io.apicurio.registry.rest.client.request.provider.Routes.LOGS_BASE_PATH;
import static io.apicurio.registry.rest.client.request.provider.Routes.LOG_PATH;
import static io.apicurio.registry.rest.client.request.provider.Routes.ROLE_MAPPINGS_BASE_PATH;
import static io.apicurio.registry.rest.client.request.provider.Routes.ROLE_MAPPING_PATH;
import static io.apicurio.registry.rest.client.request.provider.Routes.RULES_BASE_PATH;
import static io.apicurio.registry.rest.client.request.provider.Routes.RULE_PATH;
import static io.apicurio.rest.client.request.Operation.DELETE;
import static io.apicurio.rest.client.request.Operation.GET;
import static io.apicurio.rest.client.request.Operation.POST;
import static io.apicurio.rest.client.request.Operation.PUT;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.apicurio.registry.rest.Headers;
import io.apicurio.registry.rest.v2.beans.ConfigurationProperty;
import io.apicurio.registry.rest.v2.beans.LogConfiguration;
import io.apicurio.registry.rest.v2.beans.NamedLogConfiguration;
import io.apicurio.registry.rest.v2.beans.RoleMapping;
import io.apicurio.registry.rest.v2.beans.Rule;
import io.apicurio.registry.rest.v2.beans.UpdateConfigurationProperty;
import io.apicurio.registry.rest.v2.beans.UpdateRole;
import io.apicurio.registry.types.RoleType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.rest.client.request.Request;


/**
 * @author Carles Arnal 'carnalca@redhat.com'
 */
public class AdminRequestsProvider {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static Request<NamedLogConfiguration> removeLogConfiguration(String logger) {
        return new Request.RequestBuilder<NamedLogConfiguration>()
                .operation(DELETE)
                .path(Routes.LOG_PATH)
                .pathParams(List.of(logger))
                .responseType(new TypeReference<NamedLogConfiguration>() {
                })
                .build();
    }

    public static Request<NamedLogConfiguration> setLogConfiguration(String logger, LogConfiguration data) throws JsonProcessingException {
        return new Request.RequestBuilder<NamedLogConfiguration>()
                .operation(PUT)
                .path(LOG_PATH)
                .pathParams(List.of(logger))
                .responseType(new TypeReference<NamedLogConfiguration>() {
                })
                .data(IoUtil.toStream(mapper.writeValueAsBytes(data)))
                .build();
    }

    public static Request<NamedLogConfiguration> getLogConfiguration(String logger) {

        return new Request.RequestBuilder<NamedLogConfiguration>()
                .operation(GET)
                .path(LOG_PATH)
                .pathParams(List.of(logger))
                .responseType(new TypeReference<NamedLogConfiguration>() {
                })
                .build();
    }

    public static Request<List<NamedLogConfiguration>> listLogConfigurations() {
        return new Request.RequestBuilder<List<NamedLogConfiguration>>()
                .operation(GET)
                .path(LOGS_BASE_PATH)
                .responseType(new TypeReference<List<NamedLogConfiguration>>() {
                })
                .build();
    }

    public static Request<Void> deleteGlobalRule(RuleType rule) {
        return new Request.RequestBuilder<Void>()
                .operation(DELETE)
                .path(RULE_PATH)
                .pathParams(List.of(rule.value()))
                .responseType(new TypeReference<Void>() {
                })
                .build();
    }

    public static Request<Rule> updateGlobalRuleConfig(RuleType rule, Rule data) throws JsonProcessingException {
        return new Request.RequestBuilder<Rule>()
                .operation(PUT)
                .path(RULE_PATH)
                .pathParams(List.of(rule.value()))
                .responseType(new TypeReference<Rule>() {
                })
                .data(IoUtil.toStream(mapper.writeValueAsBytes(data)))
                .build();
    }

    public static Request<Rule> getGlobalRule(RuleType rule) {
        return new Request.RequestBuilder<Rule>()
                .operation(GET)
                .path(RULE_PATH)
                .pathParams(List.of(rule.value()))
                .responseType(new TypeReference<Rule>() {
                })
                .build();
    }

    public static Request<Void> deleteAllGlobalRules() {
        return new Request.RequestBuilder<Void>()
                .operation(DELETE)
                .path(RULES_BASE_PATH)
                .responseType(new TypeReference<Void>() {
                })
                .build();
    }

    public static Request<Void> createGlobalRule(Rule data) throws JsonProcessingException {
        return new Request.RequestBuilder<Void>()
                .operation(POST)
                .path(RULES_BASE_PATH)
                .data(IoUtil.toStream(mapper.writeValueAsBytes(data)))
                .responseType(new TypeReference<Void>() {})
                .build();
    }

    public static Request<List<RuleType>> listGlobalRules() {
        return new Request.RequestBuilder<List<RuleType>>()
                .operation(GET)
                .path(RULES_BASE_PATH)
                .responseType(new TypeReference<List<RuleType>>() {})
                .build();
    }

    public static Request<InputStream> exportData() {
        return new Request.RequestBuilder<InputStream>()
                .operation(GET)
                .path(EXPORT_PATH)
                .responseType(new TypeReference<InputStream>() {
                })
                .headers(new HashMap<>(Map.of(Request.ACCEPT, "application/zip")))
                .build();
    }

    public static Request<Void> importData(InputStream data, boolean preserveGlobalIds, boolean preserveContentIds) {
        return new Request.RequestBuilder<Void>()
                .operation(POST)
                .path(IMPORT_PATH)
                .responseType(new TypeReference<Void>() {})
                .data(data)
                .headers(new HashMap<>(Map.of(Request.CONTENT_TYPE, "application/zip", Headers.PRESERVE_GLOBAL_ID, Boolean.toString(preserveGlobalIds), Headers.PRESERVE_CONTENT_ID, Boolean.toString(preserveContentIds))))
                .build();
    }

    public static Request<Void> createRoleMapping(RoleMapping data) throws JsonProcessingException {
        return new Request.RequestBuilder<Void>()
                .operation(POST)
                .path(ROLE_MAPPINGS_BASE_PATH)
                .data(IoUtil.toStream(mapper.writeValueAsBytes(data)))
                .responseType(new TypeReference<Void>() {})
                .build();
    }

    public static Request<List<RoleMapping>> listRoleMappings() {
        return new Request.RequestBuilder<List<RoleMapping>>()
                .operation(GET)
                .path(ROLE_MAPPINGS_BASE_PATH)
                .responseType(new TypeReference<List<RoleMapping>>() {})
                .build();
    }

    public static Request<Void> updateRoleMapping(String principalId, RoleType role) throws JsonProcessingException {
        UpdateRole update = new UpdateRole();
        update.setRole(role);
        return new Request.RequestBuilder<Void>()
                .operation(PUT)
                .path(ROLE_MAPPING_PATH)
                .pathParams(List.of(principalId))
                .responseType(new TypeReference<Void>() {
                })
                .data(IoUtil.toStream(mapper.writeValueAsBytes(update)))
                .build();
    }

    public static Request<Void> deleteRoleMapping(String principalId) {
        return new Request.RequestBuilder<Void>()
                .operation(DELETE)
                .path(ROLE_MAPPING_PATH)
                .pathParams(List.of(principalId))
                .responseType(new TypeReference<Void>() {
                })
                .build();
    }

    public static Request<RoleMapping> getRoleMapping(String principalId) {
        return new Request.RequestBuilder<RoleMapping>()
                .operation(GET)
                .path(ROLE_MAPPING_PATH)
                .pathParams(List.of(principalId))
                .responseType(new TypeReference<RoleMapping>() {
                })
                .build();
    }

    public static Request<List<ConfigurationProperty>> listConfigProperties() {
        return new Request.RequestBuilder<List<ConfigurationProperty>>()
                .operation(GET)
                .path(CONFIG_PROPERTIES_BASE_PATH)
                .responseType(new TypeReference<List<ConfigurationProperty>>() {})
                .build();
    }

    public static Request<ConfigurationProperty> getConfigProperty(String propertyName) {
        return new Request.RequestBuilder<ConfigurationProperty>()
                .operation(GET)
                .path(CONFIG_PROPERTY_PATH)
                .pathParams(List.of(propertyName))
                .responseType(new TypeReference<ConfigurationProperty>() {
                })
                .build();
   }

    public static Request<Void> setConfigProperty(String propertyName, String propertyValue) throws JsonProcessingException {
        UpdateConfigurationProperty property = new UpdateConfigurationProperty();
        property.setValue(propertyValue);
        return new Request.RequestBuilder<Void>()
                .operation(PUT)
                .path(CONFIG_PROPERTY_PATH)
                .pathParams(List.of(propertyName))
                .responseType(new TypeReference<Void>() {
                })
                .data(IoUtil.toStream(mapper.writeValueAsBytes(property)))
                .build();
   }

    public static Request<Void> deleteConfigProperty(String propertyName) {
        return new Request.RequestBuilder<Void>()
                .operation(DELETE)
                .path(CONFIG_PROPERTY_PATH)
                .pathParams(List.of(propertyName))
                .responseType(new TypeReference<Void>() {
                })
                .build();
   }

}