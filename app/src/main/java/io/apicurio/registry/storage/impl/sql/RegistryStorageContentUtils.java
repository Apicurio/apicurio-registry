package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.types.RegistryException;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import io.apicurio.registry.util.ArtifactTypeUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * TODO Refactor TODO Cache calls to referenceResolver
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
    public TypedContent canonicalizeContent(String artifactType, TypedContent content,
            Map<String, TypedContent> resolvedReferences) {
        try {
            return factory.getArtifactTypeProvider(artifactType).getContentCanonicalizer()
                    .canonicalize(content, resolvedReferences);
        } catch (Exception ex) {
            // TODO: We should consider explicitly failing when a content could not be canonicalized.
            // throw new RegistryException("Failed to canonicalize content.", ex);
            log.debug("Failed to canonicalize content: {}", artifactType);
            return content;
        }
    }

    /**
     * Canonicalize the given content.
     *
     * @throws RegistryException in the case of an error.
     */
    public TypedContent canonicalizeContent(String artifactType, TypedContent content,
            List<ArtifactReferenceDto> references,
            Function<List<ArtifactReferenceDto>, Map<String, TypedContent>> referenceResolver) {
        try {
            return canonicalizeContent(artifactType, content, referenceResolver.apply(references));
        } catch (Exception ex) {
            throw new RegistryException("Failed to canonicalize content.", ex);
        }
    }

    /**
     * @param references may be null
     * @param referenceResolver may be null if references is null
     */
    public String getCanonicalContentHash(TypedContent content, String artifactType,
            List<ArtifactReferenceDto> references,
            Function<List<ArtifactReferenceDto>, Map<String, TypedContent>> referenceResolver) {
        try {
            if (notEmpty(references)) {
                String referencesSerialized = RegistryContentUtils.serializeReferences(references);
                TypedContent canonicalContent = canonicalizeContent(artifactType, content,
                        referenceResolver.apply(references));
                return DigestUtils.sha256Hex(concatContentAndReferences(canonicalContent.getContent().bytes(),
                        referencesSerialized));
            } else {
                TypedContent canonicalContent = canonicalizeContent(artifactType, content, Map.of());
                return DigestUtils.sha256Hex(canonicalContent.getContent().bytes());
            }
        } catch (IOException ex) {
            throw new RegistryException("Failed to compute canonical content hash.", ex);
        }
    }

    /**
     * @param references may be null
     */
    public String getContentHash(TypedContent content, List<ArtifactReferenceDto> references) {
        try {
            if (notEmpty(references)) {
                String referencesSerialized = RegistryContentUtils.serializeReferences(references);
                return DigestUtils.sha256Hex(
                        concatContentAndReferences(content.getContent().bytes(), referencesSerialized));
            } else {
                return DigestUtils.sha256Hex(content.getContent().bytes());
            }
        } catch (IOException ex) {
            throw new RegistryException("Failed to compute content hash.", ex);
        }
    }

    private byte[] concatContentAndReferences(byte[] contentBytes, String references) throws IOException {
        if (references != null && !references.isEmpty()) {
            var referencesBytes = ContentHandle.create(references).bytes();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream(
                    contentBytes.length + referencesBytes.length);
            outputStream.write(contentBytes);
            outputStream.write(referencesBytes);
            return outputStream.toByteArray();
        } else {
            throw new IllegalArgumentException("references");
        }
    }

    public String determineArtifactType(TypedContent content, String artifactTypeHint) {
        return ArtifactTypeUtil.determineArtifactType(content, artifactTypeHint, null, factory);
    }

    public String determineArtifactType(TypedContent content, String artifactTypeHint,
            Map<String, TypedContent> resolvedReferences) {
        return ArtifactTypeUtil.determineArtifactType(content, artifactTypeHint, resolvedReferences, factory);
    }

    public static boolean notEmpty(Collection<?> collection) {
        return collection != null && !collection.isEmpty();
    }
}
