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

package io.apicurio.registry.rest.client.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.apicurio.registry.rest.client.AdminClient;
import io.apicurio.registry.rest.client.exception.RestClientException;
import io.apicurio.registry.rest.client.request.provider.AdminRequestsProvider;
import io.apicurio.registry.rest.v2.beans.*;
import io.apicurio.registry.rest.v2.beans.Error;
import io.apicurio.registry.types.RoleType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.rest.client.spi.ApicurioHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.List;

/**
 * @author Jonathan Hughes 'jonathan.hughes@ibm.com'
 */

public class AdminClientImpl implements AdminClient {

    private final ApicurioHttpClient apicurioHttpClient;
    private static final Logger logger = LoggerFactory.getLogger(RegistryClientImpl.class);

    public AdminClientImpl(ApicurioHttpClient apicurioHttpClient) {
        this.apicurioHttpClient = apicurioHttpClient;
    }

    @Override
    public void createGlobalRule(Rule data) {
        try {
            apicurioHttpClient.sendRequest(AdminRequestsProvider.createGlobalRule(data));
        } catch (JsonProcessingException e) {
            throw parseSerializationError(e);
        }
    }

    @Override
    public List<RuleType> listGlobalRules() {
        return apicurioHttpClient.sendRequest(AdminRequestsProvider.listGlobalRules());
    }

    @Override
    public void deleteGlobalRule(RuleType rule) {
        apicurioHttpClient.sendRequest(AdminRequestsProvider.deleteGlobalRule(rule));
    }

    @Override
    public void deleteAllGlobalRules() {
        apicurioHttpClient.sendRequest(AdminRequestsProvider.deleteAllGlobalRules());
    }

    @Override
    public Rule getGlobalRuleConfig(RuleType rule) {
        return apicurioHttpClient.sendRequest(AdminRequestsProvider.getGlobalRule(rule));
    }

    @Override
    public Rule updateGlobalRuleConfig(RuleType rule, Rule data) {
        try {
            return apicurioHttpClient.sendRequest(AdminRequestsProvider.updateGlobalRuleConfig(rule, data));
        } catch (JsonProcessingException e) {
            throw parseSerializationError(e);
        }
    }

    @Override
    public NamedLogConfiguration setLogConfiguration(String logger, LogConfiguration data) {
        try {
            return apicurioHttpClient.sendRequest(AdminRequestsProvider.setLogConfiguration(logger, data));
        } catch (JsonProcessingException e) {
            throw parseSerializationError(e);
        }
    }

    @Override
    public NamedLogConfiguration getLogConfiguration(String logger) {
        return apicurioHttpClient.sendRequest(AdminRequestsProvider.getLogConfiguration(logger));
    }

    @Override
    public List<NamedLogConfiguration> listLogConfigurations() {
        return apicurioHttpClient.sendRequest(AdminRequestsProvider.listLogConfigurations());
    }

    @Override
    public NamedLogConfiguration removeLogConfiguration(String logger) {
        return apicurioHttpClient.sendRequest(AdminRequestsProvider.removeLogConfiguration(logger));
    }

    @Override
    public void createRoleMapping(RoleMapping data) {
        try {
            apicurioHttpClient.sendRequest(AdminRequestsProvider.createRoleMapping(data));
        } catch (JsonProcessingException e) {
            throw parseSerializationError(e);
        }
    }

    @Override
    public RoleMapping getRoleMapping(String principalId) {
        return apicurioHttpClient.sendRequest(AdminRequestsProvider.getRoleMapping(principalId));
    }
    @Override
    public List<RoleMapping> listRoleMappings() {
        return apicurioHttpClient.sendRequest(AdminRequestsProvider.listRoleMappings());
    }

    @Override
    public void updateRoleMapping(String principalId, RoleType role) {
        try {
            apicurioHttpClient.sendRequest(AdminRequestsProvider.updateRoleMapping(principalId, role));
        } catch (JsonProcessingException e) {
            throw parseSerializationError(e);
        }
    }

    @Override
    public void deleteRoleMapping(String principalId) {
        apicurioHttpClient.sendRequest(AdminRequestsProvider.deleteRoleMapping(principalId));
    }

    @Override
    public InputStream exportData() {
        return apicurioHttpClient.sendRequest(AdminRequestsProvider.exportData());
    }

    @Override
    public void importData(InputStream data) {
        apicurioHttpClient.sendRequest(AdminRequestsProvider.importData(data, true, true));
    }

    @Override
    public void importData(InputStream data, boolean preserveGlobalIds, boolean preserveContentIds) {
        apicurioHttpClient.sendRequest(AdminRequestsProvider.importData(data, preserveGlobalIds, preserveContentIds));
    }


    private static RestClientException parseSerializationError(JsonProcessingException ex) {
        final Error error = new Error();
        error.setName(ex.getClass().getSimpleName());
        error.setMessage(ex.getMessage());
        logger.debug("Error serializing request response", ex);
        return new RestClientException(error);
    }

    @Override
    public void close() {
        apicurioHttpClient.close();
    }
}
