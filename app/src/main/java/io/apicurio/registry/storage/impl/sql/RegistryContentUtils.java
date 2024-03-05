package io.apicurio.registry.storage.impl.sql;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.ContentAndReferencesDto;
import io.apicurio.registry.types.RegistryException;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import io.apicurio.registry.types.provider.DefaultArtifactTypeUtilProviderImpl;
import io.apicurio.registry.utils.StringUtil;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.Function;


public class RegistryContentUtils {

    private static final Logger log = LoggerFactory.getLogger(RegistryContentUtils.class);

    private static final String NULL_GROUP_ID = "__$GROUPID$__";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static final ArtifactTypeUtilProviderFactory ARTIFACT_TYPE_UTIL = new DefaultArtifactTypeUtilProviderImpl();


    private RegistryContentUtils() {
    }


    /**
     * Recursively resolve the references.
     */
    public static Map<String, ContentHandle> recursivelyResolveReferences(List<ArtifactReferenceDto> references, Function<ArtifactReferenceDto, ContentAndReferencesDto> loader) {
        if (references == null || references.isEmpty()) {
            return Map.of();
        } else {
            Map<String, ContentHandle> result = new LinkedHashMap<>();
            resolveReferences(result, references, loader);
            return result;
        }
    }


    private static void resolveReferences(Map<String, ContentHandle> partialRecursivelyResolvedReferences, List<ArtifactReferenceDto> references, Function<ArtifactReferenceDto, ContentAndReferencesDto> loader) {
        if (references != null && !references.isEmpty()) {
            for (ArtifactReferenceDto reference : references) {
                if (reference.getArtifactId() == null || reference.getName() == null || reference.getVersion() == null) {
                    throw new IllegalStateException("Invalid reference: " + reference);
                } else {
                    if (!partialRecursivelyResolvedReferences.containsKey(reference.getName())) {
                        try {
                            var nested = loader.apply(reference);
                            if (nested != null) {
                                resolveReferences(partialRecursivelyResolvedReferences, nested.getReferences(), loader);
                                partialRecursivelyResolvedReferences.put(reference.getName(), nested.getContent());
                            }
                        } catch (Exception ex) {
                            log.error("Could not resolve reference " + reference + ".", ex);
                        }
                    }
                }
            }
        }
    }


    /**
     * Canonicalize the given content.
     * <p>
     * WARNING: Fails silently.
     */
    private static ContentHandle canonicalizeContent(String artifactType, ContentHandle content, Map<String, ContentHandle> recursivelyResolvedReferences) {
        try {
            return ARTIFACT_TYPE_UTIL.getArtifactTypeProvider(artifactType)
                    .getContentCanonicalizer()
                    .canonicalize(content, recursivelyResolvedReferences);
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
    public static ContentHandle canonicalizeContent(String artifactType, ContentAndReferencesDto data, Function<ArtifactReferenceDto, ContentAndReferencesDto> loader) {
        try {
            return canonicalizeContent(artifactType, data.getContent(), recursivelyResolveReferences(data.getReferences(), loader));
        } catch (Exception ex) {
            throw new RegistryException("Failed to canonicalize content.", ex);
        }
    }


    /**
     * @param loader can be null *if and only if* references are empty.
     */
    public static String canonicalContentHash(String artifactType, ContentAndReferencesDto data, Function<ArtifactReferenceDto, ContentAndReferencesDto> loader) {
        try {
            if (notEmpty(data.getReferences())) {
                String serializedReferences = serializeReferences(data.getReferences());
                ContentHandle canonicalContent = canonicalizeContent(artifactType, data, loader);
                return DigestUtils.sha256Hex(concatContentAndReferences(canonicalContent.bytes(), serializedReferences));
            } else {
                ContentHandle canonicalContent = canonicalizeContent(artifactType, data.getContent(), Map.of());
                return DigestUtils.sha256Hex(canonicalContent.bytes());
            }
        } catch (IOException ex) {
            throw new RegistryException("Failed to compute canonical content hash.", ex);
        }
    }


    /**
     * data.references may be null
     */
    public static String contentHash(ContentAndReferencesDto data) {
        try {
            if (notEmpty(data.getReferences())) {
                String serializedReferences = serializeReferences(data.getReferences());
                return DigestUtils.sha256Hex(concatContentAndReferences(data.getContent().bytes(), serializedReferences));
            } else {
                return data.getContent().getSha256Hash();
            }
        } catch (IOException ex) {
            throw new RegistryException("Failed to compute content hash.", ex);
        }
    }


    private static byte[] concatContentAndReferences(byte[] contentBytes, String serializedReferences) throws IOException {
        if (serializedReferences != null && !serializedReferences.isEmpty()) {
            var serializedReferencesBytes = ContentHandle.create(serializedReferences).bytes();
            var bytes = ByteBuffer.allocate(contentBytes.length + serializedReferencesBytes.length);
            bytes.put(contentBytes);
            bytes.put(serializedReferencesBytes);
            return bytes.array();
        } else {
            throw new IllegalArgumentException("serializedReferences is null or empty");
        }
    }


    /**
     * Serializes the given collection of labels to a string for artifactStore in the DB.
     *
     * @param labels
     */
    public static String serializeLabels(List<String> labels) {
        try {
            if (labels == null) {
                return null;
            }
            if (labels.isEmpty()) {
                return null;
            }
            return MAPPER.writeValueAsString(labels);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Deserialize the labels from their string form to a <code>List&lt;String&gt;</code> form.
     *
     * @param labelsStr
     */
    @SuppressWarnings("unchecked")
    public static List<String> deserializeLabels(String labelsStr) {
        try {
            if (StringUtil.isEmpty(labelsStr)) {
                return null;
            }
            return MAPPER.readValue(labelsStr, List.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Serializes the given collection of properties to a string for artifactStore in the DB.
     *
     * @param properties
     */
    public static String serializeProperties(Map<String, String> properties) {
        try {
            if (properties == null) {
                return null;
            }
            if (properties.isEmpty()) {
                return null;
            }
            return MAPPER.writeValueAsString(properties);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Deserialize the properties from their string form to a Map<String, String> form.
     *
     * @param propertiesStr
     */
    @SuppressWarnings("unchecked")
    public static Map<String, String> deserializeProperties(String propertiesStr) {
        try {
            if (StringUtil.isEmpty(propertiesStr)) {
                return null;
            }
            return MAPPER.readValue(propertiesStr, Map.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Serializes the given collection of references to a string for artifactStore in the DB.
     *
     * @param references
     */
    public static String serializeReferences(List<ArtifactReferenceDto> references) {
        try {
            if (references == null || references.isEmpty()) {
                return null;
            }
            return MAPPER.writeValueAsString(references);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Deserialize the references from their string form to a List<ArtifactReferenceDto> form.
     *
     * @param references
     */
    public static List<ArtifactReferenceDto> deserializeReferences(String references) {
        try {
            if (StringUtil.isEmpty(references)) {
                return Collections.emptyList();
            }
            return MAPPER.readValue(references, new TypeReference<List<ArtifactReferenceDto>>() {
            });
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }


    public static String normalizeGroupId(String groupId) {
        if (groupId == null || "default".equals(groupId)) {
            return NULL_GROUP_ID;
        }
        return groupId;
    }


    public static String denormalizeGroupId(String groupId) {
        if (NULL_GROUP_ID.equals(groupId)) {
            return null;
        }
        return groupId;
    }


    public static boolean notEmpty(Collection<?> collection) {
        return collection != null && !collection.isEmpty();
    }
}
