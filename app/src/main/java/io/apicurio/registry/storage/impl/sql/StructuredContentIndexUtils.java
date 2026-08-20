package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.content.extract.StructuredElement;

import static io.apicurio.registry.utils.StringUtil.asLowerCase;
import static io.apicurio.registry.utils.StringUtil.limitStr;

/**
 * Normalization shared by the two writers of the artifact_structured_content table: the live write path
 * ({@code AbstractSqlRegistryStorage}) and the database-upgrade backfill
 * ({@code StructuredContentUpgrader}). Both must produce byte-identical rows for the same content -
 * otherwise an agent card would be searchable after an upload but not after an upgrade backfill (or
 * vice versa) - so the column widths and the lower-casing live here rather than being repeated.
 */
public final class StructuredContentIndexUtils {

    /**
     * Maximum length of the {@code elementType} column. Must match {@code VARCHAR(64)} in the DDLs.
     */
    public static final int MAX_ELEMENT_TYPE_LENGTH = 64;

    /**
     * Maximum length of the {@code elementValue} column. Must match {@code VARCHAR(256)} in the DDLs.
     * <p>
     * All four columns form the table's primary key, so this width is bounded by the smallest index key
     * limit across the supported databases: MySQL InnoDB allows 3072 bytes, and {@code elementValue} is
     * the one utf8mb4 column (4 bytes per character), which leaves 256 characters once the ascii
     * {@code groupId} (512), {@code artifactId} (512) and {@code elementType} (64) are accounted for.
     */
    public static final int MAX_ELEMENT_VALUE_LENGTH = 256;

    private StructuredContentIndexUtils() {
    }

    /**
     * Builds the {@code elementType} value for a structured element: {@code "<artifactType>:<kind>"},
     * lower-cased so matching is case-insensitive and consistent with the Elasticsearch backend.
     */
    public static String elementType(String artifactType, String kind) {
        return limitStr(asLowerCase(artifactType + ":" + kind), MAX_ELEMENT_TYPE_LENGTH);
    }

    /**
     * Builds the {@code elementValue} value for a structured element, lower-cased for the same reason as
     * {@link #elementType(String, String)}.
     */
    public static String elementValue(String name) {
        return limitStr(asLowerCase(name), MAX_ELEMENT_VALUE_LENGTH);
    }

    /**
     * Key used to de-duplicate rows before insert. It mirrors the table's primary key, and is computed
     * from the already-normalized values so two element names that only differ beyond
     * {@link #MAX_ELEMENT_VALUE_LENGTH} collapse into a single row instead of failing the insert.
     */
    public static String rowKey(String elementType, String elementValue) {
        return elementType + ":" + elementValue;
    }

    /**
     * Returns true when the element can be written to the table. {@code elementType} and
     * {@code elementValue} are NOT NULL, and on some databases (e.g. PostgreSQL) a failed statement
     * aborts the surrounding transaction - which is the artifact-write transaction on the live path -
     * so malformed elements are dropped rather than attempted.
     */
    public static boolean isIndexable(StructuredElement element) {
        return element != null && element.kind() != null && element.name() != null;
    }
}
