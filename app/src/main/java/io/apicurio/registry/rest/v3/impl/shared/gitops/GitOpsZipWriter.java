package io.apicurio.registry.rest.v3.impl.shared.gitops;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.impl.polling.model.v0.Artifact;
import io.apicurio.registry.storage.impl.polling.model.v0.Branch;
import io.apicurio.registry.storage.impl.polling.model.v0.Comment;
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
import io.apicurio.registry.utils.impexp.v3.BranchEntity;
import io.apicurio.registry.utils.impexp.v3.CommentEntity;
import io.apicurio.registry.utils.impexp.v3.ContentEntity;
import io.apicurio.registry.utils.impexp.v3.GlobalRuleEntity;
import io.apicurio.registry.utils.impexp.v3.GroupEntity;
import io.apicurio.registry.utils.impexp.v3.GroupRuleEntity;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class GitOpsZipWriter {

    private static final ObjectMapper YAML_MAPPER;

    static {
        final YAMLFactory yamlFactory = YAMLFactory.builder()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER).build();
        YAML_MAPPER = new ObjectMapper(yamlFactory);
        YAML_MAPPER.findAndRegisterModules();
        YAML_MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    private final ZipOutputStream zip;
    private final GitOpsEntityCollector collector;

    public GitOpsZipWriter(ZipOutputStream zip, GitOpsEntityCollector collector) {
        this.zip = zip;
        this.collector = collector;
    }

    public void write() throws IOException {
        writeRegistryYaml();
        writeGroupYamls();
        writeArtifactYamls();
    }

    private void writeRegistryYaml() throws IOException {
        final List<Rule> rules = new ArrayList<>();
        for (final GlobalRuleEntity gre : collector.getGlobalRules()) {
            rules.add(Rule.builder().ruleType(gre.ruleType.value()).config(gre.configuration).build());
        }

        final Registry registry = Registry.builder()
                .type("registry-v0")
                .registryId("default")
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

            writeYamlEntry(groupKey + "/" + groupKey + ".registry.yaml", group);
        }
    }

    private void writeArtifactYamls() throws IOException {
        for (final Map.Entry<String, ArtifactEntity> entry : collector.getArtifacts().entrySet()) {
            final String artifactKey = entry.getKey();
            final ArtifactEntity ae = entry.getValue();
            final String groupDir = GitOpsEntityCollector.groupKey(ae.groupId);
            final String artifactDir = groupDir + "/" + sanitize(ae.artifactId);
            final String contentDir = artifactDir + "/content";

            final List<ArtifactVersionEntity> versionEntities = collector.getVersions()
                    .getOrDefault(artifactKey, Collections.emptyList());
            versionEntities.sort(Comparator.comparingInt(v -> v.versionOrder));

            final Map<Long, String> contentIdToPath = new HashMap<>();
            final List<Version> versions = new ArrayList<>();

            for (final ArtifactVersionEntity ve : versionEntities) {
                final ContentEntity ce = collector.getContentById().get(ve.contentId);
                final String ext = resolveExtension(ae.artifactType,
                        ce != null ? ce.contentType : null);
                final String contentFileName = sanitize(ve.version) + ext;

                final String contentPath;
                if (ce != null && !contentIdToPath.containsKey(ve.contentId)) {
                    contentPath = "content/" + contentFileName;
                    contentIdToPath.put(ve.contentId, contentPath);
                    writeContentFile(contentDir + "/" + contentFileName, ce.contentBytes);
                } else {
                    contentPath = contentIdToPath.getOrDefault(ve.contentId,
                            "content/" + contentFileName);
                }

                String contentMetadataPath = null;
                if (ce != null && ce.serializedReferences != null
                        && !ce.serializedReferences.isEmpty()) {
                    final List<ArtifactReferenceDto> refs = RegistryContentUtils
                            .deserializeReferences(ce.serializedReferences);
                    if (!refs.isEmpty()) {
                        final String metadataFileName = sanitize(ve.version)
                                + ".content.registry.yaml";
                        contentMetadataPath = "content/" + metadataFileName;
                        final String actualContentFileName = contentPath
                                .substring(contentPath.lastIndexOf('/') + 1);
                        writeContentMetadataYaml(contentDir + "/" + metadataFileName, ce, refs,
                                actualContentFileName);
                    }
                }

                final List<Comment> comments = buildComments(ve.globalId);

                final Version version = Version.builder()
                        .version(ve.version)
                        .state(ve.state != null ? ve.state.value() : null)
                        .name(ve.name)
                        .description(ve.description)
                        .labels(ve.labels)
                        .content("./" + contentPath)
                        .contentMetadata(
                                contentMetadataPath != null ? "./" + contentMetadataPath : null)
                        .globalId(ve.globalId)
                        .owner(ve.owner)
                        .createdOn(epochToIso(ve.createdOn))
                        .modifiedOn(epochToIso(ve.modifiedOn))
                        .comments(comments.isEmpty() ? null : comments)
                        .build();

                versions.add(version);
            }

            final List<Rule> rules = buildArtifactRules(artifactKey);
            final List<Branch> branches = buildBranches(artifactKey);

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
                    .branches(branches.isEmpty() ? null : branches)
                    .build();

            writeYamlEntry(
                    artifactDir + "/" + sanitize(ae.artifactId) + ".registry.yaml", artifact);
        }
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

    private List<Branch> buildBranches(String artifactKey) {
        final List<BranchEntity> branchEntities = collector.getBranches().get(artifactKey);
        if (branchEntities == null) {
            return Collections.emptyList();
        }
        final List<Branch> branches = new ArrayList<>();
        for (final BranchEntity be : branchEntities) {
            branches.add(Branch.builder()
                    .branchId(be.branchId)
                    .description(be.description)
                    .systemDefined(be.systemDefined)
                    .owner(be.owner)
                    .createdOn(epochToIso(be.createdOn))
                    .modifiedOn(epochToIso(be.modifiedOn))
                    .versions(be.versions)
                    .build());
        }
        return branches;
    }

    private List<Comment> buildComments(long globalId) {
        final List<CommentEntity> commentEntities = collector.getComments().get(globalId);
        if (commentEntities == null) {
            return Collections.emptyList();
        }
        final List<Comment> comments = new ArrayList<>();
        for (final CommentEntity ce : commentEntities) {
            comments.add(Comment.builder()
                    .commentId(ce.commentId)
                    .owner(ce.owner)
                    .createdOn(epochToIso(ce.createdOn))
                    .value(ce.value)
                    .build());
        }
        return comments;
    }

    private void writeContentMetadataYaml(String path, ContentEntity ce,
            List<ArtifactReferenceDto> refs, String contentFileName) throws IOException {
        final List<ContentReference> contentRefs = new ArrayList<>();
        for (final ArtifactReferenceDto ref : refs) {
            contentRefs.add(ContentReference.builder()
                    .groupId(ref.getGroupId())
                    .artifactId(ref.getArtifactId())
                    .version(ref.getVersion())
                    .name(ref.getName())
                    .build());
        }

        final Content content = Content.builder()
                .type("content-v0")
                .contentId(ce.contentId)
                .content("./" + contentFileName)
                .references(contentRefs)
                .build();

        writeYamlEntry(path, content);
    }

    private void writeYamlEntry(String path, Object value) throws IOException {
        final byte[] yamlBytes = YAML_MAPPER.writeValueAsBytes(value);
        zip.putNextEntry(new ZipEntry(path));
        zip.write(yamlBytes);
        zip.closeEntry();
    }

    private void writeContentFile(String path, byte[] contentBytes) throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        if (contentBytes != null) {
            zip.write(contentBytes);
        }
        zip.closeEntry();
    }

    static String epochToIso(long epochMillis) {
        if (epochMillis <= 0) {
            return null;
        }
        return Instant.ofEpochMilli(epochMillis).toString();
    }

    static String sanitize(String name) {
        if (name == null) {
            return "default";
        }
        return name.replace('/', '_').replace('\\', '_').replace("..", "__");
    }

    static String resolveExtension(String artifactType, String contentType) {
        if (contentType != null) {
            if (contentType.contains("yaml") || contentType.contains("yml")) {
                return ".yaml";
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
        }
        if (artifactType != null) {
            return switch (artifactType.toUpperCase(java.util.Locale.ROOT)) {
                case "AVRO" -> ".avsc";
                case "PROTOBUF" -> ".proto";
                case "OPENAPI" -> ".yaml";
                case "ASYNCAPI" -> ".yaml";
                case "GRAPHQL" -> ".graphql";
                case "WSDL" -> ".wsdl";
                case "XSD" -> ".xsd";
                case "XML" -> ".xml";
                case "THRIFT" -> ".thrift";
                default -> ".json";
            };
        }
        return ".bin";
    }
}
