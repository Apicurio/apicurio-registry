package io.apicurio.registry.utils.protobuf.schema;

/**
 * Centralized utility class for detecting and categorizing well-known protobuf types.
 *
 * This consolidates the well-known type detection logic that was previously duplicated
 * across multiple files in the codebase.
 *
 * <h2>Type Categories:</h2>
 * <ul>
 *   <li><b>Google Protobuf Types</b> (google/protobuf/*): Core well-known types like
 *       Timestamp, Duration, Any, etc. These are handled internally by protobuf4j's
 *       ensureWellKnownTypes() and should NOT be loaded explicitly.</li>
 *   <li><b>Google API Types</b> (google/type/*): Common types from googleapis like
 *       Money, Date, TimeOfDay, etc. These are loaded from resources by ProtobufSchemaLoader.</li>
 *   <li><b>Apicurio Bundled Types</b> (metadata/*, additionalTypes/*): Custom types
 *       bundled with Apicurio Registry for schema metadata and decimal support.</li>
 * </ul>
 */
public final class ProtobufWellKnownTypes {

    // Google Protocol Buffer well-known types (handled by protobuf4j internally)
    private static final String GOOGLE_PROTOBUF_PREFIX = "google/protobuf/";

    // Google Protocol Buffer package name
    private static final String GOOGLE_PROTOBUF_PACKAGE = "google.protobuf";

    // Google API types (loaded from resources by ProtobufSchemaLoader)
    private static final String GOOGLE_TYPE_PREFIX = "google/type/";

    // Google API package name
    private static final String GOOGLE_TYPE_PACKAGE = "google.type";

    // Apicurio custom bundled types
    private static final String METADATA_PREFIX = "metadata/";
    private static final String ADDITIONAL_TYPES_PREFIX = "additionalTypes/";

    // Apicurio package names
    private static final String METADATA_PACKAGE = "metadata";
    private static final String ADDITIONAL_TYPES_PACKAGE = "additionalTypes";

    private ProtobufWellKnownTypes() {
        // Utility class - prevent instantiation
    }

    /**
     * Check if a file path represents a Google Protocol Buffer well-known type.
     * These types (google/protobuf/*) are handled internally by protobuf4j.
     *
     * @param fileName The proto file path (e.g., "google/protobuf/timestamp.proto")
     * @return true if this is a Google Protobuf well-known type
     */
    public static boolean isGoogleProtobufType(String fileName) {
        return fileName != null && fileName.startsWith(GOOGLE_PROTOBUF_PREFIX);
    }

    /**
     * Check if a file path represents a Google API type.
     * These types (google/type/*) are loaded from resources by ProtobufSchemaLoader.
     *
     * @param fileName The proto file path (e.g., "google/type/money.proto")
     * @return true if this is a Google API type
     */
    public static boolean isGoogleApiType(String fileName) {
        return fileName != null && fileName.startsWith(GOOGLE_TYPE_PREFIX);
    }

    /**
     * Check if a file path represents an Apicurio bundled type.
     * These include metadata protos and additional types like decimal.
     *
     * @param fileName The proto file path (e.g., "metadata/metadata.proto")
     * @return true if this is an Apicurio bundled type
     */
    public static boolean isApicurioBundledType(String fileName) {
        return fileName != null &&
               (fileName.startsWith(METADATA_PREFIX) || fileName.startsWith(ADDITIONAL_TYPES_PREFIX));
    }

    /**
     * Check if a file path represents any well-known type that should be filtered out
     * when processing references or dependencies.
     *
     * This includes:
     * <ul>
     *   <li>Google Protobuf types (google/protobuf/*)</li>
     *   <li>Google API types (google/type/*)</li>
     *   <li>Apicurio bundled types (metadata/*, additionalTypes/*)</li>
     * </ul>
     *
     * @param fileName The proto file path
     * @return true if this is any type of well-known/bundled proto
     */
    public static boolean isWellKnownType(String fileName) {
        return isGoogleProtobufType(fileName) ||
               isGoogleApiType(fileName) ||
               isApicurioBundledType(fileName);
    }

    /**
     * Check if a file path represents a type that is handled internally by protobuf4j.
     * Currently, only Google Protobuf well-known types (google/protobuf/*) are handled
     * by protobuf4j's ensureWellKnownTypes().
     *
     * @param fileName The proto file path
     * @return true if protobuf4j handles this type internally
     */
    public static boolean isHandledByProtobuf4j(String fileName) {
        return isGoogleProtobufType(fileName);
    }

    /**
     * Check if a file path should be skipped when processing schema references.
     * This is used by serdes and other components that need to filter out
     * types that don't need to be stored as explicit references.
     *
     * Types to skip include:
     * <ul>
     *   <li>Google Protobuf types - handled by protobuf4j</li>
     *   <li>Google API types - bundled with the registry</li>
     * </ul>
     *
     * @param fileName The proto file path
     * @return true if this reference should be skipped
     */
    public static boolean shouldSkipAsReference(String fileName) {
        return isGoogleProtobufType(fileName) || isGoogleApiType(fileName);
    }

    /**
     * Check if a package name represents the Google Protobuf package.
     *
     * @param packageName The package name (e.g., "google.protobuf")
     * @return true if this is the Google Protobuf package
     */
    public static boolean isGoogleProtobufPackage(String packageName) {
        return GOOGLE_PROTOBUF_PACKAGE.equals(packageName);
    }

    /**
     * Check if a package name represents the Google API types package.
     *
     * @param packageName The package name (e.g., "google.type")
     * @return true if this is the Google API types package
     */
    public static boolean isGoogleTypePackage(String packageName) {
        return GOOGLE_TYPE_PACKAGE.equals(packageName);
    }

    /**
     * Check if a package name is one of the Apicurio bundled type packages.
     *
     * @param packageName The package name
     * @return true if this is an Apicurio bundled package
     */
    public static boolean isApicurioBundledPackage(String packageName) {
        return METADATA_PACKAGE.equals(packageName) || ADDITIONAL_TYPES_PACKAGE.equals(packageName);
    }

    /**
     * Check if a package name conflicts with bundled protos that are pre-loaded
     * in the compilation context pool. If a user schema uses one of these packages,
     * we cannot use a pooled context (which has these protos pre-loaded) without
     * causing duplicate definition errors.
     *
     * @param packageName The package name
     * @return true if this package conflicts with bundled protos
     */
    public static boolean isBundledPackage(String packageName) {
        return isApicurioBundledPackage(packageName);
    }

    /**
     * Get the metadata package name.
     *
     * @return The metadata package name
     */
    public static String getMetadataPackage() {
        return METADATA_PACKAGE;
    }

    /**
     * Get the additional types (decimal) package name.
     *
     * @return The additional types package name
     */
    public static String getAdditionalTypesPackage() {
        return ADDITIONAL_TYPES_PACKAGE;
    }

    /**
     * Check if schema content defines a Google well-known type (package google.protobuf).
     * These types are provided internally by protobuf4j and cannot be compiled separately
     * without causing duplicate definition errors.
     *
     * @param schemaContent The proto schema content
     * @return true if the schema defines a google.protobuf type
     */
    public static boolean isGoogleProtobufSchema(String schemaContent) {
        if (schemaContent == null) {
            return false;
        }
        // Check for package google.protobuf declaration
        // This regex matches "package google.protobuf;" with optional whitespace
        return schemaContent.matches("(?s).*\\bpackage\\s+google\\.protobuf\\s*;.*");
    }
}
