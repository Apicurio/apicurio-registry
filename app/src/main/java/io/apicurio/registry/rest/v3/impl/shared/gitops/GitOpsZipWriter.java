package io.apicurio.registry.rest.v3.impl.shared.gitops;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.impl.polling.model.v0.Artifact;
import io.apicurio.registry.storage.impl.polling.model.v0.Content;
import io.apicurio.registry.storage.impl.polling.model.v0.ContentReference;
import io.apicurio.registry.storage.impl.polling.model.v0.Group;
import io.apicurio.registry.storage.impl.polling.model.v0.Registry;
import io.apicurio.registry.storage.impl.polling.model.v0.Rule;
import io.apicurio.registry.storage.impl.polling.model.v0.Version;
import io.apicurio.registry.storage.impl.sql.RegistryContentUtils;
import io.apicurio.registry.utils.impexp.v3.ArtifactEntity;
import io.apicurio.registry.utils.impexp.v3.ArtifactRuleEntity;
import io.apicurio.registry.utils.impexp.v3.ArtifactVersionEntity;
import io.apicurio.registry.utils.impexp.v3.GlobalRuleEntity;
import io.apicurio.registry.utils.impexp.v3.GroupEntity;
import io.apicurio.registry.utils.impexp.v3.GroupRuleEntity;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class GitOpsZipWriter {

    private static final ObjectMapper YAML_MAPPER;
    private static final String EXT_YAML = ".yaml";
    private static final String EXT_JSON = ".json";

    static {
        final YAMLFactory yamlFactory = YAMLFactory.builder()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER).build();
        YAML_MAPPER = new ObjectMapper(yamlFactory);
        YAML_MAPPER.findAndRegisterModules();
        YAML_MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    private static final String DEFAULT_REGISTRY_ID = "main";

    private final ZipOutputStream zip;
    private final GitOpsEntityCollector collector;
    private final Map<Long, String> contentIdToPath;
    private final String registryId;
    private final Set<String> usedZipPaths = new HashSet<>();
    private final Map<Long, String> writtenContentMetadata = new HashMap<>();

    public GitOpsZipWriter(ZipOutputStream zip, GitOpsEntityCollector collector,
            Map<Long, String> contentIdToPath) {
        this(zip, collector, contentIdToPath, DEFAULT_REGISTRY_ID);
    }

    public GitOpsZipWriter(ZipOutputStream zip, GitOpsEntityCollector collector,
            Map<Long, String> contentIdToPath, String registryId) {
        this.zip = zip;
        this.collector = collector;
        this.contentIdToPath = contentIdToPath;
        this.registryId = registryId != null ? registryId : DEFAULT_REGISTRY_ID;
    }

    public void write() throws IOException {
        writeGroupYamls();
        writeArtifactYamls();
        writeRegistryYaml();
    }

    private void writeRegistryYaml() throws IOException {
        final List<Rule> rules = new ArrayList<>();
        for (final GlobalRuleEntity gre : collector.getGlobalRules()) {
            rules.add(Rule.builder().ruleType(gre.ruleType.value()).config(gre.configuration).build());
        }

        final Registry registry = Registry.builder()
                .type("registry-v0")
                .registryId(registryId)
                .globalRules(rules.isEmpty() ? Collections.emptyList() : rules)
                .properties(Collections.emptyList())
                .build();

        writeYamlEntry("registry.registry.yaml", registry);
    }

    private void writeGroupYamls() throws IOException {
        for (final Map.Entry<String, GroupEntity> entry : collector.getGroups().entrySet()) {
            final String groupKey = entry.getKey();
            final GroupEntity ge = entry.getValue();

            final List<Rule> rules = new ArrayList<>();
            final List<GroupRuleEntity> groupRuleEntities = collector.getGroupRules().get(groupKey);
            if (groupRuleEntities != null) {
                for (final GroupRuleEntity gre : groupRuleEntities) {
                    rules.add(Rule.builder().ruleType(gre.type.value()).config(gre.configuration).build());
                }
            }

            final Group group = Group.builder()
                    .type("group-v0")
                    .groupId(ge.groupId)
                    .description(ge.description)
                    .labels(ge.labels)
                    .owner(ge.owner)
                    .artifactsType(ge.artifactsType)
                    .createdOn(epochToIso(ge.createdOn))
                    .modifiedOn(epochToIso(ge.modifiedOn))
                    .rules(rules.isEmpty() ? null : rules)
                    .build();

            final String safeGroupKey = sanitize(groupKey);
            writeYamlEntry(safeGroupKey + "/" + safeGroupKey + ".registry.yaml", group);
        }
    }

    private void writeArtifactYamls() throws IOException {
        for (final Map.Entry<String, ArtifactEntity> entry : collector.getArtifacts().entrySet()) {
            writeArtifactYaml(entry.getKey(), entry.getValue());
        }
    }

    private void writeArtifactYaml(String artifactKey, ArtifactEntity ae) throws IOException {
        final String groupDir = sanitize(GitOpsEntityCollector.groupKey(ae.groupId));
        final String safeArtifactId = sanitize(ae.artifactId);
        final String artifactDir = groupDir + "/" + safeArtifactId;

        final List<Version> versions = buildVersions(artifactKey, artifactDir);
        final List<Rule> rules = buildArtifactRules(artifactKey);

        final Artifact artifact = Artifact.builder()
                .type("artifact-v0")
                .groupId(ae.groupId)
                .artifactId(ae.artifactId)
                .artifactType(ae.artifactType)
                .name(ae.name)
                .description(ae.description)
                .labels(ae.labels)
                .owner(ae.owner)
                .createdOn(epochToIso(ae.createdOn))
                .modifiedOn(epochToIso(ae.modifiedOn))
                .versions(versions.isEmpty() ? null : versions)
                .rules(rules.isEmpty() ? null : rules)
                .build();

        writeYamlEntry(artifactDir + "/" + safeArtifactId + ".registry.yaml", artifact);
    }

    private List<Version> buildVersions(String artifactKey,
            String artifactDir) throws IOException {
        final List<ArtifactVersionEntity> versionEntities = new ArrayList<>(collector.getVersions()
                .getOrDefault(artifactKey, Collections.emptyList()));
        versionEntities.sort(Comparator.comparingInt(v -> v.versionOrder));

        final List<Version> versions = new ArrayList<>();
        for (final ArtifactVersionEntity ve : versionEntities) {
            versions.add(buildVersion(ve, artifactDir));
        }
        return versions;
    }

    private Version buildVersion(ArtifactVersionEntity ve, String artifactDir) throws IOException {
        final String sharedContentPath = contentIdToPath.get(ve.contentId);
        final String relativeContentPath = sharedContentPath != null
                ? relativePathFromArtifact(artifactDir, sharedContentPath)
                : "../../content/" + ve.contentId + ".bin";

        final String contentMetadataRef = writeContentMetadata(ve, artifactDir);

        return Version.builder()
                .version(ve.version)
                .state(ve.state != null ? ve.state.value() : null)
                .name(ve.name)
                .description(ve.description)
                .labels(ve.labels)
                .content(relativeContentPath)
                .contentMetadata(contentMetadataRef)
                .globalId(ve.globalId)
                .owner(ve.owner)
                .createdOn(epochToIso(ve.createdOn))
                .modifiedOn(epochToIso(ve.modifiedOn))
                .build();
    }

    private String writeContentMetadata(ArtifactVersionEntity ve,
            String artifactDir) throws IOException {
        final String alreadyWritten = writtenContentMetadata.get(ve.contentId);
        if (alreadyWritten != null) {
            return relativePathFromArtifact(artifactDir, alreadyWritten);
        }

        final GitOpsEntityCollector.ContentMetadata cm = collector.getContentMetadataById()
                .get(ve.contentId);

        final List<ContentReference> contentRefs = buildContentReferences(cm);

        final String sharedContentPath = contentIdToPath.get(ve.contentId);
        final String contentFileName = sharedContentPath != null
                ? sharedContentPath.substring(sharedContentPath.lastIndexOf('/') + 1)
                : ve.contentId + ".bin";

        final String metadataEntryPath = "content/" + ve.contentId + ".content.registry.yaml";
        final Content content = Content.builder()
                .type("content-v0")
                .contentId(cm != null ? cm.contentId() : ve.contentId)
                .content("./" + contentFileName)
                .references(contentRefs.isEmpty() ? null : contentRefs)
                .build();

        writeYamlEntry(metadataEntryPath, content);
        writtenContentMetadata.put(ve.contentId, metadataEntryPath);
        return relativePathFromArtifact(artifactDir, metadataEntryPath);
    }

    private List<ContentReference> buildContentReferences(GitOpsEntityCollector.ContentMetadata cm) {
        if (cm == null || cm.serializedReferences() == null || cm.serializedReferences().isEmpty()) {
            return Collections.emptyList();
        }
        final List<ArtifactReferenceDto> refs = RegistryContentUtils
                .deserializeReferences(cm.serializedReferences());
        final List<ContentReference> contentRefs = new ArrayList<>();
        for (final ArtifactReferenceDto ref : refs) {
            contentRefs.add(ContentReference.builder()
                    .groupId(ref.getGroupId())
                    .artifactId(ref.getArtifactId())
                    .version(ref.getVersion())
                    .name(ref.getName())
                    .build());
        }
        return contentRefs;
    }

    private List<Rule> buildArtifactRules(String artifactKey) {
        final List<ArtifactRuleEntity> ruleEntities = collector.getArtifactRules().get(artifactKey);
        if (ruleEntities == null) {
            return Collections.emptyList();
        }
        final List<Rule> rules = new ArrayList<>();
        for (final ArtifactRuleEntity are : ruleEntities) {
            rules.add(Rule.builder().ruleType(are.type.value()).config(are.configuration).build());
        }
        return rules;
    }

    private void writeYamlEntry(String path, Object value) throws IOException {
        final String safePath = deduplicatePath(path);
        final byte[] yamlBytes = YAML_MAPPER.writeValueAsBytes(value);
        zip.putNextEntry(new ZipEntry(safePath));
        zip.write(yamlBytes);
        zip.closeEntry();
    }

    private String deduplicatePath(String path) {
        if (usedZipPaths.add(path)) {
            return path;
        }
        final int dotIdx = path.lastIndexOf('.');
        final String base = dotIdx > 0 ? path.substring(0, dotIdx) : path;
        final String ext = dotIdx > 0 ? path.substring(dotIdx) : "";
        int suffix = 2;
        String candidate = base + "_" + suffix + ext;
        while (!usedZipPaths.add(candidate)) {
            suffix++;
            candidate = base + "_" + suffix + ext;
        }
        return candidate;
    }

    static String relativePathFromArtifact(String artifactDir, String targetPath) {
        final int depth = artifactDir.split("/").length;
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            sb.append("../");
        }
        sb.append(targetPath);
        return sb.toString();
    }

    static String epochToIso(long epochMillis) {
        if (epochMillis <= 0) {
            return null;
        }
        return Instant.ofEpochMilli(epochMillis).toString();
    }

    static String sanitize(String name) {
        if (name == null || name.isEmpty()) {
            return "default";
        }
        final String replaced = name.replace("..", "__");
        final StringBuilder sb = new StringBuilder(replaced.length());
        for (int i = 0; i < replaced.length(); i++) {
            final char c = replaced.charAt(i);
            if (c == '/' || c == '\\') {
                sb.append('_');
            } else {
                sb.append(c);
            }
        }
        final String result = sb.toString();
        if (".".equals(result)) {
            return "_";
        }
        return result;
    }

    static String resolveExtension(String artifactType, String contentType) {
        if (artifactType != null) {
            final String upper = artifactType.toUpperCase(Locale.ROOT);
            return switch (upper) {
                case "AVRO" -> ".avsc";
                case "PROTOBUF" -> ".proto";
                case "OPENAPI", "ASYNCAPI" -> resolveMultiFormatExtension(contentType);
                case "GRAPHQL" -> ".graphql";
                case "WSDL" -> ".wsdl";
                case "XSD" -> ".xsd";
                case "XML" -> ".xml";
                case "THRIFT" -> ".thrift";
                default -> resolveByContentType(contentType, EXT_JSON);
            };
        }
        return resolveByContentType(contentType, ".bin");
    }

    private static String resolveMultiFormatExtension(String contentType) {
        if (contentType != null && contentType.contains("json")) {
            return EXT_JSON;
        }
        return EXT_YAML;
    }

    private static String resolveByContentType(String contentType, String fallback) {
        if (contentType == null) {
            return fallback;
        }
        if (contentType.contains("json")) {
            return EXT_JSON;
        }
        if (contentType.contains("yaml") || contentType.contains("yml")) {
            return EXT_YAML;
        }
        if (contentType.contains("xml")) {
            return ".xml";
        }
        if (contentType.contains("protobuf") || contentType.contains("proto")) {
            return ".proto";
        }
        if (contentType.contains("graphql")) {
            return ".graphql";
        }
        return fallback;
    }
}
