package io.apicurio.registry.utils.impexp;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.impexp.v3.ArtifactEntity;
import io.apicurio.registry.utils.impexp.v3.ArtifactRuleEntity;
import io.apicurio.registry.utils.impexp.v3.ArtifactVersionEntity;
import io.apicurio.registry.utils.impexp.v3.BranchEntity;
import io.apicurio.registry.utils.impexp.v3.CommentEntity;
import io.apicurio.registry.utils.impexp.v3.ContentEntity;
import io.apicurio.registry.utils.impexp.v3.GlobalRuleEntity;
import io.apicurio.registry.utils.impexp.v3.GroupEntity;
import io.apicurio.registry.utils.impexp.v3.GroupRuleEntity;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class EntityReader {

    private static final ObjectMapper mapper;
    static {
        JsonFactory jsonFactory = new JsonFactory();
        jsonFactory.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);
        mapper = new ObjectMapper(jsonFactory);
    }

    private final transient Path importDirectory;
    private List<EntityInfo> entities = null;
    private int majorVersion = 3;
    private int currentIndex = 0;

    /**
     * Constructor.
     */
    public EntityReader(Path importDirectory) {
        this.importDirectory = importDirectory;
    }

    public Entity readNextEntity() throws IOException {
        if (entities == null) {
            createEntityIndex();
        }

        EntityInfo entityInfo = getNextEntityInfo();
        if (entityInfo != null) {
            EntityType entityType = entityInfo.getType();
            if (entityType != null) {
                switch (entityType) {
                    case Artifact:
                        return readArtifact(entityInfo);
                    case ArtifactRule:
                        return readArtifactRule(entityInfo);
                    case ArtifactVersion:
                        return readArtifactVersion(entityInfo);
                    case Content:
                        return readContent(entityInfo);
                    case GlobalRule:
                        return readGlobalRule(entityInfo);
                    case Group:
                        return readGroup(entityInfo);
                    case GroupRule:
                        return readGroupRule(entityInfo);
                    case Comment:
                        return readComment(entityInfo);
                    case Branch:
                        return readBranch(entityInfo);
                    case Manifest:
                        return readManifest(entityInfo);
                }
            }
        }

        return null;
    }

    /**
     * Return the next entity info object in the list.
     */
    private EntityInfo getNextEntityInfo() {
        if (entities == null) {
            return null;
        }
        if (currentIndex >= entities.size()) {
            return null;
        }
        return entities.get(currentIndex++);
    }

    /**
     * Create the index of entities found in the ZIP file. Make sure we read the entities in the following
     * order: <br/>
     * <ol>
     * <li>Manifest</li>
     * <li>Rules</li>
     * <li>Content</li>
     * <li>Groups</li>
     * <li>Group Rules</li>
     * <li>Artifact Rules</li>
     * <li>Artifact Versions</li>
     * <li>Artifact Branches</li>
     * <li>Comments</li>
     * </ol>
     */
    private void createEntityIndex() {
        EntityInfo manifest = null;
        List<EntityInfo> rules = new LinkedList<>();
        List<EntityInfo> content = new LinkedList<>();
        List<EntityInfo> groups = new LinkedList<>();
        List<EntityInfo> groupRules = new LinkedList<>();
        List<EntityInfo> artifacts = new LinkedList<>();
        List<EntityInfo> artifactRules = new LinkedList<>();
        List<EntityInfo> versions = new LinkedList<>();
        List<EntityInfo> branches = new LinkedList<>();
        List<EntityInfo> comments = new LinkedList<>();

        Collection<File> allFiles = FileUtils.listFiles(this.importDirectory.toFile(),
                new String[] { "json" }, true);
        for (File file : allFiles) {
            String path = file.getAbsolutePath();
            EntityType type = parseEntityType(path);
            EntityInfo entityInfo = new EntityInfo(path, type);
            switch (type) {
                case Artifact:
                    artifacts.add(entityInfo);
                    break;
                case ArtifactRule:
                    artifactRules.add(entityInfo);
                    break;
                case ArtifactVersion:
                    versions.add(entityInfo);
                    break;
                case Content:
                    content.add(entityInfo);
                    break;
                case GlobalRule:
                    rules.add(entityInfo);
                    break;
                case Group:
                    groups.add(entityInfo);
                    break;
                case GroupRule:
                    groupRules.add(entityInfo);
                    break;
                case Comment:
                    comments.add(entityInfo);
                    break;
                case Branch:
                    branches.add(entityInfo);
                    break;
                case Manifest:
                    manifest = entityInfo;
            }
        }

        if (manifest == null) {
            throw new RuntimeException("No manifest found");
        }
        try {
            readManifest(manifest);
        } catch (IOException e) {
            throw new RuntimeException("Invalid manifest: ", e);
        }

        // Make sure we sort the versions when upgrading from v2 to v3 - this is
        // so that the "latest" branch contains the versions in the correct order.
        if (majorVersion == 2) {
            versions.sort((v1, v2) -> {
                int versionId1 = getArtifactVersionOrder(v1);
                int versionId2 = getArtifactVersionOrder(v1);
                return versionId1 - versionId2;
            });
        }

        entities = new LinkedList<>();
        if (manifest != null) {
            entities.add(manifest);
        }

        entities.addAll(rules);
        entities.addAll(content);
        entities.addAll(groups);
        entities.addAll(groupRules);
        entities.addAll(artifacts);
        entities.addAll(versions);
        entities.addAll(artifactRules);
        entities.addAll(branches);
        entities.addAll(comments);
    }

    private Entity readContent(EntityInfo entry) throws IOException {
        String path = entry.getPath();

        if (majorVersion == 3) {
            // Read the content entity from the *.Content.json file.
            ContentEntity entity = this.readEntry(entry, ContentEntity.class);

            // Read the data for the content entity from the corresponding *.Content.data file.
            String dataFilePath = path.replace(".json", ".data");
            File dataFile = new File(dataFilePath);
            if (dataFile.exists() && dataFile.isFile()) {
                entity.contentBytes = IoUtil.toBytes(dataFile);
            }
            return entity;
        } else {
            // Read the content entity from the *.Content.json file.
            io.apicurio.registry.utils.impexp.v2.ContentEntity entity = this.readEntry(entry,
                    io.apicurio.registry.utils.impexp.v2.ContentEntity.class);

            // Read the data for the content entity from the corresponding *.Content.data file.
            String dataFilePath = path.replace(".json", ".data");
            File dataFile = new File(dataFilePath);
            if (dataFile.exists() && dataFile.isFile()) {
                entity.contentBytes = IoUtil.toBytes(dataFile);
            }
            return entity;
        }
    }

    private ManifestEntity readManifest(EntityInfo entry) throws IOException {
        ManifestEntity manifestEntity = readEntry(entry, ManifestEntity.class);
        if (manifestEntity.systemVersion.startsWith("1")) {
            this.majorVersion = 1;
        }
        if (manifestEntity.systemVersion.startsWith("2")) {
            this.majorVersion = 2;
        }
        return manifestEntity;
    }

    private Entity readGroup(EntityInfo entry) throws IOException {
        return majorVersion == 3 ? readEntry(entry, GroupEntity.class)
            : readEntry(entry, io.apicurio.registry.utils.impexp.v2.GroupEntity.class);
    }

    private Entity readGroupRule(EntityInfo entry) throws IOException {
        return readEntry(entry, GroupRuleEntity.class);
    }

    private Entity readArtifact(EntityInfo entry) throws IOException {
        return readEntry(entry, ArtifactEntity.class);
    }

    private Entity readArtifactVersion(EntityInfo entry) throws IOException {
        return majorVersion == 3 ? readEntry(entry, ArtifactVersionEntity.class)
            : readEntry(entry, io.apicurio.registry.utils.impexp.v2.ArtifactVersionEntity.class);
    }

    private Entity readArtifactRule(EntityInfo entry) throws IOException {
        return majorVersion == 3 ? readEntry(entry, ArtifactRuleEntity.class)
            : readEntry(entry, io.apicurio.registry.utils.impexp.v2.ArtifactRuleEntity.class);
    }

    private Entity readComment(EntityInfo entry) throws IOException {
        return majorVersion == 3 ? readEntry(entry, CommentEntity.class)
            : readEntry(entry, io.apicurio.registry.utils.impexp.v2.CommentEntity.class);
    }

    private Entity readBranch(EntityInfo entry) throws IOException {
        return readEntry(entry, BranchEntity.class);
    }

    private Entity readGlobalRule(EntityInfo entry) throws IOException {
        return majorVersion == 3 ? readEntry(entry, GlobalRuleEntity.class)
            : readEntry(entry, io.apicurio.registry.utils.impexp.v2.GlobalRuleEntity.class);
    }

    private EntityType parseEntityType(String path) {
        String[] split = path.split("\\.");
        if (split.length > 2) {
            String typeStr = split[split.length - 2];
            EntityType type = EntityType.valueOf(typeStr);
            return type;
        }
        return null;
    }

    private <T> T readEntry(EntityInfo entry, Class<T> theClass) throws IOException {
        File file = entry.toFile();
        byte[] bytes = IoUtil.toBytes(file);
        T entity = mapper.readerFor(theClass).readValue(bytes);
        return entity;
    }

    private int getArtifactVersionOrder(EntityInfo artifactVersionEntityInfo) {
        try {
            Entity entity = readArtifactVersion(artifactVersionEntityInfo);
            return ((io.apicurio.registry.utils.impexp.v2.ArtifactVersionEntity) entity).versionId;
        } catch (IOException e) {
            // Nothing we can do here. :(
            return 0;
        }
    }

}
