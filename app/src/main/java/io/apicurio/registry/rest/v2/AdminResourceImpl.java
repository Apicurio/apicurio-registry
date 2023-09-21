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

package io.apicurio.registry.rest.v2;

import java.io.InputStream;
import java.util.List;

import io.apicurio.common.apps.logging.Logged;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.rest.v2.beans.ArtifactTypeInfo;
import io.apicurio.registry.rest.v2.beans.ConfigurationProperty;
import io.apicurio.registry.rest.v2.beans.LogConfiguration;
import io.apicurio.registry.rest.v2.beans.NamedLogConfiguration;
import io.apicurio.registry.rest.v2.beans.RoleMapping;
import io.apicurio.registry.rest.v2.beans.Rule;
import io.apicurio.registry.rest.v2.beans.UpdateConfigurationProperty;
import io.apicurio.registry.rest.v2.beans.UpdateRole;
import io.apicurio.registry.types.RuleType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptors;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.core.Response;

/**
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
@Logged
public class AdminResourceImpl extends AbstractResourceImpl implements AdminResource {
    
    @Inject
    io.apicurio.registry.rest.v3.AdminResourceImpl v3Impl;

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#listArtifactTypes()
     */
    @Override
    public List<ArtifactTypeInfo> listArtifactTypes() {
        return V2ApiUtil.fromV3_ArtifactTypeInfoList(v3Impl.listArtifactTypes());
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#listGlobalRules()
     */
    @Override
    public List<RuleType> listGlobalRules() {
        return v3Impl.listGlobalRules();
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#createGlobalRule(io.apicurio.registry.rest.v2.beans.Rule)
     */
    @Override
    public void createGlobalRule(@NotNull Rule data) {
        v3Impl.createGlobalRule(V2ApiUtil.toV3(data));
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#deleteAllGlobalRules()
     */
    @Override
    public void deleteAllGlobalRules() {
        v3Impl.deleteAllGlobalRules();
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#getGlobalRuleConfig(io.apicurio.registry.types.RuleType)
     */
    @Override
    public Rule getGlobalRuleConfig(RuleType rule) {
        return V2ApiUtil.fromV3(v3Impl.getGlobalRuleConfig(rule));
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#updateGlobalRuleConfig(io.apicurio.registry.types.RuleType, io.apicurio.registry.rest.v2.beans.Rule)
     */
    @Override
    public Rule updateGlobalRuleConfig(RuleType rule, @NotNull Rule data) {
        return V2ApiUtil.fromV3(v3Impl.updateGlobalRuleConfig(rule, V2ApiUtil.toV3(data)));
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#deleteGlobalRule(io.apicurio.registry.types.RuleType)
     */
    @Override
    public void deleteGlobalRule(RuleType rule) {
        v3Impl.deleteGlobalRule(rule);
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#listLogConfigurations()
     */
    @Override
    public List<NamedLogConfiguration> listLogConfigurations() {
        throw new UnsupportedOperationException();
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#getLogConfiguration(java.lang.String)
     */
    @Override
    public NamedLogConfiguration getLogConfiguration(String logger) {
        throw new UnsupportedOperationException();
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#setLogConfiguration(java.lang.String, io.apicurio.registry.rest.v2.beans.LogConfiguration)
     */
    @Override
    public NamedLogConfiguration setLogConfiguration(String logger, @NotNull LogConfiguration data) {
        throw new UnsupportedOperationException();
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#removeLogConfiguration(java.lang.String)
     */
    @Override
    public NamedLogConfiguration removeLogConfiguration(String logger) {
        throw new UnsupportedOperationException();
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#exportData(java.lang.Boolean)
     */
    @Override
    public Response exportData(Boolean forBrowser) {
        requireHttpServletRequest();
        return v3Impl.exportData(forBrowser);
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#importData(java.lang.Boolean, java.lang.Boolean, java.io.InputStream)
     */
    @Override
    public void importData(Boolean xRegistryPreserveGlobalId, Boolean xRegistryPreserveContentId, @NotNull InputStream data) {
        v3Impl.importData(xRegistryPreserveGlobalId, xRegistryPreserveContentId, data);
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#getRoleMapping(java.lang.String)
     */
    @Override
    public RoleMapping getRoleMapping(String principalId) {
        return V2ApiUtil.fromV3(v3Impl.getRoleMapping(principalId));
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#updateRoleMapping(java.lang.String, io.apicurio.registry.rest.v2.beans.UpdateRole)
     */
    @Override
    public void updateRoleMapping(String principalId, @NotNull UpdateRole data) {
        v3Impl.updateRoleMapping(principalId, V2ApiUtil.toV3(data));
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#deleteRoleMapping(java.lang.String)
     */
    @Override
    public void deleteRoleMapping(String principalId) {
        v3Impl.deleteRoleMapping(principalId);
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#listRoleMappings()
     */
    @Override
    public List<RoleMapping> listRoleMappings() {
        return V2ApiUtil.fromV3_RoleMappingList(v3Impl.listRoleMappings());
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#createRoleMapping(io.apicurio.registry.rest.v2.beans.RoleMapping)
     */
    @Override
    public void createRoleMapping(@NotNull RoleMapping data) {
        v3Impl.createRoleMapping(V2ApiUtil.toV3(data));
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#listConfigProperties()
     */
    @Override
    public List<ConfigurationProperty> listConfigProperties() {
        return V2ApiUtil.fromV3_ConfigurationPropertyList(v3Impl.listConfigProperties());
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#getConfigProperty(java.lang.String)
     */
    @Override
    public ConfigurationProperty getConfigProperty(String propertyName) {
        return V2ApiUtil.fromV3(v3Impl.getConfigProperty(propertyName));
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#updateConfigProperty(java.lang.String, io.apicurio.registry.rest.v2.beans.UpdateConfigurationProperty)
     */
    @Override
    public void updateConfigProperty(String propertyName, @NotNull UpdateConfigurationProperty data) {
        v3Impl.updateConfigProperty(propertyName, V2ApiUtil.toV3(data));
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#resetConfigProperty(java.lang.String)
     */
    @Override
    public void resetConfigProperty(String propertyName) {
        v3Impl.resetConfigProperty(propertyName);
    }

}
