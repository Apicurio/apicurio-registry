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

import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_FOR_BROWSER;
import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_LOGGER;
import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_LOG_CONFIGURATION;
import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_PRINCIPAL_ID;
import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_ROLE_MAPPING;
import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_RULE;
import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_RULE_TYPE;
import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_UPDATE_ROLE;
import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_NAME;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.interceptor.Interceptors;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import io.apicurio.registry.rest.v2.beans.ArtifactTypeInfo;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

import io.apicurio.common.apps.config.Dynamic;
import io.apicurio.common.apps.config.DynamicConfigPropertyDef;
import io.apicurio.common.apps.config.DynamicConfigPropertyDto;
import io.apicurio.common.apps.config.DynamicConfigPropertyIndex;
import io.apicurio.common.apps.config.Info;
import io.apicurio.common.apps.logging.Logged;
import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.auth.RoleBasedAccessApiOperation;
import io.apicurio.common.apps.logging.audit.Audited;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.rest.MissingRequiredParameterException;
import io.apicurio.registry.rest.v2.beans.ConfigurationProperty;
import io.apicurio.registry.rest.v2.beans.DownloadRef;
import io.apicurio.registry.rest.v2.beans.LogConfiguration;
import io.apicurio.registry.rest.v2.beans.NamedLogConfiguration;
import io.apicurio.registry.rest.v2.beans.RoleMapping;
import io.apicurio.registry.rest.v2.beans.Rule;
import io.apicurio.registry.rest.v2.beans.UpdateConfigurationProperty;
import io.apicurio.registry.rest.v2.beans.UpdateRole;
import io.apicurio.registry.rest.v2.shared.DataExporter;
import io.apicurio.registry.rules.DefaultRuleDeletionException;
import io.apicurio.registry.rules.RulesProperties;
import io.apicurio.registry.services.LogConfigurationService;
import io.apicurio.registry.storage.ConfigPropertyNotFoundException;
import io.apicurio.registry.storage.InvalidPropertyValueException;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.RuleNotFoundException;
import io.apicurio.registry.storage.dto.DownloadContextDto;
import io.apicurio.registry.storage.dto.DownloadContextType;
import io.apicurio.registry.storage.dto.RoleMappingDto;
import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.storage.impexp.EntityInputStream;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.types.RoleType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import io.apicurio.registry.utils.impexp.Entity;
import io.apicurio.registry.utils.impexp.EntityReader;

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

    @Inject
    DynamicConfigPropertyIndex dynamicPropertyIndex;

    @Inject
    ArtifactTypeUtilProviderFactory factory;

    @Inject
    Config config;

    @Inject
    DataExporter exporter;

    @Context
    HttpServletRequest request;

    @Dynamic(label = "Download link expiry", description = "The number of seconds that a generated link to a .zip download file is active before expiring.")
    @ConfigProperty(name = "registry.download.href.ttl", defaultValue = "30")
    @Info(category = "download", description = "Download link expiry", availableSince = "2.1.2.Final")
    Supplier<Long> downloadHrefTtl;

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#listArtifactTypes()
     */
    @Override
    @Authorized(style=AuthorizedStyle.None, level=AuthorizedLevel.Read)
    public List<ArtifactTypeInfo> listArtifactTypes() {
        return factory
                .getAllArtifactTypes()
                .stream()
                .map(t -> {
                    ArtifactTypeInfo ati = new ArtifactTypeInfo();
                    ati.setName(t);
                    return ati;
                })
                .collect(Collectors.toList());

    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#listGlobalRules()
     */
    @Override
    @Authorized(style=AuthorizedStyle.None, level=AuthorizedLevel.Read)
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
    @Audited(extractParameters = {"0", KEY_RULE})
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
    @Audited
    @Authorized(style=AuthorizedStyle.None, level=AuthorizedLevel.Admin)
    public void deleteAllGlobalRules() {
        storage.deleteGlobalRules();
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#getGlobalRuleConfig(io.apicurio.registry.types.RuleType)
     */
    @Override
    @Authorized(style=AuthorizedStyle.None, level=AuthorizedLevel.Read)
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
    @Audited(extractParameters = {"0", KEY_RULE_TYPE, "1", KEY_RULE})
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
    @Audited(extractParameters = {"0", KEY_RULE_TYPE})
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
    @Audited(extractParameters = {"0", KEY_LOGGER})
    @Authorized(style=AuthorizedStyle.None, level=AuthorizedLevel.Admin)
    public NamedLogConfiguration removeLogConfiguration(String logger) {
        return logConfigService.removeLogLevelConfiguration(logger);
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#setLogConfiguration(java.lang.String, io.apicurio.registry.rest.v2.beans.LogConfiguration)
     */
    @Override
    @Audited(extractParameters = {"0", KEY_LOGGER, "1", KEY_LOG_CONFIGURATION})
    @Authorized(style=AuthorizedStyle.None, level=AuthorizedLevel.Admin)
    public NamedLogConfiguration setLogConfiguration(String logger, LogConfiguration data) {
        if (data.getLevel() == null) {
            throw new MissingRequiredParameterException("logLevel");
        }
        return logConfigService.setLogLevel(logger, data.getLevel());
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#importData(Boolean, Boolean, java.io.InputStream)
     */
    @Override
    @Audited
    @Authorized(style=AuthorizedStyle.None, level=AuthorizedLevel.Admin)
    public void importData(Boolean xRegistryPreserveGlobalId, Boolean xRegistryPreserveContentId, InputStream data) {
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
        this.storage.importData(stream, isNullOrTrue(xRegistryPreserveGlobalId), isNullOrTrue(xRegistryPreserveContentId));
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#exportData(java.lang.Boolean)
     */
    @Override
    @Audited(extractParameters = {"0", KEY_FOR_BROWSER})
    @Authorized(style=AuthorizedStyle.None, level=AuthorizedLevel.Admin)
    public Response exportData(Boolean forBrowser) {
        String acceptHeader = request.getHeader("Accept");
        if (Boolean.TRUE.equals(forBrowser) || MediaType.APPLICATION_JSON.equals(acceptHeader)) {
            long expires = System.currentTimeMillis() + (downloadHrefTtl.get() * 1000);
            DownloadContextDto downloadCtx = DownloadContextDto.builder().type(DownloadContextType.EXPORT).expires(expires).build();
            String downloadId = storage.createDownload(downloadCtx);
            String downloadHref = createDownloadHref(downloadId);
            DownloadRef downloadRef = new DownloadRef();
            downloadRef.setDownloadId(downloadId);
            downloadRef.setHref(downloadHref);
            return Response.ok(downloadRef).type(MediaType.APPLICATION_JSON_TYPE).build();
        } else {
            return exporter.exportData();
        }
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#createRoleMapping(io.apicurio.registry.rest.v2.beans.RoleMapping)
     */
    @Override
    @Audited(extractParameters = {"0", KEY_ROLE_MAPPING})
    @Authorized(style=AuthorizedStyle.None, level=AuthorizedLevel.Admin)
    @RoleBasedAccessApiOperation
    public void createRoleMapping(RoleMapping data) {
        storage.createRoleMapping(data.getPrincipalId(), data.getRole().name(), data.getPrincipalName());
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
     * @see io.apicurio.registry.rest.v2.AdminResource#updateRoleMapping (java.lang.String, io.apicurio.registry.rest.v2.beans.Role)
     */
    @Override
    @Audited(extractParameters = {"0", KEY_PRINCIPAL_ID, "1", KEY_UPDATE_ROLE})
    @Authorized(style=AuthorizedStyle.None, level=AuthorizedLevel.Admin)
    @RoleBasedAccessApiOperation
    public void updateRoleMapping(String principalId, UpdateRole data) {
        storage.updateRoleMapping(principalId, data.getRole().name());
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#deleteRoleMapping(java.lang.String)
     */
    @Override
    @Audited(extractParameters = {"0", KEY_PRINCIPAL_ID})
    @Authorized(style=AuthorizedStyle.None, level=AuthorizedLevel.Admin)
    @RoleBasedAccessApiOperation
    public void deleteRoleMapping(String principalId) {
        storage.deleteRoleMapping(principalId);
    }


    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#listConfigProperties()
     */
    @Override
    @Authorized(style=AuthorizedStyle.None, level=AuthorizedLevel.Admin)
    public List<ConfigurationProperty> listConfigProperties() {
        // Query the DB for the set of configured properties.
        List<DynamicConfigPropertyDto> props = storage.getConfigProperties();

        // Index the stored properties for easy lookup
        Map<String, DynamicConfigPropertyDto> propsI = new HashMap<>();
        props.forEach(dto -> propsI.put(dto.getName(), dto));

        // Return value is the set of all dynamic config properties, with either configured or default values (depending
        // on whether the value is actually configured and stored in the DB or not).
        return dynamicPropertyIndex.getAcceptedPropertyNames().stream()
                .sorted((pname1, pname2) -> pname1.compareTo(pname2))
                .map(pname -> propsI.containsKey(pname) ? dtoToConfigurationProperty(dynamicPropertyIndex.getProperty(pname), propsI.get(pname)) : defToConfigurationProperty(dynamicPropertyIndex.getProperty(pname)))
                .collect(Collectors.toList());
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#getConfigProperty(java.lang.String)
     */
    @Override
    @Authorized(style=AuthorizedStyle.None, level=AuthorizedLevel.Admin)
    public ConfigurationProperty getConfigProperty(String propertyName) {
        // Ensure that the property is a valid dynamic config property.
        DynamicConfigPropertyDef def = resolveConfigProperty(propertyName);

        DynamicConfigPropertyDto dto = storage.getRawConfigProperty(propertyName);
        if (dto == null) {
            return defToConfigurationProperty(def);
        } else {
            return dtoToConfigurationProperty(def, dto);
        }
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#updateConfigProperty(java.lang.String, io.apicurio.registry.rest.v2.beans.UpdateConfigurationProperty)
     */
    @Override
    @Authorized(style=AuthorizedStyle.None, level=AuthorizedLevel.Admin)
    public void updateConfigProperty(String propertyName, UpdateConfigurationProperty data) {
        DynamicConfigPropertyDef propertyDef = resolveConfigProperty(propertyName);
        validateConfigPropertyValue(propertyDef, data.getValue());

        DynamicConfigPropertyDto dto = new DynamicConfigPropertyDto();
        dto.setName(propertyName);
        dto.setValue(data.getValue());
        storage.setConfigProperty(dto);
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#resetConfigProperty(java.lang.String)
     */
    @Override
    @Authorized(style=AuthorizedStyle.None, level=AuthorizedLevel.Admin)
    @Audited(extractParameters = {"0", KEY_NAME})
    public void resetConfigProperty(String propertyName) {
        // Check if the config property exists.
        resolveConfigProperty(propertyName);
        // Delete it in the storage.
        storage.deleteConfigProperty(propertyName);
    }

    private static RoleMapping dtoToRoleMapping(RoleMappingDto dto) {
        RoleMapping mapping = new RoleMapping();
        mapping.setPrincipalId(dto.getPrincipalId());
        mapping.setRole(RoleType.valueOf(dto.getRole()));
        mapping.setPrincipalName(dto.getPrincipalName());
        return mapping;
    }


    private static boolean isNullOrTrue(Boolean value) {
        return value == null || value;
    }

    private String createDownloadHref(String downloadId) {
        return "/apis/registry/v2/downloads/" + downloadId;
    }

    private static ConfigurationProperty dtoToConfigurationProperty(DynamicConfigPropertyDef def, DynamicConfigPropertyDto dto) {
        ConfigurationProperty rval = new ConfigurationProperty();
        rval.setName(def.getName());
        rval.setValue(dto.getValue());
        rval.setType(def.getType().getName());
        rval.setLabel(def.getLabel());
        rval.setDescription(def.getDescription());
        return rval;
    }

    private ConfigurationProperty defToConfigurationProperty(DynamicConfigPropertyDef def) {
        String propertyValue = config.getOptionalValue(def.getName(), String.class).orElse(def.getDefaultValue());

        ConfigurationProperty rval = new ConfigurationProperty();
        rval.setName(def.getName());
        rval.setValue(propertyValue);
        rval.setType(def.getType().getName());
        rval.setLabel(def.getLabel());
        rval.setDescription(def.getDescription());
        return rval;
    }

    /**
     * Lookup the dynamic configuration property being set.  Ensure that it exists (throws
     * a {@link NotFoundException} if it does not.
     * @param propertyName the name of the dynamic property
     * @return the dynamic config property definition
     */
    private DynamicConfigPropertyDef resolveConfigProperty(String propertyName) {
        DynamicConfigPropertyDef property = dynamicPropertyIndex.getProperty(propertyName);
        if (property == null) {
            throw new ConfigPropertyNotFoundException(propertyName);
        }
        if (!dynamicPropertyIndex.isAccepted(propertyName)) {
            throw new ConfigPropertyNotFoundException(propertyName);
        }
        return property;
    }

    /**
     * Ensure that the value being set on the given property is value for the property type.
     * For example, this should fail
     * @param propertyDef the dynamic config property definition
     * @param value the config property value
     */
    private void validateConfigPropertyValue(DynamicConfigPropertyDef propertyDef, String value) {
        if (!propertyDef.isValidValue(value)) {
            throw new InvalidPropertyValueException("Invalid dynamic configuration property value for: " + propertyDef.getName());
        }
    }

}
