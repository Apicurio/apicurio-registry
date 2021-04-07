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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.rest.client.request.ErrorHandler;
import io.apicurio.registry.rest.client.request.Request;
import io.apicurio.registry.rest.v2.beans.LogConfiguration;
import io.apicurio.registry.rest.v2.beans.NamedLogConfiguration;
import io.apicurio.registry.rest.v2.beans.Rule;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.IoUtil;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.apicurio.registry.rest.client.request.provider.Operation.DELETE;
import static io.apicurio.registry.rest.client.request.provider.Operation.GET;
import static io.apicurio.registry.rest.client.request.provider.Operation.POST;
import static io.apicurio.registry.rest.client.request.provider.Operation.PUT;
import static io.apicurio.registry.rest.client.request.provider.Routes.LOGS_BASE_PATH;
import static io.apicurio.registry.rest.client.request.provider.Routes.LOG_PATH;
import static io.apicurio.registry.rest.client.request.provider.Routes.RULES_BASE_PATH;
import static io.apicurio.registry.rest.client.request.provider.Routes.RULE_PATH;
import static io.apicurio.registry.rest.client.request.provider.Routes.EXPORT_PATH;
import static io.apicurio.registry.rest.client.request.provider.Routes.IMPORT_PATH;


/**
 * @author Carles Arnal 'carnalca@redhat.com'
 */
public class AdminRequestsProvider {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static Request<NamedLogConfiguration> removeLogConfiguration(String logger) {

        return new Request.RequestBuilder<NamedLogConfiguration>()
                .operation(Operation.DELETE)
                .path(Routes.LOG_PATH)
                .pathParams(List.of(logger))
                .responseType(new TypeReference<NamedLogConfiguration>(){})
                .build();
    }

    public static Request<NamedLogConfiguration> setLogConfiguration(String logger, LogConfiguration data) {

        try {
            return new Request.RequestBuilder<NamedLogConfiguration>()
                    .operation(PUT)
                    .path(LOG_PATH)
                    .pathParams(List.of(logger))
                    .responseType(new TypeReference<NamedLogConfiguration>(){})
                    .data(IoUtil.toStream(mapper.writeValueAsBytes(data)))
                    .build();
        } catch (JsonProcessingException e) {
            throw ErrorHandler.parseInputSerializingError(e);
        }
    }

    public static Request<NamedLogConfiguration> getLogConfiguration(String logger) {

        return new Request.RequestBuilder<NamedLogConfiguration>()
                .operation(GET)
                .path(LOG_PATH)
                .pathParams(List.of(logger))
                .responseType(new TypeReference<NamedLogConfiguration>(){})
                .build();
    }

    public static Request<List<NamedLogConfiguration>> listLogConfigurations() {
        return new Request.RequestBuilder<List<NamedLogConfiguration>>()
                .operation(GET)
                .path(LOGS_BASE_PATH)
                .responseType(new TypeReference<List<NamedLogConfiguration>>(){})
                .build();
    }

    public static Request<Void> deleteGlobalRule(RuleType rule) {
        return new Request.RequestBuilder<Void>()
                .operation(DELETE)
                .path(RULE_PATH)
                .pathParams(List.of(rule.value()))
                .responseType(new TypeReference<Void>(){})
                .build();
    }

    public static Request<Rule> updateGlobalRuleConfig(RuleType rule, Rule data) {
        try {
            return new Request.RequestBuilder<Rule>()
                    .operation(PUT)
                    .path(RULE_PATH)
                    .pathParams(List.of(rule.value()))
                    .responseType(new TypeReference<Rule>(){})
                    .data(IoUtil.toStream(mapper.writeValueAsBytes(data)))
                    .build();
        } catch (JsonProcessingException e) {
            throw ErrorHandler.parseInputSerializingError(e);
        }
    }

    public static Request<Rule> getGlobalRule(RuleType rule) {
        return new Request.RequestBuilder<Rule>()
                .operation(GET)
                .path(RULE_PATH)
                .pathParams(List.of(rule.value()))
                .responseType(new TypeReference<Rule>(){})
                .build();
    }

    public static Request<Void> deleteAllGlobalRules() {
        return new Request.RequestBuilder<Void>()
                .operation(DELETE)
                .path(RULES_BASE_PATH)
                .responseType(new TypeReference<Void>(){})
                .build();
    }

    public static Request<Void> createGlobalRule(Rule data) {
        try {
            return new Request.RequestBuilder<Void>()
                    .operation(POST)
                    .path(RULES_BASE_PATH)
                    .data(IoUtil.toStream(mapper.writeValueAsBytes(data)))
                    .responseType(new TypeReference<Void>(){})
                    .build();
        } catch (JsonProcessingException e) {
            throw ErrorHandler.parseInputSerializingError(e);
        }
    }

    public static Request<List<RuleType>> listGlobalRules() {
        return new Request.RequestBuilder<List<RuleType>>()
                .operation(GET)
                .path(RULES_BASE_PATH)
                .responseType(new TypeReference<List<RuleType>>(){})
                .build();
    }

    public static Request<InputStream> exportData() {
        return new Request.RequestBuilder<InputStream>()
                .operation(GET)
                .path(EXPORT_PATH)
                .responseType(new TypeReference<InputStream>(){})
                .headers(new HashMap<>(Map.of(Request.ACCEPT, "application/zip")))
                .build();
    }

    public static Request<Void> importData(InputStream data) {
        return new Request.RequestBuilder<Void>()
                .operation(POST)
                .path(IMPORT_PATH)
                .responseType(new TypeReference<Void>(){})
                .data(data)
                .headers(new HashMap<>(Map.of(Request.CONTENT_TYPE, "application/zip")))
                .build();
    }
}