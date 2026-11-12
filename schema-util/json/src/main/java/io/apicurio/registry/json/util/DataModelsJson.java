package io.apicurio.registry.json.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.apitomy.datamodels.ModelTypeDetector;
import io.apitomy.datamodels.models.ModelType;
import tools.jackson.core.TreeNode;

import java.io.IOException;

/**
 * Bridge to apitomy-data-models, whose API only accepts Jackson 2 trees, from code that uses Jackson 3.
 */
public final class DataModelsJson {

    private static final ObjectMapper JACKSON2_MAPPER = new ObjectMapper();

    private DataModelsJson() {
    }

    /**
     * Converts a Jackson 3 tree ({@code tools.jackson.databind.JsonNode}) to a Jackson 2 tree.
     */
    public static JsonNode toJackson2(TreeNode node) throws IOException {
        return JACKSON2_MAPPER.readTree(node.toString());
    }

    /**
     * {@link ModelTypeDetector#discoverModelType(ObjectNode)} for a Jackson 3 object node.
     */
    public static ModelType discoverModelType(TreeNode objectNode) throws IOException {
        return ModelTypeDetector.discoverModelType((ObjectNode) toJackson2(objectNode));
    }
}
