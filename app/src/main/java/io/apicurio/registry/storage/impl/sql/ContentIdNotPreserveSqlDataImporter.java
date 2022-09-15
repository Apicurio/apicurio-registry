package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.RegistryStorageException;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.impl.sql.jdb.Handle;
import io.apicurio.registry.utils.impexp.ContentEntity;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;

import java.util.List;
import java.util.stream.Collectors;

public class ContentIdNotPreserveSqlDataImporter extends SqlDataImporter {

    public ContentIdNotPreserveSqlDataImporter(Logger logger, Handle handle, AbstractSqlRegistryStorage registryStorage, boolean preserveGlobalId) {
        super(logger, handle, registryStorage, preserveGlobalId);
    }

    @Override
    public void importContent(ContentEntity entity) {
        List<ArtifactReferenceDto> references = SqlUtil.deserializeReferences(entity.serializedReferences);

        // We do not need canonicalHash if we have artifactType
        if (entity.canonicalHash == null) {
            if (entity.artifactType != null) {
                ContentHandle canonicalContent = getRegistryStorage().canonicalizeContent(entity.artifactType, ContentHandle.create(entity.contentBytes), references);
                entity.canonicalHash = DigestUtils.sha256Hex(canonicalContent.bytes());
            } else {
                throw new RegistryStorageException("There is not enough information about content. Artifact Type and CanonicalHash are both missing.");
            }
        }

        // When we do not want to preserve contentId, the best solution to import content is create new one with the contentBytes
        // It makes sure there won't be any conflicts
        long newContentId = getRegistryStorage().createOrUpdateContent(getHandle(), ContentHandle.create(entity.contentBytes), entity.contentHash, entity.canonicalHash, references);

        getContentIdMapping().put(entity.contentId, newContentId);

        // Import artifact versions that were waiting for this content
        var artifactsToImport = getWaitingForContent().stream()
                .filter(artifactVersion -> artifactVersion.contentId == entity.contentId)
                .collect(Collectors.toList());

        artifactsToImport.forEach(this::importArtifactVersion);
        getWaitingForContent().removeAll(artifactsToImport);
    }
}
