package io.apicurio.registry.rest.v3;

import io.apicurio.common.apps.config.Dynamic;
import io.apicurio.common.apps.config.DynamicConfigPropertyDef;
import io.apicurio.common.apps.config.DynamicConfigPropertyDto;
import io.apicurio.common.apps.config.DynamicConfigPropertyIndex;
import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.auth.RoleBasedAccessApiOperation;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.logging.audit.Audited;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.rest.ConflictException;
import io.apicurio.registry.rest.MissingRequiredParameterException;
import io.apicurio.registry.rest.v3.beans.ArtifactTypeInfo;
import io.apicurio.registry.rest.v3.beans.ConfigurationProperty;
import io.apicurio.registry.rest.v3.beans.CreateRule;
import io.apicurio.registry.rest.v3.beans.DownloadRef;
import io.apicurio.registry.rest.v3.beans.RoleMapping;
import io.apicurio.registry.rest.v3.beans.RoleMappingSearchResults;
import io.apicurio.registry.rest.v3.beans.Rule;
import io.apicurio.registry.rest.v3.beans.SnapshotMetaData;
import io.apicurio.registry.rest.v3.beans.UpdateConfigurationProperty;
import io.apicurio.registry.rest.v3.beans.UpdateRole;
import io.apicurio.registry.rest.v3.shared.DataExporter;
import io.apicurio.registry.rules.DefaultRuleDeletionException;
import io.apicurio.registry.rules.RulesProperties;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.DownloadContextDto;
import io.apicurio.registry.storage.dto.DownloadContextType;
import io.apicurio.registry.storage.dto.RoleMappingDto;
import io.apicurio.registry.storage.dto.RoleMappingSearchResultsDto;
import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.storage.error.ConfigPropertyNotFoundException;
import io.apicurio.registry.storage.error.InvalidPropertyValueException;
import io.apicurio.registry.storage.error.RuleNotFoundException;
import io.apicurio.registry.storage.importing.ImportExportConfigProperties;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.impexp.Entity;
import io.apicurio.registry.utils.impexp.EntityInputStream;
import io.apicurio.registry.utils.impexp.EntityInputStreamImpl;
import io.apicurio.registry.utils.impexp.EntityReader;
import io.apicurio.registry.utils.impexp.EntityType;
import io.apicurio.registry.utils.impexp.ManifestEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptors;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.commons.io.FileUtils;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;

import static io.apicurio.registry.logging.audit.AuditingConstants.KEY_FOR_BROWSER;
import static io.apicurio.registry.logging.audit.AuditingConstants.KEY_NAME;
import static io.apicurio.registry.logging.audit.AuditingConstants.KEY_PRINCIPAL_ID;
import static io.apicurio.registry.logging.audit.AuditingConstants.KEY_PROPERTY_CONFIGURATION;
import static io.apicurio.registry.logging.audit.AuditingConstants.KEY_ROLE_MAPPING;
import static io.apicurio.registry.logging.audit.AuditingConstants.KEY_RULE;
import static io.apicurio.registry.logging.audit.AuditingConstants.KEY_RULE_TYPE;
import static io.apicurio.registry.logging.audit.AuditingConstants.KEY_UPDATE_ROLE;
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
    DataExporter exporter;

    @Inject
    ImportExportConfigProperties importExportProps;

    @Context
    HttpServletRequest request;

    @Dynamic(label = "Download link expiry", description = "The number of seconds that a generated link to a .zip download file is active before expiring.")
    @ConfigProperty(name = "apicurio.download.href.ttl.seconds", defaultValue = "30")
    @Info(category = "download", description = "Download link expiry", availableSince = "2.1.2.Final")
    Supplier<Long> downloadHrefTtl;

    private static void requireParameter(String parameterName, Object parameterValue) {
        if (parameterValue == null) {
            throw new MissingRequiredParameterException(parameterName);
        }
    }

    /**
     * @see io.apicurio.registry.rest.v3.AdminResource#listArtifactTypes()
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

    @Override
    @Audited
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Admin)
    public SnapshotMetaData triggerSnapshot() {
        storage.triggerSnapshotCreation();
        return SnapshotMetaData.builder().build();
    }

    /**
     * @see io.apicurio.registry.rest.v3.AdminResource#listGlobalRules()
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
     * @see io.apicurio.registry.rest.v3.AdminResource#createGlobalRule(CreateRule)
     */
    @Override
    @Audited(extractParameters = { "0", KEY_RULE })
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Admin)
    public void createGlobalRule(CreateRule data) {
        RuleType ruleType = data.getRuleType();
        requireParameter("ruleType", ruleType);

        if (data.getConfig() == null || data.getConfig().trim().isEmpty()) {
            throw new MissingRequiredParameterException("Config");
        }

        RuleConfigurationDto configDto = new RuleConfigurationDto();
        configDto.setConfiguration(data.getConfig());
        storage.createGlobalRule(data.getRuleType(), configDto);
    }

    /**
     * @see io.apicurio.registry.rest.v3.AdminResource#deleteAllGlobalRules()
     */
    @Override
    @Audited
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Admin)
    public void deleteAllGlobalRules() {
        storage.deleteGlobalRules();
    }

    /**
     * @see io.apicurio.registry.rest.v3.AdminResource#getGlobalRuleConfig(io.apicurio.registry.types.RuleType)
     */
    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public Rule getGlobalRuleConfig(RuleType ruleType) {
        RuleConfigurationDto dto;
        try {
            dto = storage.getGlobalRule(ruleType);
        } catch (RuleNotFoundException ruleNotFoundException) {
            // Check if the rule exists in the default global rules
            dto = rulesProperties.getDefaultGlobalRuleConfiguration(ruleType);
            if (dto == null) {
                throw ruleNotFoundException;
            }
        }
        Rule ruleBean = new Rule();
        ruleBean.setRuleType(ruleType);
        ruleBean.setConfig(dto.getConfiguration());
        return ruleBean;
    }

    /**
     * @see io.apicurio.registry.rest.v3.AdminResource#updateGlobalRuleConfig(io.apicurio.registry.types.RuleType,
     *      io.apicurio.registry.rest.v3.beans.Rule)
     */
    @Override
    @Audited(extractParameters = { "0", KEY_RULE_TYPE, "1", KEY_RULE })
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Admin)
    public Rule updateGlobalRuleConfig(RuleType ruleType, Rule data) {
        RuleConfigurationDto configDto = new RuleConfigurationDto();
        configDto.setConfiguration(data.getConfig());
        try {
            storage.updateGlobalRule(ruleType, configDto);
        } catch (RuleNotFoundException ruleNotFoundException) {
            // This global rule doesn't exist in artifactStore - if the rule exists in the default
            // global rules, override the default by creating a new global rule
            if (rulesProperties.isDefaultGlobalRuleConfigured(ruleType)) {
                storage.createGlobalRule(ruleType, configDto);
            } else {
                throw ruleNotFoundException;
            }
        }
        Rule ruleBean = new Rule();
        ruleBean.setRuleType(ruleType);
        ruleBean.setConfig(data.getConfig());
        return ruleBean;
    }

    /**
     * @see io.apicurio.registry.rest.v3.AdminResource#deleteGlobalRule(io.apicurio.registry.types.RuleType)
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
     * @see io.apicurio.registry.rest.v3.AdminResource#importData(Boolean, Boolean, Boolean, InputStream)
     */
    @Override
    @Audited
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Admin)
    public void importData(Boolean xRegistryPreserveGlobalId, Boolean xRegistryPreserveContentId,
            Boolean requireEmptyRegistry, InputStream data) {
        boolean preserveGlobalId = xRegistryPreserveGlobalId == null ? importExportProps.preserveGlobalId
            : xRegistryPreserveGlobalId;
        boolean preserveContentId = xRegistryPreserveContentId == null ? importExportProps.preserveContentId
            : xRegistryPreserveContentId;
        boolean requireEmpty = requireEmptyRegistry == null ? importExportProps.requireEmptyRegistry
            : requireEmptyRegistry;

        if (requireEmpty && !storage.isEmpty()) {
            throw new ConflictException("Registry is not empty.");
        }

        // The input should be a ZIP file
        final ZipInputStream zip = new ZipInputStream(data, StandardCharsets.UTF_8);

        // Unpack the ZIP file to the local file system (temp)
        Path tempDirectory = null;
        try {
            tempDirectory = Files.createTempDirectory(Paths.get(importExportProps.workDir),
                    "apicurio-import_");
            IoUtil.unpackToDisk(zip, tempDirectory);
            zip.close();
        } catch (IOException e) {
            throw new BadRequestException("Error importing data: " + e.getMessage(), e);
        }

        try {
            // EntityReader reader reads all unpacked entities from the file system
            final EntityReader reader = new EntityReader(tempDirectory);

            // Check the manifest for the version of the ZIP. We either need to import
            // or import with upgrade depending on the version.
            boolean upgrade = false;
            try {
                Entity entity = reader.readNextEntity();
                if (entity.getEntityType() != EntityType.Manifest) {
                    throw new BadRequestException("Invalid import file: missing Manifest file");
                }
                ManifestEntity manifestEntity = (ManifestEntity) entity;

                // Version 2 or 1 requires an upgrade to v3.
                if (manifestEntity.exportVersion.startsWith("3")) {
                    upgrade = false;
                } else if (manifestEntity.exportVersion.startsWith("2")
                        || manifestEntity.exportVersion.startsWith("1")) {
                    upgrade = true;
                } else {
                    throw new BadRequestException(
                            "Invalid import file, unknown manifest version: " + manifestEntity.systemVersion);
                }
            } catch (IOException e) {
                throw new BadRequestException("Error importing data: " + e.getMessage(), e);
            }

            // Create an entity input stream to pass to the storage layer
            EntityInputStream stream = new EntityInputStreamImpl(reader);

            // Import or upgrade the data into the storage
            if (upgrade) {
                this.storage.upgradeData(stream, preserveGlobalId, preserveContentId);
            } else {
                this.storage.importData(stream, preserveGlobalId, preserveContentId);
            }
        } finally {
            try {
                FileUtils.deleteDirectory(tempDirectory.toFile());
            } catch (IOException e) {
                // Best effort
            }
        }
    }

    /**
     * @see io.apicurio.registry.rest.v3.AdminResource#exportData(java.lang.Boolean)
     */
    @Override
    @Audited(extractParameters = { "0", KEY_FOR_BROWSER })
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Admin)
    public Response exportData(Boolean forBrowser) {
        String acceptHeader = request.getHeader("Accept");
        if (Boolean.TRUE.equals(forBrowser) || MediaType.APPLICATION_JSON.equals(acceptHeader)) {
            long expires = System.currentTimeMillis() + (downloadHrefTtl.get() * 1000);
            DownloadContextDto downloadCtx = DownloadContextDto.builder().type(DownloadContextType.EXPORT)
                    .expires(expires).build();
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
     * @see io.apicurio.registry.rest.v3.AdminResource#createRoleMapping(io.apicurio.registry.rest.v3.beans.RoleMapping)
     */
    @Override
    @Audited(extractParameters = { "0", KEY_ROLE_MAPPING })
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Admin)
    @RoleBasedAccessApiOperation
    public void createRoleMapping(RoleMapping data) {
        storage.createRoleMapping(data.getPrincipalId(), data.getRole().name(), data.getPrincipalName());
    }

    /**
     * @see io.apicurio.registry.rest.v3.AdminResource#listRoleMappings(java.math.BigInteger,
     *      java.math.BigInteger)
     */
    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Admin)
    @RoleBasedAccessApiOperation
    public RoleMappingSearchResults listRoleMappings(BigInteger limit, BigInteger offset) {
        if (offset == null) {
            offset = BigInteger.valueOf(0);
        }
        if (limit == null) {
            limit = BigInteger.valueOf(20);
        }

        RoleMappingSearchResultsDto dto = storage.searchRoleMappings(offset.intValue(), limit.intValue());
        return V3ApiUtil.dtoToRoleMappingSearchResults(dto);
    }

    /**
     * @see io.apicurio.registry.rest.v3.AdminResource#getRoleMapping(java.lang.String)
     */
    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Admin)
    @RoleBasedAccessApiOperation
    public RoleMapping getRoleMapping(String principalId) {
        RoleMappingDto dto = storage.getRoleMapping(principalId);
        return V3ApiUtil.dtoToRoleMapping(dto);
    }

    /**
     * @see io.apicurio.registry.rest.v3.AdminResource#updateRoleMapping (java.lang.String,
     *      io.apicurio.registry.rest.v3.beans.Role)
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
     * @see io.apicurio.registry.rest.v3.AdminResource#deleteRoleMapping(java.lang.String)
     */
    @Override
    @Audited(extractParameters = { "0", KEY_PRINCIPAL_ID })
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Admin)
    @RoleBasedAccessApiOperation
    public void deleteRoleMapping(String principalId) {
        storage.deleteRoleMapping(principalId);
    }

    /**
     * @see io.apicurio.registry.rest.v3.AdminResource#listConfigProperties()
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
        return dynamicPropertyIndex.getAcceptedPropertyNames().stream().sorted(String::compareTo)
                .map(pname -> propsI.containsKey(pname)
                    ? V3ApiUtil.dtoToConfigurationProperty(dynamicPropertyIndex.getProperty(pname),
                            propsI.get(pname))
                    : defToConfigurationProperty(dynamicPropertyIndex.getProperty(pname)))
                .collect(Collectors.toList());
    }

    /**
     * @see io.apicurio.registry.rest.v3.AdminResource#getConfigProperty(java.lang.String)
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
            return V3ApiUtil.dtoToConfigurationProperty(def, dto);
        }
    }

    /**
     * @see io.apicurio.registry.rest.v3.AdminResource#updateConfigProperty(java.lang.String,
     *      io.apicurio.registry.rest.v3.beans.UpdateConfigurationProperty)
     */
    @Override
    @Audited(extractParameters = { "0", KEY_NAME, "1", KEY_PROPERTY_CONFIGURATION })
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
     * @see io.apicurio.registry.rest.v3.AdminResource#resetConfigProperty(java.lang.String)
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

    private String createDownloadHref(String downloadId) {
        return "/apis/registry/v3/downloads/" + downloadId;
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
