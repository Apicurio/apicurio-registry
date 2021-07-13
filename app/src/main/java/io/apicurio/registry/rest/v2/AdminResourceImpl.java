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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.interceptor.Interceptors;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import org.slf4j.Logger;

import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.auth.RoleBasedAccessApiOperation;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.rest.MissingRequiredParameterException;
import io.apicurio.registry.rest.v2.beans.LogConfiguration;
import io.apicurio.registry.rest.v2.beans.NamedLogConfiguration;
import io.apicurio.registry.rest.v2.beans.RoleMapping;
import io.apicurio.registry.rest.v2.beans.Rule;
import io.apicurio.registry.rest.v2.beans.UpdateRole;
import io.apicurio.registry.rules.DefaultRuleDeletionException;
import io.apicurio.registry.rules.RulesProperties;
import io.apicurio.registry.services.LogConfigurationService;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.RuleNotFoundException;
import io.apicurio.registry.storage.dto.RoleMappingDto;
import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.storage.impexp.EntityInputStream;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.types.RoleType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.impexp.Entity;
import io.apicurio.registry.utils.impexp.EntityReader;
import io.apicurio.registry.utils.impexp.EntityWriter;

/**
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
@Logged
public class AdminResourceImpl implements AdminResource {

    @Inject
    Logger log;

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    RulesProperties rulesProperties;

    @Inject
    LogConfigurationService logConfigService;

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#listGlobalRules()
     */
    @Override
    @Authorized(style=AuthorizedStyle.None, level=AuthorizedLevel.Admin)
    public List<RuleType> listGlobalRules() {
        List<RuleType> rules = storage.getGlobalRules();
        List<RuleType> defaultRules = rulesProperties.getFilteredDefaultGlobalRules(rules);
        return Stream.concat(rules.stream(), defaultRules.stream())
            .sorted()
            .collect(Collectors.toList());
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#createGlobalRule(io.apicurio.registry.rest.v2.beans.Rule)
     */
    @Override
    @Authorized(style=AuthorizedStyle.None, level=AuthorizedLevel.Admin)
    public void createGlobalRule(Rule data) {
        RuleConfigurationDto configDto = new RuleConfigurationDto();
        configDto.setConfiguration(data.getConfig());
        storage.createGlobalRule(data.getType(), configDto);
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#deleteAllGlobalRules()
     */
    @Override
    @Authorized(style=AuthorizedStyle.None, level=AuthorizedLevel.Admin)
    public void deleteAllGlobalRules() {
        storage.deleteGlobalRules();
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#getGlobalRuleConfig(io.apicurio.registry.types.RuleType)
     */
    @Override
    @Authorized(style=AuthorizedStyle.None, level=AuthorizedLevel.Admin)
    public Rule getGlobalRuleConfig(RuleType rule) {
        RuleConfigurationDto dto;
        try {
            dto = storage.getGlobalRule(rule);
        } catch (RuleNotFoundException ruleNotFoundException) {
            // Check if the rule exists in the default global rules
            dto = rulesProperties.getDefaultGlobalRuleConfiguration(rule);
            if (dto == null) {
                throw ruleNotFoundException;
            }
        }
        Rule ruleBean = new Rule();
        ruleBean.setType(rule);
        ruleBean.setConfig(dto.getConfiguration());
        return ruleBean;
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#updateGlobalRuleConfig(io.apicurio.registry.types.RuleType, io.apicurio.registry.rest.v2.beans.Rule)
     */
    @Override
    @Authorized(style=AuthorizedStyle.None, level=AuthorizedLevel.Admin)
    public Rule updateGlobalRuleConfig(RuleType rule, Rule data) {
        RuleConfigurationDto configDto = new RuleConfigurationDto();
        configDto.setConfiguration(data.getConfig());
        try {
            storage.updateGlobalRule(rule, configDto);
        } catch (RuleNotFoundException ruleNotFoundException) {
            // This global rule doesn't exist in artifactStore - if the rule exists in the default
            // global rules, override the default by creating a new global rule
            if (rulesProperties.isDefaultGlobalRuleConfigured(rule)) {
                storage.createGlobalRule(rule, configDto);
            } else {
                throw ruleNotFoundException;
            }
        }
        Rule ruleBean = new Rule();
        ruleBean.setType(rule);
        ruleBean.setConfig(data.getConfig());
        return ruleBean;
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#deleteGlobalRule(io.apicurio.registry.types.RuleType)
     */
    @Override
    @Authorized(style=AuthorizedStyle.None, level=AuthorizedLevel.Admin)
    public void deleteGlobalRule(RuleType rule) {
        try {
            storage.deleteGlobalRule(rule);
        } catch (RuleNotFoundException ruleNotFoundException) {
            // This global rule doesn't exist in artifactStore - if the rule exists in
            // the default global rules, return a DefaultRuleDeletionException.
            // Otherwise, return the RuleNotFoundException
            if (rulesProperties.isDefaultGlobalRuleConfigured(rule)) {
                throw new DefaultRuleDeletionException(rule);
            } else {
                throw ruleNotFoundException;
            }
        }
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#getLogConfiguration(java.lang.String)
     */
    @Override
    @Authorized(style=AuthorizedStyle.None, level=AuthorizedLevel.Admin)
    public NamedLogConfiguration getLogConfiguration(String logger) {
        return logConfigService.getLogConfiguration(logger);
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#listLogConfigurations()
     */
    @Override
    @Authorized(style=AuthorizedStyle.None, level=AuthorizedLevel.Admin)
    public List<NamedLogConfiguration> listLogConfigurations() {
        return logConfigService.listLogConfigurations();
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#removeLogConfiguration(java.lang.String)
     */
    @Override
    @Authorized(style=AuthorizedStyle.None, level=AuthorizedLevel.Admin)
    public NamedLogConfiguration removeLogConfiguration(String logger) {
        return logConfigService.removeLogLevelConfiguration(logger);
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#setLogConfiguration(java.lang.String, io.apicurio.registry.rest.v2.beans.LogConfiguration)
     */
    @Override
    @Authorized(style=AuthorizedStyle.None, level=AuthorizedLevel.Admin)
    public NamedLogConfiguration setLogConfiguration(String logger, LogConfiguration data) {
        if (data.getLevel() == null) {
            throw new MissingRequiredParameterException("logLevel");
        }
        return logConfigService.setLogLevel(logger, data.getLevel());
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#importData(java.io.InputStream)
     */
    @Override
    @Authorized(style=AuthorizedStyle.None, level=AuthorizedLevel.Admin)
    public void importData(InputStream data) {
        final ZipInputStream zip = new ZipInputStream(data, StandardCharsets.UTF_8);
        final EntityReader reader = new EntityReader(zip);
        EntityInputStream stream = new EntityInputStream() {
            @Override
            public Entity nextEntity() throws IOException {
                try {
                    return reader.readEntity();
                } catch (Exception e) {
                    log.error("Error reading data from import ZIP file.", e);
                    return null;
                }
            }

            @Override
            public void close() throws IOException {
                zip.close();
            }
        };
        this.storage.importData(stream);
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#exportData()
     */
    @Override
    @Authorized(style=AuthorizedStyle.None, level=AuthorizedLevel.Admin)
    public Response exportData() {
        StreamingOutput stream = new StreamingOutput() {
            @Override
            public void write(OutputStream os) throws IOException, WebApplicationException {
                try {
                    ZipOutputStream zip = new ZipOutputStream(os, StandardCharsets.UTF_8);
                    EntityWriter writer = new EntityWriter(zip);
                    AtomicInteger errorCounter = new AtomicInteger(0);
                    storage.exportData(entity -> {
                        try {
                            writer.writeEntity(entity);
                        } catch (Exception e) {
                            // TODO do something interesting with this
                            e.printStackTrace();
                            errorCounter.incrementAndGet();
                        }
                        return null;
                    });

                    // TODO if the errorCounter > 0, then what?

                    zip.flush();
                    zip.close();
                } catch (IOException e) {
                    throw e;
                } catch (Exception e) {
                    throw new IOException(e);
                }
            }
        };

        return Response.ok(stream).type("application/zip").build();
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#createRoleMapping(io.apicurio.registry.rest.v2.beans.RoleMapping)
     */
    @Override
    @Authorized(style=AuthorizedStyle.None, level=AuthorizedLevel.Admin)
    @RoleBasedAccessApiOperation
    public void createRoleMapping(RoleMapping data) {
        storage.createRoleMapping(data.getPrincipalId(), data.getRole().name());
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#listRoleMappings()
     */
    @Override
    @Authorized(style=AuthorizedStyle.None, level=AuthorizedLevel.Admin)
    @RoleBasedAccessApiOperation
    public List<RoleMapping> listRoleMappings() {
        List<RoleMappingDto> mappings = storage.getRoleMappings();
        return mappings.stream().map(dto -> {
            return dtoToRoleMapping(dto);
        }).collect(Collectors.toList());
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#getRoleMapping(java.lang.String)
     */
    @Override
    @Authorized(style=AuthorizedStyle.None, level=AuthorizedLevel.Admin)
    @RoleBasedAccessApiOperation
    public RoleMapping getRoleMapping(String principalId) {
        RoleMappingDto dto = storage.getRoleMapping(principalId);
        return dtoToRoleMapping(dto);
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#updateRoleMapping(java.lang.String, io.apicurio.registry.rest.v2.beans.Role)
     */
    @Override
    @Authorized(style=AuthorizedStyle.None, level=AuthorizedLevel.Admin)
    @RoleBasedAccessApiOperation
    public void updateRoleMapping(String principalId, UpdateRole data) {
        storage.updateRoleMapping(principalId, data.getRole().name());
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#deleteRoleMapping(java.lang.String)
     */
    @Override
    @Authorized(style=AuthorizedStyle.None, level=AuthorizedLevel.Admin)
    @RoleBasedAccessApiOperation
    public void deleteRoleMapping(String principalId) {
        storage.deleteRoleMapping(principalId);
    }

    private static RoleMapping dtoToRoleMapping(RoleMappingDto dto) {
        RoleMapping mapping = new RoleMapping();
        mapping.setPrincipalId(dto.getPrincipalId());
        mapping.setRole(RoleType.valueOf(dto.getRole()));
        return mapping;
    }

}
