package io.apicurio.registry.storage;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Ales Justin
 */
public class MetaDataKeys {
    public static String ARTIFACT_ID = "artifact_id";
    public static String CONTENT = "content";
    public static String GLOBAL_ID = "global_id";
    public static String VERSION = "version";

    // Internal

    public static String DELETED = "_deleted";

    // Helpers

    public static Map<String, String> toMetaData(Map<String, String> content) {
        Map<String, String> copy = new HashMap<>(content);
        copy.remove(CONTENT);
        copy.remove(DELETED);
        return copy;
    }
}
