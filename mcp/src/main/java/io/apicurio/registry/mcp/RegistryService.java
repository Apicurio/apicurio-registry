package io.apicurio.registry.mcp;

import com.microsoft.kiota.ApiException;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.ArdFilter;
import io.apicurio.registry.rest.client.models.ArdSearchQuery;
import io.apicurio.registry.rest.client.models.ArdSearchRequest;
import io.apicurio.registry.rest.client.models.ArdSearchResponse;
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
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rest.client.models.RuleViolationProblemDetails;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class RegistryService {

    private static final String PAGE_TOKEN_PREFIX = "offset:";

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

    public Page<SearchedGroup> listGroups(
            String order,
            String groupOrderBy,
            String pageToken,
            Integer pageSize
    ) {
        var p = pageRequest(pageToken, pageSize);
        var page = client().groups().get(r -> {
            r.queryParameters.offset = p.offset();
            r.queryParameters.limit = p.size();
            r.queryParameters.order = parseSortOrder(order);
            r.queryParameters.orderby = parseGroupSortBy(groupOrderBy);
        });
        return toPage(page.getGroups(), page.getCount(), p);
    }

    /**
     * Paging arguments of one list or search tool call, resolved from its {@code pageToken} and
     * {@code pageSize}.
     */
    record PageRequest(int offset, int size) {
    }

    /**
     * Resolves the paging arguments of a list or search tool call. An empty token starts at the first
     * item. The page size defaults to {@code apicurio.mcp.paging.limit} and is capped at it.
     */
    PageRequest pageRequest(String pageToken, Integer pageSize) {
        if (pageSize != null && pageSize < 1) {
            throw new ToolCallException("Invalid pageSize " + pageSize + ": it must be 1 or more.");
        }
        int limit = config.paging().limit();
        int size = pageSize == null ? limit : Math.min(pageSize, limit);
        return new PageRequest(decodePageToken(pageToken), size);
    }

    static String encodePageToken(int offset) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((PAGE_TOKEN_PREFIX + offset).getBytes(StandardCharsets.UTF_8));
    }

    static int decodePageToken(String pageToken) {
        if (pageToken == null || pageToken.isBlank()) {
            return 0;
        }
        try {
            var decoded = new String(Base64.getUrlDecoder().decode(pageToken.trim()), StandardCharsets.UTF_8);
            if (decoded.startsWith(PAGE_TOKEN_PREFIX)) {
                int offset = Integer.parseInt(decoded.substring(PAGE_TOKEN_PREFIX.length()));
                if (offset >= 0) {
                    return offset;
                }
            }
        } catch (IllegalArgumentException e) {
            // Not Base64, or not a number (NumberFormatException extends IllegalArgumentException).
        }
        throw new ToolCallException("""
                Invalid pageToken. Pass the pageToken returned by the previous call of the same tool, \
                or leave it empty to get the first page.""");
    }

    /**
     * Builds the page returned to the MCP client. The next-page token is set only when the registry
     * reports more matches than the client has received so far.
     */
    static <T> Page<T> toPage(List<T> results, Integer count, PageRequest request) {
        List<T> items = results == null ? List.of() : results;
        if (items.size() > request.size()) {
            items = items.subList(0, request.size());
        }
        int next = request.offset() + items.size();
        String token = !items.isEmpty() && count != null && next < count ? encodePageToken(next) : null;
        return new Page<>(items, count, token);
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

    public Page<SearchedArtifact> listArtifacts(
            String groupId,
            String order,
            String artifactOrderBy,
            String pageToken,
            Integer pageSize
    ) {
        var p = pageRequest(pageToken, pageSize);
        var page = client().groups().byGroupId(groupId).artifacts().get(r -> {
            r.queryParameters.offset = p.offset();
            r.queryParameters.limit = p.size();
            r.queryParameters.order = parseSortOrder(order);
            r.queryParameters.orderby = parseArtifactSortBy(artifactOrderBy);
        });
        return toPage(page.getArtifacts(), page.getCount(), p);
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

    public Page<SearchedVersion> listVersions(
            String groupId,
            String artifactId,
            String order,
            String versionOrderBy,
            String pageToken,
            Integer pageSize
    ) {
        var p = pageRequest(pageToken, pageSize);
        var page = client().groups().byGroupId(groupId)
                .artifacts().byArtifactId(artifactId)
                .versions()
                .get(r -> {
                    r.queryParameters.offset = p.offset();
                    r.queryParameters.limit = p.size();
                    r.queryParameters.order = parseSortOrder(order);
                    r.queryParameters.orderby = parseVersionSortBy(versionOrderBy);
                });
        return toPage(page.getVersions(), page.getCount(), p);
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

    public String testSchemaRules(
            String groupId,
            String artifactId,
            String versionContent,
            String versionContentType
    ) {
        var v = new CreateVersion();
        var c = new VersionContent();
        c.setContentType(versionContentType);
        c.setContent(versionContent);
        v.setContent(c);

        try {
            client().groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().post(v, r -> {
                r.queryParameters.dryRun = true;
            });
            return "Schema is valid and compatible.";
        } catch (RuleViolationProblemDetails e) {
            StringBuilder sb = new StringBuilder();
            sb.append("Schema rules check failed: ").append(e.getDetail()).append("\n");
            if (e.getCauses() != null) {
                for (var cause : e.getCauses()) {
                    sb.append("- ").append(cause.getDescription());
                    if (cause.getContext() != null) {
                        sb.append(" at ").append(cause.getContext());
                    }
                    sb.append("\n");
                }
            }
            return sb.toString();
        } catch (ApiException e) {
            throw new ToolCallException("Failed to test schema rules: " + e.getMessage(), e);
        }
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

    public Page<SearchedGroup> searchGroups(
            String groupId,
            String description,
            String labels,
            String order,
            String groupOrderBy,
            String pageToken,
            Integer pageSize
    ) {
        var p = pageRequest(pageToken, pageSize);
        var page = client().search().groups().get(r -> {
            r.queryParameters.groupId = groupId;
            r.queryParameters.description = description;
            r.queryParameters.labels = utils.toQueryLabels(labels);

            r.queryParameters.offset = p.offset();
            r.queryParameters.limit = p.size();
            r.queryParameters.order = parseSortOrder(order);
            r.queryParameters.orderby = parseGroupSortBy(groupOrderBy);
        });
        return toPage(page.getGroups(), page.getCount(), p);
    }

    /**
     * Returns every matching version, following the pages internally. For callers that need the
     * complete result (such as {@code list_mcp_prompts}), not for tools that return a page to the client.
     */
    public List<SearchedVersion> searchAllVersions(
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
        List<SearchedVersion> all = new ArrayList<>();
        String pageToken = null;
        do {
            var page = searchVersions(groupId, artifactId, artifactType, name, description, jsonLabels,
                    order, versionOrderBy, state, pageToken, null);
            all.addAll(page.results());
            pageToken = page.pageToken();
        } while (pageToken != null);
        return all;
    }

    public Page<SearchedVersion> searchVersions(
            String groupId,
            String artifactId,
            String artifactType,
            String name,
            String description,
            String jsonLabels,
            String order,
            String versionOrderBy,
            VersionState state,
            String pageToken,
            Integer pageSize
    ) {
        var p = pageRequest(pageToken, pageSize);
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

            r.queryParameters.offset = p.offset();
            r.queryParameters.limit = p.size();
            r.queryParameters.order = parseSortOrder(order);
            r.queryParameters.orderby = parseVersionSortBy(versionOrderBy);
        });
        return toPage(page.getVersions(), page.getCount(), p);
    }

    public Page<SearchedArtifact> searchArtifacts(
            String groupId,
            String artifactId,
            String artifactType,
            String name,
            String description,
            String jsonLabels,
            String order,
            String artifactOrderBy,
            String pageToken,
            Integer pageSize
    ) {
        var p = pageRequest(pageToken, pageSize);
        var page = client().search().artifacts().get(r -> {
            r.queryParameters.groupId = groupId;
            r.queryParameters.artifactId = artifactId;
            r.queryParameters.artifactType = artifactType;
            r.queryParameters.name = name;
            r.queryParameters.description = description;
            r.queryParameters.labels = utils.toQueryLabels(jsonLabels);

            r.queryParameters.offset = p.offset();
            r.queryParameters.limit = p.size();
            r.queryParameters.order = parseSortOrder(order);
            r.queryParameters.orderby = parseArtifactSortBy(artifactOrderBy);
        });
        return toPage(page.getArtifacts(), page.getCount(), p);
    }

    public List<ConfigurationProperty> listConfigurationProperties() {
        return client().admin().config().properties().get();
    }

    public ConfigurationProperty getConfigurationProperty(String propertyName) {
        return client().admin().config().properties().byPropertyName(propertyName).get();
    }

    public String searchAgentCards(String name, String skill, String capability,
                                   String pageToken, Integer pageSize) throws Exception {
        var p = pageRequest(pageToken, pageSize);
        var results = client().wellKnown().agents().get(r -> {
            r.queryParameters.offset = p.offset();
            r.queryParameters.limit = p.size();
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
        return utils.toPrettyJson(toPage(results.getAgents(), results.getCount(), p));
    }

    public String getAgentCard(String groupId, String artifactId) throws Exception {
        try (InputStream is = client().wellKnown().agents().byGroupId(groupId).byArtifactId(artifactId).get()) {
            if (is == null) {
                throw new ToolCallException("Unable to retrieve Agent Card.");
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public String searchMcpTools(String name, String parameter,
                                 String pageToken, Integer pageSize) throws Exception {
        var p = pageRequest(pageToken, pageSize);
        var results = client().wellKnown().mcpTools().get(r -> {
            r.queryParameters.offset = p.offset();
            r.queryParameters.limit = p.size();
            if (name != null && !name.isBlank()) {
                r.queryParameters.name = name;
            }
            if (parameter != null && !parameter.isBlank()) {
                r.queryParameters.parameter = new String[]{ parameter };
            }
        });
        return utils.toPrettyJson(toPage(results.getTools(), results.getCount(), p));
    }

    public String getMcpTool(String groupId, String artifactId) throws Exception {
        try (InputStream is = client().wellKnown().mcpTools().byGroupId(groupId).byArtifactId(artifactId).get()) {
            if (is == null) {
                throw new ToolCallException("Unable to retrieve MCP tool.");
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Invokes the registry's {@code POST /.well-known/ard/search} endpoint (ARD - Agentic
     * Resource Discovery). {@code type}, {@code tags}, {@code capabilities} and
     * {@code publisher} are comma-separated lists that populate the corresponding
     * {@code query.filter} keys (OR semantics within a key, AND semantics across keys), per
     * the ARD spec.
     */
    public String ardSearch(
            String text,
            String type,
            String tags,
            String capabilities,
            String publisher,
            String pageToken,
            Integer pageSize
    ) throws Exception {
        var query = new ArdSearchQuery();
        query.setText(text);

        Map<String, Object> filter = new HashMap<>();
        putCommaSeparatedFilter(filter, "type", type);
        putCommaSeparatedFilter(filter, "tags", tags);
        putCommaSeparatedFilter(filter, "capabilities", capabilities);
        putCommaSeparatedFilter(filter, "publisher", publisher);
        if (!filter.isEmpty()) {
            var ardFilter = new ArdFilter();
            ardFilter.setAdditionalData(filter);
            query.setFilter(ardFilter);
        }

        var request = new ArdSearchRequest();
        request.setQuery(query);
        request.setPageToken(pageToken);
        request.setPageSize(pageSize);

        ArdSearchResponse response = client().wellKnown().ard().search().post(request);
        return utils.toPrettyJson(response);
    }

    private void putCommaSeparatedFilter(Map<String, Object> filter, String key, String value) {
        if (value != null && !value.isBlank()) {
            filter.put(key, Arrays.stream(value.split(","))
                    .map(String::trim)
                    .filter(v -> !v.isEmpty())
                    .toList());
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

    public void deleteGroup(String groupId) {
        client().groups().byGroupId(groupId).delete();
    }

    public void createArtifactRule(String groupId, String artifactId, String ruleType, String ruleConfig) {
        var createRule = new CreateRule();
        createRule.setRuleType(RuleType.forValue(ruleType));
        createRule.setConfig(ruleConfig);
        client().groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).rules().post(createRule);
    }

    SortOrder parseSortOrder(String order) {
        return Utils.parseEnum(SortOrder.class, order, "sort order");
    }

    GroupSortBy parseGroupSortBy(String orderBy) {
        return Utils.parseEnum(GroupSortBy.class, orderBy, "group order-by");
    }

    ArtifactSortBy parseArtifactSortBy(String orderBy) {
        return Utils.parseEnum(ArtifactSortBy.class, orderBy, "artifact order-by");
    }

    VersionSortBy parseVersionSortBy(String orderBy) {
        return Utils.parseEnum(VersionSortBy.class, orderBy, "version order-by");
    }
}
