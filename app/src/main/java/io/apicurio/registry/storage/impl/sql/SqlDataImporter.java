package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.impl.sql.jdb.Handle;
import io.apicurio.registry.util.AbstractDataImporter;
import io.apicurio.registry.utils.impexp.ArtifactRuleEntity;
import io.apicurio.registry.utils.impexp.ArtifactVersionEntity;
import io.apicurio.registry.utils.impexp.ContentEntity;
import io.apicurio.registry.utils.impexp.GlobalRuleEntity;
import io.apicurio.registry.utils.impexp.GroupEntity;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


public class SqlDataImporter extends AbstractDataImporter {

    private final Handle handle;
    private final AbstractSqlRegistryStorage registryStorage;

    private final boolean preserveGlobalId;

    private final Map<Long, Long> contentIdMapping = new HashMap<>();
    // To handle issue, when we do not know new content id before importing the actual content
    private final ArrayList<ArtifactVersionEntity> waitingForContent = new ArrayList<>();

    public SqlDataImporter(Logger logger, Handle handle, AbstractSqlRegistryStorage registryStorage, boolean preserveGlobalId) {
        super(logger);
        this.handle = handle;
        this.registryStorage = registryStorage;
        this.preserveGlobalId = preserveGlobalId;
    }

    @Override
    public void importArtifactRule(ArtifactRuleEntity entity) {
        registryStorage.importArtifactRule(handle, entity);
    }

    @Override
    public void importArtifactVersion(ArtifactVersionEntity entity) {
        // Content needs to be imported before artifact
        if (!contentIdMapping.containsKey(entity.contentId)) {
            // Add to the queue waiting for content imported
            waitingForContent.add(entity);
            return;
        }

        entity.contentId = contentIdMapping.get(entity.contentId);

        if(!preserveGlobalId) {
            entity.globalId = -1;
        }

        registryStorage.importArtifactVersion(handle, entity);
    }

    @Override
    public void importContent(ContentEntity entity) {
        List<ArtifactReferenceDto> references = SqlUtil.deserializeReferences(entity.serializedReferences);

        // We do not need canonicalHash if we have artifactType
        if (entity.canonicalHash == null && entity.artifactType != null) {
            ContentHandle canonicalContent = registryStorage.canonicalizeContent(entity.artifactType, ContentHandle.create(entity.contentBytes), references);
            entity.canonicalHash = DigestUtils.sha256Hex(canonicalContent.bytes());
        }

        getContentIdMapping().put(entity.contentId, entity.contentId);

        registryStorage.importContent(handle, entity);

        // Import artifact versions that were waiting for this content
        var artifactsToImport = waitingForContent.stream()
                .filter(artifactVersion -> artifactVersion.contentId == entity.contentId)
                .collect(Collectors.toList());

        artifactsToImport.forEach(this::importArtifactVersion);
        waitingForContent.removeAll(artifactsToImport);
    }

    @Override
    public void importGlobalRule(GlobalRuleEntity entity) {
        registryStorage.importGlobalRule(handle, entity);
    }

    @Override
    public void importGroup(GroupEntity entity) {
        registryStorage.importGroup(handle, entity);
    }

    protected Map<Long, Long> getContentIdMapping() {
        return contentIdMapping;
    }

    protected ArrayList<ArtifactVersionEntity> getWaitingForContent() {
        return waitingForContent;
    }

    protected AbstractSqlRegistryStorage getRegistryStorage() {
        return registryStorage;
    }

    protected Handle getHandle() {
        return handle;
    }
}
