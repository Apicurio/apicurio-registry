package io.apicurio.registry.storage.impl.sql;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.EditableVersionMetaDataDto;
import io.apicurio.registry.types.RegistryException;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import io.apicurio.registry.util.ArtifactTypeUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * TODO Refactor
 * TODO Cache calls to referenceResolver
 *
 */
@ApplicationScoped
public class RegistryStorageContentUtils {

    @Inject
    ArtifactTypeUtilProviderFactory factory;

    @Inject
    Logger log;

    /**
     * Canonicalize the given content.
     *
     * @throws RegistryException in the case of an error.
     */
    public ContentHandle canonicalizeContent(String artifactType, ContentHandle content, Map<String, ContentHandle> resolvedReferences) {
        try {
            return factory.getArtifactTypeProvider(artifactType)
                    .getContentCanonicalizer()
                    .canonicalize(content, resolvedReferences);
        } catch (Exception ex) {
            // TODO: We should consider explicitly failing when a content could not be canonicalized.
            // throw new RegistryException("Failed to canonicalize content.", ex);
            log.debug("Failed to canonicalize content: {}", content.content());
            return content;
        }
    }


    /**
     * Canonicalize the given content.
     *
     * @throws RegistryException in the case of an error.
     */
    public ContentHandle canonicalizeContent(String artifactType, ContentHandle content, List<ArtifactReferenceDto> references,
                                             Function<List<ArtifactReferenceDto>, Map<String, ContentHandle>> referenceResolver) {
        try {
            return canonicalizeContent(artifactType, content, referenceResolver.apply(references));
        } catch (Exception ex) {
            throw new RegistryException("Failed to canonicalize content.", ex);
        }
    }


    /**
     * @param references        may be null
     * @param referenceResolver may be null if references is null
     */
    public String getCanonicalContentHash(ContentHandle content, String artifactType, List<ArtifactReferenceDto> references,
                                          Function<List<ArtifactReferenceDto>, Map<String, ContentHandle>> referenceResolver) {
        try {
            if (notEmpty(references)) {
                String referencesSerialized = SqlUtil.serializeReferences(references);
                ContentHandle canonicalContent = canonicalizeContent(artifactType, content, referenceResolver.apply(references));
                return DigestUtils.sha256Hex(concatContentAndReferences(canonicalContent.bytes(), referencesSerialized));
            } else {
                ContentHandle canonicalContent = canonicalizeContent(artifactType, content, Map.of());
                return DigestUtils.sha256Hex(canonicalContent.bytes());
            }
        } catch (IOException ex) {
            throw new RegistryException("Failed to compute canonical content hash.", ex);
        }
    }


    /**
     * @param references may be null
     */
    public String getContentHash(ContentHandle content, List<ArtifactReferenceDto> references) {
        try {
            if (notEmpty(references)) {
                String referencesSerialized = SqlUtil.serializeReferences(references);
                return DigestUtils.sha256Hex(concatContentAndReferences(content.bytes(), referencesSerialized));
            } else {
                return DigestUtils.sha256Hex(content.bytes());
            }
        } catch (IOException ex) {
            throw new RegistryException("Failed to compute content hash.", ex);
        }
    }


    private byte[] concatContentAndReferences(byte[] contentBytes, String references) throws IOException {
        if (references != null && !references.isEmpty()) {
            var referencesBytes = ContentHandle.create(references).bytes();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream(contentBytes.length + referencesBytes.length);
            outputStream.write(contentBytes);
            outputStream.write(referencesBytes);
            return outputStream.toByteArray();
        } else {
            throw new IllegalArgumentException("references");
        }
    }


    public String determineArtifactType(ContentHandle content, String artifactTypeHint) {
        return ArtifactTypeUtil.determineArtifactType(content, artifactTypeHint, null, factory.getAllArtifactTypes());
    }


    public EditableVersionMetaDataDto extractEditableArtifactMetadata(String artifactType, ContentHandle content) {
        var provider = factory.getArtifactTypeProvider(artifactType);
        var extractor = provider.getContentExtractor();
        var extractedMetadata = extractor.extract(content);
        if (extractedMetadata != null) {
            return EditableVersionMetaDataDto.builder()
                    .name(extractedMetadata.getName())
                    .description(extractedMetadata.getDescription())
                    .labels(extractedMetadata.getLabels())
                    .build();
        } else {
            return EditableVersionMetaDataDto.builder().build();
        }
    }


    public static boolean notEmpty(Collection<?> collection) {
        return collection != null && !collection.isEmpty();
    }
}
