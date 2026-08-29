package io.apicurio.registry.mcp;

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.ArtifactMetaData;
import io.apicurio.registry.rest.client.models.ArtifactSortBy;
import io.apicurio.registry.rest.client.models.ArtifactTypeInfo;
import io.apicurio.registry.rest.client.models.ConfigurationProperty;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateGroup;
import io.apicurio.registry.rest.client.models.CreateRule;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.EditableArtifactMetaData;
import io.apicurio.registry.rest.client.models.EditableGroupMetaData;
import io.apicurio.registry.rest.client.models.EditableVersionMetaData;
import io.apicurio.registry.rest.client.models.GroupMetaData;
import io.apicurio.registry.rest.client.models.GroupSortBy;
import io.apicurio.registry.rest.client.models.Rule;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rest.client.models.SearchedArtifact;
import io.apicurio.registry.rest.client.models.SearchedGroup;
import io.apicurio.registry.rest.client.models.SearchedVersion;
import io.apicurio.registry.rest.client.models.SortOrder;
import io.apicurio.registry.rest.client.models.SystemInfo;
import io.apicurio.registry.rest.client.models.UpdateConfigurationProperty;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.rest.client.models.VersionSortBy;
import io.apicurio.registry.rest.client.models.VersionState;
import io.apicurio.registry.rest.client.models.WrappedVersionState;
import io.quarkiverse.mcp.server.ToolCallException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.rules.integrity.IntegrityLevel;

@ApplicationScoped
public class RegistryService {

    @Inject
    McpConfig config;

    @Inject
    Utils utils;

    @Inject
    RegistryClientResolver clientResolver;

    private RegistryClient client() {
        return clientResolver.getClient();
    }

    public SystemInfo getServerInfo() {
        return client().system().info().get();
    }

    public List<SearchedGroup> listGroups(
            String order,
            String groupOrderBy
    ) {
        var page = client().groups().get(r -> {
            r.queryParameters.limit = config.paging().limit() + 1;
            r.queryParameters.order = SortOrder.forValue(order);
            r.queryParameters.orderby = GroupSortBy.forValue(groupOrderBy);
        });
        checkPagingLimit(page.getCount());
        return page.getGroups();
    }

    private void checkPagingLimit(int count) {
        if (config.paging().limitError() && count > config.paging().limit()) {
            throw new ToolCallException("""
                    Apicurio Registry contains more than %s objects, which is the currently configured paging limit. \
                    Use configuration properties "apicurio.mcp.paging.limit" and "apicurio.mcp.paging.limit-error" to configure how paging is handled."""
                    .formatted(config.paging().limit()));
        }
    }

    public GroupMetaData createGroup(
            String groupId,
            String description,
            String jsonLabels
    ) {
        var g = new CreateGroup();
        g.setGroupId(groupId);
        g.setDescription(description);
        g.setLabels(utils.toLabels(jsonLabels));

        return client().groups().post(g);
    }

    public GroupMetaData getGroupMetadata(
            String groupId
    ) {
        return client().groups().byGroupId(groupId).get();
    }

    public void updateGroupMetadata(
            String groupId,
            String description,
            String jsonLabels
    ) {
        var m = new EditableGroupMetaData();
        m.setDescription(description);
        m.setLabels(utils.toLabels(jsonLabels));

        client().groups().byGroupId(groupId).put(m);
    }

    public List<ArtifactTypeInfo> getArtifactTypes() {
        return client().admin().config().artifactTypes().get();
    }

    public List<SearchedArtifact> listArtifacts(
            String groupId,
            String order,
            String artifactOrderBy
    ) {
        var page = client().groups().byGroupId(groupId).artifacts().get(r -> {
            r.queryParameters.limit = config.paging().limit() + 1;
            r.queryParameters.order = SortOrder.forValue(order);
            r.queryParameters.orderby = ArtifactSortBy.forValue(artifactOrderBy);
        });
        checkPagingLimit(page.getCount());
        return page.getArtifacts();
    }

    public ArtifactMetaData getArtifactMetadata(
            String groupId,
            String artifactId
    ) {
        return client().groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get();
    }

    public void updateArtifactMetadata(
            String groupId,
            String artifactId,
            String name,
            String description,
            String jsonLabels
    ) {
        var m = new EditableArtifactMetaData();
        m.setName(name);
        m.setDescription(description);
        m.setLabels(utils.toLabels(jsonLabels));

        client().groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).put(m);
    }

    public void updateVersionMetadata(
            String groupId,
            String artifactId,
            String versionExpression,
            String name,
            String description,
            String jsonLabels
    ) {
        var m = new EditableVersionMetaData();
        m.setName(name);
        m.setDescription(description);
        m.setLabels(utils.toLabels(jsonLabels));

        client().groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .versions().byVersionExpression(versionExpression).put(m);
    }

    public String getVersionContent(
            String groupId,
            String artifactId,
            String versionExpression
    ) throws IOException {
        return new String(client()
                .groups().byGroupId(groupId)
                .artifacts().byArtifactId(artifactId)
                .versions().byVersionExpression(versionExpression)
                .content().get().readAllBytes(),
                StandardCharsets.UTF_8);
    }

    public VersionMetaData getVersionMetadata(
            String groupId,
            String artifactId,
            String versionExpression
    ) {
        return client().groups().byGroupId(groupId)
                .artifacts().byArtifactId(artifactId)
                .versions().byVersionExpression(versionExpression)
                .get();
    }

    public void updateVersionContent(
            String groupId,
            String artifactId,
            String versionExpression,
            String versionContentType,
            String versionContent
    ) {
        var vc = new VersionContent();
        vc.setContentType(versionContentType);
        vc.setContent(versionContent);

        client().groups().byGroupId(groupId)
                .artifacts().byArtifactId(artifactId)
                .versions().byVersionExpression(versionExpression)
                .content().put(vc);
    }

    public List<SearchedVersion> listVersions(
            String groupId,
            String artifactId,
            String order,
            String versionOrderBy
    ) {
        var page = client().groups().byGroupId(groupId)
                .artifacts().byArtifactId(artifactId)
                .versions()
                .get(r -> {
                    r.queryParameters.limit = config.paging().limit() + 1;
                    r.queryParameters.order = SortOrder.forValue(order);
                    r.queryParameters.orderby = VersionSortBy.forValue(versionOrderBy);
                });
        checkPagingLimit(page.getCount());
        return page.getVersions();
    }

    public ArtifactMetaData createArtifact(
            String groupId,
            String artifactId,
            String artifactType,
            String name,
            String description,
            String jsonLabels
    ) {
        var a = new CreateArtifact();
        a.setArtifactId(artifactId);
        a.setArtifactType(artifactType);
        a.setName(name);
        a.setDescription(description);
        a.setLabels(utils.toLabels(jsonLabels));

        return client().groups().byGroupId(groupId).artifacts().post(a).getArtifact();
    }

    public VersionMetaData createVersion(
            String groupId,
            String artifactId,
            String version,
            String versionContentType,
            String versionContent,
            String name,
            String description,
            String jsonLabels,
            Boolean isDraft
    ) {
        var v = new CreateVersion();
        v.setVersion(version);
        v.setName(name);
        v.setDescription(description);
        v.setLabels(utils.toLabels(jsonLabels));
        v.setIsDraft(isDraft);

        var c = new VersionContent();
        c.setContentType(versionContentType);
        c.setContent(versionContent);
        v.setContent(c);

        return client().groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().post(v);
    }

    public void updateVersionState(
            String groupId,
            String artifactId,
            String versionExpression,
            String versionState
    ) {
        VersionState state = Arrays.stream(VersionState.values())
                .filter(v -> versionState != null && v.name().equalsIgnoreCase(versionState.trim()))
                .findFirst()
                .orElseThrow(() -> new ToolCallException(
                        "Invalid version state: '" + versionState + "'. Accepted values (case-insensitive): "
                                + Arrays.toString(VersionState.values())));

        var vs = new WrappedVersionState();
        vs.setState(state);

        client().groups().byGroupId(groupId)
                .artifacts().byArtifactId(artifactId)
                .versions().byVersionExpression(versionExpression)
                .state().put(vs);
    }

    public List<SearchedGroup> searchGroups(
            String groupId,
            String description,
            String labels,
            String order,
            String groupOrderBy
    ) {
        var page = client().search().groups().get(r -> {
            r.queryParameters.groupId = groupId;
            r.queryParameters.description = description;
            r.queryParameters.labels = utils.toQueryLabels(labels);

            r.queryParameters.limit = config.paging().limit() + 1;
            r.queryParameters.order = SortOrder.forValue(order);
            r.queryParameters.orderby = GroupSortBy.forValue(groupOrderBy);
        });
        checkPagingLimit(page.getCount());
        return page.getGroups();
    }

    public List<SearchedVersion> searchVersions(
            String groupId,
            String artifactId,
            String artifactType,
            String name,
            String description,
            String jsonLabels,
            String order,
            String versionOrderBy
    ) {
        return searchVersions(groupId, artifactId, artifactType, name, description, jsonLabels, order, versionOrderBy, (VersionState) null);
    }

    public List<SearchedVersion> searchVersions(
            String groupId,
            String artifactId,
            String artifactType,
            String name,
            String description,
            String jsonLabels,
            String order,
            String versionOrderBy,
            VersionState state
    ) {
        var page = client().search().versions().get(r -> {
            r.queryParameters.groupId = groupId;
            r.queryParameters.artifactId = artifactId;
            r.queryParameters.artifactType = artifactType;
            r.queryParameters.name = name;
            r.queryParameters.description = description;
            r.queryParameters.labels = utils.toQueryLabels(jsonLabels);
            if (state != null) {
                r.queryParameters.state = state;
            }

            r.queryParameters.limit = config.paging().limit() + 1;
            r.queryParameters.order = SortOrder.forValue(order);
            r.queryParameters.orderby = VersionSortBy.forValue(versionOrderBy);
        });
        checkPagingLimit(page.getCount());
        return page.getVersions();
    }

    public List<SearchedArtifact> searchArtifacts(
            String groupId,
            String artifactId,
            String artifactType,
            String name,
            String description,
            String jsonLabels,
            String order,
            String artifactOrderBy
    ) {
        var page = client().search().artifacts().get(r -> {
            r.queryParameters.groupId = groupId;
            r.queryParameters.artifactId = artifactId;
            r.queryParameters.artifactType = artifactType;
            r.queryParameters.name = name;
            r.queryParameters.description = description;
            r.queryParameters.labels = utils.toQueryLabels(jsonLabels);

            r.queryParameters.limit = config.paging().limit() + 1;
            r.queryParameters.order = SortOrder.forValue(order);
            r.queryParameters.orderby = ArtifactSortBy.forValue(artifactOrderBy);
        });
        checkPagingLimit(page.getCount());
        return page.getArtifacts();
    }

    public List<ConfigurationProperty> listConfigurationProperties() {
        return client().admin().config().properties().get();
    }

    public ConfigurationProperty getConfigurationProperty(String propertyName) {
        return client().admin().config().properties().byPropertyName(propertyName).get();
    }

    public String searchAgentCards(String name, String skill, String capability) throws Exception {
        var results = client().wellKnown().agents().get(r -> {
            r.queryParameters.limit = config.paging().limit() + 1;
            if (name != null && !name.isBlank()) {
                r.queryParameters.name = name;
            }
            if (skill != null && !skill.isBlank()) {
                r.queryParameters.skill = new String[]{ skill };
            }
            if (capability != null && !capability.isBlank()) {
                r.queryParameters.capability = new String[]{ capability };
            }
        });
        checkPagingLimit(results.getCount());
        return utils.toPrettyJson(results);
    }

    public String getAgentCard(String groupId, String artifactId) throws Exception {
        try (InputStream is = client().wellKnown().agents().byGroupId(groupId).byArtifactId(artifactId).get()) {
            if (is == null) {
                throw new ToolCallException("Unable to retrieve Agent Card.");
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public String searchMcpTools(String name, String parameter) throws Exception {
        var results = client().wellKnown().mcpTools().get(r -> {
            r.queryParameters.limit = config.paging().limit() + 1;
            if (name != null && !name.isBlank()) {
                r.queryParameters.name = name;
            }
            if (parameter != null && !parameter.isBlank()) {
                r.queryParameters.parameter = new String[]{ parameter };
            }
        });
        checkPagingLimit(results.getCount());
        return utils.toPrettyJson(results);
    }

    public String getMcpTool(String groupId, String artifactId) throws Exception {
        try (InputStream is = client().wellKnown().mcpTools().byGroupId(groupId).byArtifactId(artifactId).get()) {
            if (is == null) {
                throw new ToolCallException("Unable to retrieve MCP tool.");
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public void updateConfigurationProperty(String propertyName, String propertyValue) {
        if (config.safeMode() && !List.of(
                "apicurio.rest.mutability.artifact-version-content.enabled"
        ).contains(propertyName)) {
            throw new ToolCallException("Configuration property can't be updated because it's not in the whitelist.");
        }
        var p = new UpdateConfigurationProperty();
        p.setValue(propertyValue);
        client().admin().config().properties().byPropertyName(propertyName).put(p);
    }

    private static final Map<RuleType, Set<String>> RULE_CONFIGS = Map.of(
            RuleType.COMPATIBILITY, enumNames(CompatibilityLevel.values()),
            RuleType.VALIDITY,      enumNames(ValidityLevel.values()),
            RuleType.INTEGRITY,     enumNames(IntegrityLevel.values()));

    private static Set<String> enumNames(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private RuleType parseRuleType(String ruleType) {
        if (ruleType == null) {
            throw new ToolCallException("Rule type must not be null");
        }
        RuleType parsed = RuleType.forValue(ruleType.trim().toUpperCase(Locale.ROOT));
        if (parsed == null) {
            throw new ToolCallException("Invalid rule type: '" + ruleType + "'. Accepted values: " + Arrays.toString(RuleType.values()));
        }
        return parsed;
    }

    private String validateRuleConfig(RuleType ruleType, String configValue) {
        if (configValue == null || configValue.isBlank()) {
            throw new ToolCallException("Rule configuration value must not be null or empty");
        }
        String normalizedConfig = configValue.trim().toUpperCase(Locale.ROOT);
        Set<String> accepted = RULE_CONFIGS.get(ruleType);
        if (accepted == null) {
            throw new ToolCallException("Invalid rule type: " + ruleType);
        }

        if (ruleType == RuleType.INTEGRITY) {
            String[] parts = normalizedConfig.split(",");
            for (String part : parts) {
                String trimmedPart = part.trim();
                if (!accepted.contains(trimmedPart)) {
                    throw new ToolCallException("Invalid configuration '" + configValue + "' for rule type '"
                            + ruleType + "'. Accepted values (can be comma-separated): " + accepted);
                }
            }
        } else {
            if (!accepted.contains(normalizedConfig)) {
                throw new ToolCallException("Invalid configuration '" + configValue + "' for rule type '"
                        + ruleType + "'. Accepted values: " + accepted);
            }
        }
        return normalizedConfig;
    }

    // ========== Global Rules ==========

    public List<RuleType> listGlobalRules() {
        return client().admin().rules().get();
    }

    public Rule getGlobalRule(String ruleType) {
        RuleType rt = parseRuleType(ruleType);
        return client().admin().rules().byRuleType(rt.name()).get();
    }

    public Rule createGlobalRule(String ruleType, String configValue) {
        RuleType rt = parseRuleType(ruleType);
        String ruleConfig = validateRuleConfig(rt, configValue);
        CreateRule r = new CreateRule();
        r.setRuleType(rt);
        r.setConfig(ruleConfig);
        client().admin().rules().post(r);
        return getGlobalRule(ruleType);
    }

    public Rule updateGlobalRule(String ruleType, String configValue) {
        RuleType rt = parseRuleType(ruleType);
        String ruleConfig = validateRuleConfig(rt, configValue);
        Rule r = new Rule();
        r.setRuleType(rt);
        r.setConfig(ruleConfig);
        return client().admin().rules().byRuleType(rt.name()).put(r);
    }

    public void deleteGlobalRule(String ruleType) {
        RuleType rt = parseRuleType(ruleType);
        client().admin().rules().byRuleType(rt.name()).delete();
    }

    public void deleteAllGlobalRules() {
        client().admin().rules().delete();
    }

    // ========== Group Rules ==========

    public List<RuleType> listGroupRules(String groupId) {
        return client().groups().byGroupId(groupId).rules().get();
    }

    public Rule getGroupRule(String groupId, String ruleType) {
        RuleType rt = parseRuleType(ruleType);
        return client().groups().byGroupId(groupId).rules().byRuleType(rt.name()).get();
    }

    public Rule createGroupRule(String groupId, String ruleType, String configValue) {
        RuleType rt = parseRuleType(ruleType);
        String ruleConfig = validateRuleConfig(rt, configValue);
        CreateRule r = new CreateRule();
        r.setRuleType(rt);
        r.setConfig(ruleConfig);
        client().groups().byGroupId(groupId).rules().post(r);
        return getGroupRule(groupId, ruleType);
    }

    public Rule updateGroupRule(String groupId, String ruleType, String configValue) {
        RuleType rt = parseRuleType(ruleType);
        String ruleConfig = validateRuleConfig(rt, configValue);
        Rule r = new Rule();
        r.setRuleType(rt);
        r.setConfig(ruleConfig);
        return client().groups().byGroupId(groupId).rules().byRuleType(rt.name()).put(r);
    }

    public void deleteGroupRule(String groupId, String ruleType) {
        RuleType rt = parseRuleType(ruleType);
        client().groups().byGroupId(groupId).rules().byRuleType(rt.name()).delete();
    }

    public void deleteAllGroupRules(String groupId) {
        client().groups().byGroupId(groupId).rules().delete();
    }

    // ========== Artifact Rules ==========

    public List<RuleType> listArtifactRules(String groupId, String artifactId) {
        return client().groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).rules().get();
    }

    public Rule getArtifactRule(String groupId, String artifactId, String ruleType) {
        RuleType rt = parseRuleType(ruleType);
        return client().groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).rules().byRuleType(rt.name()).get();
    }

    public Rule createArtifactRule(String groupId, String artifactId, String ruleType, String configValue) {
        RuleType rt = parseRuleType(ruleType);
        String ruleConfig = validateRuleConfig(rt, configValue);
        CreateRule r = new CreateRule();
        r.setRuleType(rt);
        r.setConfig(ruleConfig);
        client().groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).rules().post(r);
        return getArtifactRule(groupId, artifactId, ruleType);
    }

    public Rule updateArtifactRule(String groupId, String artifactId, String ruleType, String configValue) {
        RuleType rt = parseRuleType(ruleType);
        String ruleConfig = validateRuleConfig(rt, configValue);
        Rule r = new Rule();
        r.setRuleType(rt);
        r.setConfig(ruleConfig);
        return client().groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).rules().byRuleType(rt.name()).put(r);
    }

    public void deleteArtifactRule(String groupId, String artifactId, String ruleType) {
        RuleType rt = parseRuleType(ruleType);
        client().groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).rules().byRuleType(rt.name()).delete();
    }

    public void deleteAllArtifactRules(String groupId, String artifactId) {
        client().groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).rules().delete();
    }
}
