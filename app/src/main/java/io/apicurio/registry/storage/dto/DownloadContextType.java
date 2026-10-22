package io.apicurio.registry.storage.dto;

/**
 * Defines the types of download contexts available for retrieving content or data exports from the registry.
 */
public enum DownloadContextType {

    /** A full registry data export. */
    EXPORT,

    /** Download content by the version's global ID. */
    CONTENT_BY_GLOBAL_ID,

    /** Download content by content ID. */
    CONTENT_BY_CONTENT_ID,

    /** Download content by content hash. */
    CONTENT_BY_CONTENT_HASH,

    /** Export a specific artifact version. */
    VERSION_EXPORT

}
