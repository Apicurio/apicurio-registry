package io.apicurio.registry.utils.impexp.v3;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.utils.impexp.Entity;
import io.apicurio.registry.utils.impexp.EntityType;
import io.apicurio.registry.utils.impexp.ManifestEntity;

import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class EntityWriter {

    private static final ObjectMapper mapper;
    static {
        JsonFactory jsonFactory = new JsonFactory();
        jsonFactory.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);
        mapper = new ObjectMapper(jsonFactory);
    }

    private final transient ZipOutputStream zip;

    /**
     * Constructor.
     * 
     * @param zip
     */
    public EntityWriter(ZipOutputStream zip) {
        this.zip = zip;
    }

    /**
     * Writes the given entity to the zip output stream.
     * 
     * @param entity
     * @throws IOException
     */
    public void writeEntity(Entity entity) throws IOException {
        switch (entity.getEntityType()) {
            case Content:
                writeEntity((ContentEntity) entity);
                break;
            case Group:
                writeEntity((GroupEntity) entity);
                break;
            case GroupRule:
                writeEntity((GroupRuleEntity) entity);
                break;
            case Artifact:
                writeEntity((ArtifactEntity) entity);
                break;
            case ArtifactVersion:
                writeEntity((ArtifactVersionEntity) entity);
                break;
            case ArtifactRule:
                writeEntity((ArtifactRuleEntity) entity);
                break;
            case GlobalRule:
                writeEntity((GlobalRuleEntity) entity);
                break;
            case Comment:
                writeEntity((CommentEntity) entity);
                break;
            case Branch:
                writeEntity((BranchEntity) entity);
                break;
            case Manifest:
                writeEntity((ManifestEntity) entity);
                break;
            default:
                throw new RuntimeException("Unhandled entity type: " + entity.getEntityType().name());
        }
    }

    private void writeEntity(ContentEntity entity) throws IOException {
        ZipEntry mdEntry = createZipEntry(EntityType.Content, entity.contentHash, "json");
        ZipEntry dataEntry = createZipEntry(EntityType.Content, entity.contentHash, "data");

        // Write the meta-data file.
        write(mdEntry, entity, ContentEntity.class);

        // Write the content file.
        zip.putNextEntry(dataEntry);
        zip.write(entity.contentBytes);
        zip.closeEntry();
    }

    private void writeEntity(ManifestEntity entity) throws IOException {
        ZipEntry mdEntry = createZipEntry(EntityType.Manifest,
                "manifest-" + entity.exportedOn.toInstant().toString(), "json");
        write(mdEntry, entity, ManifestEntity.class);
    }

    private void writeEntity(GroupEntity entity) throws IOException {
        ZipEntry mdEntry = createZipEntry(EntityType.Group, entity.groupId, "json");
        write(mdEntry, entity, GroupEntity.class);
    }

    private void writeEntity(GroupRuleEntity entity) throws IOException {
        ZipEntry mdEntry = createZipEntry(EntityType.GroupRule, entity.groupId, entity.type.name(), "json");
        write(mdEntry, entity, GroupRuleEntity.class);
    }

    private void writeEntity(ArtifactEntity entity) throws IOException {
        ZipEntry mdEntry = createZipEntry(EntityType.Artifact, entity.groupId, entity.artifactId, "MetaData",
                "json");
        write(mdEntry, entity, ArtifactEntity.class);
    }

    private void writeEntity(ArtifactVersionEntity entity) throws IOException {
        ZipEntry mdEntry = createZipEntry(EntityType.ArtifactVersion, entity.groupId, entity.artifactId,
                entity.version, "json");
        write(mdEntry, entity, ArtifactVersionEntity.class);
    }

    private void writeEntity(ArtifactRuleEntity entity) throws IOException {
        ZipEntry mdEntry = createZipEntry(EntityType.ArtifactRule, entity.groupId, entity.artifactId,
                entity.type.name(), "json");
        write(mdEntry, entity, ArtifactRuleEntity.class);
    }

    private void writeEntity(GlobalRuleEntity entity) throws IOException {
        ZipEntry mdEntry = createZipEntry(EntityType.GlobalRule, entity.ruleType.name(), "json");
        write(mdEntry, entity, GlobalRuleEntity.class);
    }

    private void writeEntity(CommentEntity entity) throws IOException {
        ZipEntry mdEntry = createZipEntry(EntityType.Comment, entity.globalId + '-' + entity.commentId,
                "json");
        write(mdEntry, entity, CommentEntity.class);
    }

    private void writeEntity(BranchEntity entity) throws IOException {
        ZipEntry mdEntry = createZipEntry(EntityType.Branch, entity.groupId, entity.artifactId,
                entity.branchId, "json");
        write(mdEntry, entity, BranchEntity.class);
    }

    private ZipEntry createZipEntry(EntityType type, String fileName, String fileExt) {
        return createZipEntry(type, null, null, fileName, fileExt);
    }

    private ZipEntry createZipEntry(EntityType type, String groupId, String fileName, String fileExt) {
        return createZipEntry(type, groupId, null, fileName, fileExt);
    }

    private ZipEntry createZipEntry(EntityType type, String groupId, String artifactId, String fileName,
            String fileExt) {
        // TODO encode groupId, artifactId, and filename as path elements
        String path = null;
        switch (type) {
            case ArtifactRule:
                path = String.format("groups/%s/artifacts/%s/rules/%s.%s.%s", groupOrDefault(groupId),
                        artifactId, fileName, type.name(), fileExt);
                break;
            case Artifact:
                path = String.format("groups/%s/artifacts/%s/%s.%s.%s", groupOrDefault(groupId), artifactId,
                        fileName, type.name(), fileExt);
                break;
            case Branch:
                path = String.format("groups/%s/artifacts/%s/branches/%s.%s.%s", groupOrDefault(groupId),
                        artifactId, fileName, type.name(), fileExt);
                break;
            case ArtifactVersion:
                path = String.format("groups/%s/artifacts/%s/versions/%s.%s.%s", groupOrDefault(groupId),
                        artifactId, fileName, type.name(), fileExt);
                break;
            case Content:
                path = String.format("content/%s.%s.%s", fileName, type.name(), fileExt);
                break;
            case GlobalRule:
                path = String.format("rules/%s.%s.%s", fileName, type.name(), fileExt);
                break;
            case Group:
                path = String.format("groups/%s.%s.%s", fileName, type.name(), fileExt);
                break;
            case GroupRule:
                path = String.format("groups/%s/rules/%s.%s.%s", groupOrDefault(groupId), fileName,
                        type.name(), fileExt);
                break;
            case Comment:
                path = String.format("comments/%s.%s.%s", fileName, type.name(), fileExt);
                break;
            case Manifest:
                path = String.format("%s.%s.%s", fileName, type.name(), fileExt);
                break;
            default:
                throw new RuntimeException("Unhandled entity type: " + type.name());
        }
        return new ZipEntry(path);
    }

    private String groupOrDefault(String groupId) {
        return groupId == null ? "default" : groupId;
    }

    private void write(ZipEntry entry, Entity entity, Class<?> entityClass) throws IOException {
        zip.putNextEntry(entry);
        mapper.writerFor(entityClass).writeValue(zip, entity);
        zip.closeEntry();
    }

}
