package io.apicurio.registry.util;

import io.apicurio.registry.content.ContentAccepter;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.storage.error.InvalidArtifactTypeException;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProvider;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class ArtifactTypeUtil {

    /**
     * Constructor.
     */
    private ArtifactTypeUtil() {
    }

    /**
     * Figures out the artifact type in the following order of precedent:
     * <p>
     * 1) The type provided in the request 2) Determined from the content itself
     *
     * @param content the content
     * @param artifactType the artifact type
     */
    public static String determineArtifactType(TypedContent content, String artifactType,
            ArtifactTypeUtilProviderFactory artifactTypeProviderFactory) {
        return determineArtifactType(content, artifactType, Collections.emptyMap(),
                artifactTypeProviderFactory);
    }

    public static String determineArtifactType(TypedContent content, String artifactType,
            Map<String, TypedContent> resolvedReferences,
            ArtifactTypeUtilProviderFactory artifactTypeProviderFactory) {
        if ("".equals(artifactType)) {
            artifactType = null;
        }
        if (artifactType == null && content != null) {
            artifactType = ArtifactTypeUtil.discoverType(content, resolvedReferences,
                    artifactTypeProviderFactory);
        }
        if (!artifactTypeProviderFactory.getAllArtifactTypes().contains(artifactType)) {
            throw new InvalidArtifactTypeException("Invalid or unknown artifact type: " + artifactType);
        }
        return artifactType;
    }

    // TODO: should we move this to ArtifactTypeUtilProvider and make this logic injectable? yes!
    // as a first implementation forcing users to specify the type if its custom sounds like a reasonable
    // tradeoff
    /**
     * Method that discovers the artifact type from the raw content of an artifact. This will attempt to parse
     * the content (with the optional provided Content Type as a hint) and figure out what type of artifact it
     * is. Examples include Avro, Protobuf, OpenAPI, etc. Most of the supported artifact types are JSON
     * formatted. So in these cases we will need to look for some sort of type-specific marker in the content
     * of the artifact. The method does its best to figure out the type, but will default to Avro if all else
     * fails.
     * 
     * @param content
     * @param resolvedReferences
     */
    @SuppressWarnings("deprecation")
    private static String discoverType(TypedContent content, Map<String, TypedContent> resolvedReferences,
            ArtifactTypeUtilProviderFactory artifactTypeProviderFactory) throws InvalidArtifactTypeException {
        for (ArtifactTypeUtilProvider provider : artifactTypeProviderFactory.getAllArtifactTypeProviders()) {
            ContentAccepter contentAccepter = provider.getContentAccepter();
            if (contentAccepter.acceptsContent(content, resolvedReferences)) {
                return provider.getArtifactType();
            }
        }

        throw new InvalidArtifactTypeException("Failed to discover artifact type from content.");
    }

    /**
     * Detects the artifact type from a file extension by querying all registered providers.
     * If exactly one provider claims the extension, its type is returned. If multiple providers
     * claim it, the extension is treated as ambiguous and null is returned (fall back to content
     * detection). If no provider claims it, null is returned.
     *
     * @param fileName the file name or path
     * @param factory the provider factory to query
     * @return the artifact type, or null if the extension is unrecognized or ambiguous
     */
    public static String detectArtifactTypeFromExtension(String fileName,
            ArtifactTypeUtilProviderFactory factory) {
        final int dotIndex = fileName.toLowerCase(Locale.ROOT).lastIndexOf('.');
        if (dotIndex < 0) {
            return null;
        }
        final String extension = fileName.substring(dotIndex).toLowerCase(Locale.ROOT);
        String matched = null;
        for (ArtifactTypeUtilProvider provider : factory.getAllArtifactTypeProviders()) {
            if (provider.getFileExtensions().contains(extension)) {
                if (matched != null) {
                    return null;
                }
                matched = provider.getArtifactType();
            }
        }
        return matched;
    }

    /**
     * Returns all file extensions supported by registered providers, for use in error messages.
     *
     * @param factory the provider factory to query
     * @return all supported file extensions
     */
    public static Set<String> getAllSupportedFileExtensions(ArtifactTypeUtilProviderFactory factory) {
        return factory.getAllArtifactTypeProviders().stream()
                .flatMap(p -> p.getFileExtensions().stream())
                .collect(Collectors.toSet());
    }
}
