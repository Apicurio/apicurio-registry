package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.content.extract.StructuredElement;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.util.ContentTypeUtil;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.IOException;

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
     * Width of the existing elementValue column. Equality keys occupy 64 hexadecimal characters;
     * the wider column is retained so fresh and upgrade DDL remain consistent. It is not a limit
     * on the length of identifiers in artifact content or search requests.
     */
    public static final int MAX_ELEMENT_VALUE_LENGTH = 256;

    private StructuredContentIndexUtils() {
    }

    /** OpenAPI/AsyncAPI extractors expect JSON, while registry content may be YAML. */
    public static ContentHandle extractionContent(String artifactType, ContentHandle content) throws IOException {
        if (ArtifactType.OPENAPI.equals(artifactType) || ArtifactType.ASYNCAPI.equals(artifactType)) {
            return ContentHandle.create(ContentTypeUtil.parseJsonOrYaml(
                    TypedContent.create(content, ContentTypes.APPLICATION_YAML)).toString());
        }
        return content;
    }

    /**
     * Builds the {@code elementType} value for a structured element: {@code "<artifactType>:<kind>"},
     * lower-cased so matching is case-insensitive and consistent with the Elasticsearch backend.
     */
    public static String elementType(String artifactType, String kind) {
        return limitStr(asLowerCase(artifactType + ":" + kind), MAX_ELEMENT_TYPE_LENGTH);
    }

    /**
     * Builds a bounded equality key from the entire normalized value. Hash every value, including
     * short ones, so a literal string resembling a digest cannot alias a long identifier. Original
     * values remain in artifact content; live writes, backfill and queries must all use this method.
     */
    public static String elementValue(String name) {
        return DigestUtils.sha256Hex(asLowerCase(name));
    }

    /**
     * Key used to de-duplicate rows before insert. It mirrors the table's primary key, and is computed
     * from the normalized type and full-value digest. Long names sharing a prefix remain distinct.
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
