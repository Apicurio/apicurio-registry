package io.apicurio.registry.rest.v2;

import io.apicurio.common.apps.config.DynamicConfigPropertyDef;
import io.apicurio.common.apps.config.DynamicConfigPropertyDto;
import io.apicurio.common.apps.config.DynamicConfigPropertyIndex;
import io.apicurio.common.apps.logging.Logged;
import io.apicurio.common.apps.logging.audit.Audited;
import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.auth.RoleBasedAccessApiOperation;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.rest.MissingRequiredParameterException;
import io.apicurio.registry.rest.v2.beans.ArtifactTypeInfo;
import io.apicurio.registry.rest.v2.beans.ConfigurationProperty;
import io.apicurio.registry.rest.v2.beans.RoleMapping;
import io.apicurio.registry.rest.v2.beans.Rule;
import io.apicurio.registry.rest.v2.beans.UpdateConfigurationProperty;
import io.apicurio.registry.rest.v2.beans.UpdateRole;
import io.apicurio.registry.rules.DefaultRuleDeletionException;
import io.apicurio.registry.rules.RulesProperties;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.RoleMappingDto;
import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.storage.error.ConfigPropertyNotFoundException;
import io.apicurio.registry.storage.error.InvalidPropertyValueException;
import io.apicurio.registry.storage.error.RuleNotFoundException;
import io.apicurio.registry.storage.importing.ImportExportConfigProperties;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.types.RoleType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptors;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.Config;
import org.slf4j.Logger;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_FOR_BROWSER;
import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_NAME;
import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_PRINCIPAL_ID;
import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_ROLE_MAPPING;
import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_RULE;
import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_RULE_TYPE;
import static io.apicurio.common.apps.logging.audit.AuditingConstants.KEY_UPDATE_ROLE;
import static io.apicurio.registry.utils.DtoUtil.appAuthPropertyToRegistry;
import static io.apicurio.registry.utils.DtoUtil.registryAuthPropertyToApp;

@ApplicationScoped
@Interceptors({ ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class })
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
    DynamicConfigPropertyIndex dynamicPropertyIndex;

    @Inject
    ArtifactTypeUtilProviderFactory factory;

    @Inject
    Config config;

    @Inject
    ImportExportConfigProperties importExportProps;

    @Inject
    io.apicurio.registry.rest.v3.AdminResourceImpl v3Admin;

    private static void requireParameter(String parameterName, Object parameterValue) {
        if (parameterValue == null) {
            throw new MissingRequiredParameterException(parameterName);
        }
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#listArtifactTypes()
     */
    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public List<ArtifactTypeInfo> listArtifactTypes() {
        return factory.getAllArtifactTypes().stream().map(t -> {
            ArtifactTypeInfo ati = new ArtifactTypeInfo();
            ati.setName(t);
            return ati;
        }).collect(Collectors.toList());
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#listGlobalRules()
     */
    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public List<RuleType> listGlobalRules() {
        List<RuleType> rules = storage.getGlobalRules();
        Set<RuleType> defaultRules = rulesProperties.getDefaultGlobalRules();
        return Stream.concat(rules.stream(), defaultRules.stream()).collect(Collectors.toSet()).stream()
                .sorted().collect(Collectors.toList());
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#createGlobalRule(io.apicurio.registry.rest.v2.beans.Rule)
     */
    @Override
    @Audited(extractParameters = { "0", KEY_RULE })
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Admin)
    public void createGlobalRule(Rule data) {
        RuleType type = data.getType();
        requireParameter("type", type);

        if (data.getConfig() == null || data.getConfig().isEmpty()) {
            throw new MissingRequiredParameterException("Config");
        }

        RuleConfigurationDto configDto = new RuleConfigurationDto();
        configDto.setConfiguration(data.getConfig());
        storage.createGlobalRule(data.getType(), configDto);
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#deleteAllGlobalRules()
     */
    @Override
    @Audited
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Admin)
    public void deleteAllGlobalRules() {
        storage.deleteGlobalRules();
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#getGlobalRuleConfig(io.apicurio.registry.types.RuleType)
     */
    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
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
     * @see io.apicurio.registry.rest.v2.AdminResource#updateGlobalRuleConfig(io.apicurio.registry.types.RuleType,
     *      io.apicurio.registry.rest.v2.beans.Rule)
     */
    @Override
    @Audited(extractParameters = { "0", KEY_RULE_TYPE, "1", KEY_RULE })
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Admin)
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
    @Audited(extractParameters = { "0", KEY_RULE_TYPE })
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Admin)
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
     * @see io.apicurio.registry.rest.v2.AdminResource#importData(Boolean, Boolean, java.io.InputStream)
     */
    @Override
    @Audited
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Admin)
    public void importData(Boolean xRegistryPreserveGlobalId, Boolean xRegistryPreserveContentId,
            InputStream data) {
        v3Admin.importData(xRegistryPreserveGlobalId, xRegistryPreserveContentId, false, data);
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#exportData(java.lang.Boolean)
     */
    @Override
    @Audited(extractParameters = { "0", KEY_FOR_BROWSER })
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Admin)
    public Response exportData(Boolean forBrowser) {
        throw new UnsupportedOperationException(
                "Exporting data using the Registry Core v2 API is no longer supported.  Use the v3 API.");
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#createRoleMapping(io.apicurio.registry.rest.v2.beans.RoleMapping)
     */
    @Override
    @Audited(extractParameters = { "0", KEY_ROLE_MAPPING })
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Admin)
    @RoleBasedAccessApiOperation
    public void createRoleMapping(RoleMapping data) {
        storage.createRoleMapping(data.getPrincipalId(), data.getRole().name(), data.getPrincipalName());
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#listRoleMappings()
     */
    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Admin)
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
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Admin)
    @RoleBasedAccessApiOperation
    public RoleMapping getRoleMapping(String principalId) {
        RoleMappingDto dto = storage.getRoleMapping(principalId);
        return dtoToRoleMapping(dto);
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#updateRoleMapping (java.lang.String,
     *      io.apicurio.registry.rest.v2.beans.Role)
     */
    @Override
    @Audited(extractParameters = { "0", KEY_PRINCIPAL_ID, "1", KEY_UPDATE_ROLE })
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Admin)
    @RoleBasedAccessApiOperation
    public void updateRoleMapping(String principalId, UpdateRole data) {
        requireParameter("principalId", principalId);
        requireParameter("role", data.getRole());
        storage.updateRoleMapping(principalId, data.getRole().name());
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#deleteRoleMapping(java.lang.String)
     */
    @Override
    @Audited(extractParameters = { "0", KEY_PRINCIPAL_ID })
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Admin)
    @RoleBasedAccessApiOperation
    public void deleteRoleMapping(String principalId) {
        storage.deleteRoleMapping(principalId);
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#listConfigProperties()
     */
    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Admin)
    public List<ConfigurationProperty> listConfigProperties() {
        // Query the DB for the set of configured properties.
        List<DynamicConfigPropertyDto> props = storage.getConfigProperties();

        // Index the stored properties for easy lookup
        Map<String, DynamicConfigPropertyDto> propsI = new HashMap<>();
        props.forEach(dto -> propsI.put(dto.getName(), dto));

        // Return value is the set of all dynamic config properties, with either configured or default values
        // (depending
        // on whether the value is actually configured and stored in the DB or not).
        return dynamicPropertyIndex.getAcceptedPropertyNames().stream()
                .sorted((pname1, pname2) -> pname1.compareTo(pname2))
                .map(pname -> propsI.containsKey(pname)
                    ? dtoToConfigurationProperty(dynamicPropertyIndex.getProperty(pname), propsI.get(pname))
                    : defToConfigurationProperty(dynamicPropertyIndex.getProperty(pname)))
                .collect(Collectors.toList());
    }

    /**
     * @see io.apicurio.registry.rest.v2.AdminResource#getConfigProperty(java.lang.String)
     */
    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Admin)
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
     * @see io.apicurio.registry.rest.v2.AdminResource#updateConfigProperty(java.lang.String,
     *      io.apicurio.registry.rest.v2.beans.UpdateConfigurationProperty)
     */
    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Admin)
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
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Admin)
    @Audited(extractParameters = { "0", KEY_NAME })
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

    private static ConfigurationProperty dtoToConfigurationProperty(DynamicConfigPropertyDef def,
            DynamicConfigPropertyDto dto) {
        ConfigurationProperty rval = new ConfigurationProperty();
        rval.setName(def.getName());
        rval.setValue(dto.getValue());
        rval.setType(def.getType().getName());
        rval.setLabel(def.getLabel());
        rval.setDescription(def.getDescription());
        return rval;
    }

    private ConfigurationProperty defToConfigurationProperty(DynamicConfigPropertyDef def) {
        String propertyValue = config.getOptionalValue(def.getName(), String.class)
                .orElse(def.getDefaultValue());

        ConfigurationProperty rval = new ConfigurationProperty();
        rval.setName(appAuthPropertyToRegistry(def.getName()));
        rval.setValue(propertyValue);
        rval.setType(def.getType().getName());
        rval.setLabel(def.getLabel());
        rval.setDescription(def.getDescription());
        return rval;
    }

    /**
     * Lookup the dynamic configuration property being set. Ensure that it exists (throws a
     * {@link io.apicurio.registry.storage.error.NotFoundException} if it does not.
     * 
     * @param propertyName the name of the dynamic property
     * @return the dynamic config property definition
     */
    private DynamicConfigPropertyDef resolveConfigProperty(String propertyName) {
        DynamicConfigPropertyDef property = dynamicPropertyIndex.getProperty(propertyName);

        if (property == null) {
            propertyName = registryAuthPropertyToApp(propertyName);
        }
        // If registry property cannot be found, try with app property
        property = dynamicPropertyIndex.getProperty(propertyName);

        if (property == null) {
            throw new ConfigPropertyNotFoundException(propertyName);
        }

        if (!dynamicPropertyIndex.isAccepted(propertyName)) {
            throw new ConfigPropertyNotFoundException(propertyName);
        }
        return property;
    }

    /**
     * Ensure that the value being set on the given property is value for the property type. For example, this
     * should fail
     * 
     * @param propertyDef the dynamic config property definition
     * @param value the config property value
     */
    private void validateConfigPropertyValue(DynamicConfigPropertyDef propertyDef, String value) {
        if (!propertyDef.isValidValue(value)) {
            throw new InvalidPropertyValueException(
                    "Invalid dynamic configuration property value for: " + propertyDef.getName());
        }
    }

}
