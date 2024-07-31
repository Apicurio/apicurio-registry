package io.apicurio.registry.utils.impexp.v2;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.impexp.Entity;
import io.apicurio.registry.utils.impexp.EntityType;

import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class EntityReader {

    private static final ObjectMapper mapper;
    static {
        JsonFactory jsonFactory = new JsonFactory();
        jsonFactory.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);
        mapper = new ObjectMapper(jsonFactory);
    }

    private final transient ZipInputStream zip;

    /**
     * Constructor.
     * 
     * @param zip
     */
    public EntityReader(ZipInputStream zip) {
        this.zip = zip;
    }

    public Entity readEntity() throws IOException {
        ZipEntry entry = zip.getNextEntry();
        while (entry != null && entry.isDirectory()) {
            entry = zip.getNextEntry();
        }
        if (entry != null) {
            String path = entry.getName();
            EntityType entityType = parseEntityType(path);
            if (entityType != null) {
                switch (entityType) {
                    case ArtifactRule:
                        return readArtifactRule(entry);
                    case ArtifactVersion:
                        return readArtifactVersion(entry);
                    case Content:
                        return readContent(entry);
                    case GlobalRule:
                        return readGlobalRule(entry);
                    case Group:
                        return readGroup(entry);
                    case Comment:
                        return readComment(entry);
                    case Manifest:
                        return readManifest(entry);
                }
            }
        }

        return null;
    }

    private io.apicurio.registry.utils.impexp.v2.ContentEntity readContent(ZipEntry entry)
            throws IOException {
        if (entry.getName().endsWith(".json")) {
            io.apicurio.registry.utils.impexp.v2.ContentEntity entity = this.readEntry(entry,
                    ContentEntity.class);

            ZipEntry dataEntry = zip.getNextEntry();
            if (!dataEntry.getName().endsWith(".Content.data")) {
                // TODO what to do if this isn't the file we expect??
            }

            entity.contentBytes = IoUtil.toBytes(zip, false);
            zip.read(entity.contentBytes);
            return entity;
        } else {
            throw new IOException("Not yet supported: found .Content.data file before .Content.json");
        }
    }

    private io.apicurio.registry.utils.impexp.v2.ManifestEntity readManifest(ZipEntry entry)
            throws IOException {
        return readEntry(entry, ManifestEntity.class);
    }

    private io.apicurio.registry.utils.impexp.v2.GroupEntity readGroup(ZipEntry entry) throws IOException {
        return readEntry(entry, GroupEntity.class);
    }

    private io.apicurio.registry.utils.impexp.v2.ArtifactVersionEntity readArtifactVersion(ZipEntry entry)
            throws IOException {
        return this.readEntry(entry, ArtifactVersionEntity.class);
    }

    private io.apicurio.registry.utils.impexp.v2.ArtifactRuleEntity readArtifactRule(ZipEntry entry)
            throws IOException {
        return this.readEntry(entry, ArtifactRuleEntity.class);
    }

    private io.apicurio.registry.utils.impexp.v2.CommentEntity readComment(ZipEntry entry)
            throws IOException {
        return this.readEntry(entry, CommentEntity.class);
    }

    private io.apicurio.registry.utils.impexp.v2.GlobalRuleEntity readGlobalRule(ZipEntry entry)
            throws IOException {
        return this.readEntry(entry, GlobalRuleEntity.class);
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

    private <T> T readEntry(ZipEntry entry, Class<T> theClass) throws IOException {
        byte[] bytes = IoUtil.toBytes(zip, false);
        T entity = mapper.readerFor(theClass).readValue(bytes);
        return entity;
    }

}
