package io.apicurio.registry.openapi.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.util.ContentTypeUtil;
import tools.jackson.core.TreeNode;

import java.io.IOException;

/**
 * Bridge between the registry, which uses Jackson 3, and apitomy-data-models, whose API only accepts and
 * returns Jackson 2 trees. Content is parsed with {@link ContentTypeUtil} (so JSON/YAML detection is the
 * same as everywhere else) and then converted to a Jackson 2 tree.
 */
public final class DataModelsJson {

    private static final ObjectMapper JACKSON2_MAPPER = new ObjectMapper();

    private DataModelsJson() {
    }

    /**
     * Parses JSON or YAML content into a Jackson 2 tree that can be passed to apitomy-data-models.
     */
    public static JsonNode parseJsonOrYaml(TypedContent content) throws IOException {
        return toJackson2(ContentTypeUtil.parseJsonOrYaml(content));
    }

    /**
     * Converts a Jackson 3 tree ({@code tools.jackson.databind.JsonNode}) to a Jackson 2 tree.
     */
    public static JsonNode toJackson2(TreeNode node) throws IOException {
        return JACKSON2_MAPPER.readTree(node.toString());
    }
}
