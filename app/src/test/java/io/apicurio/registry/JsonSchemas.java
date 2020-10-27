package io.apicurio.registry;

/**
 * Created by aohana
 */
public class JsonSchemas {

    public static final String jsonSchema = "{\n" +
            "  \"type\": \"object\",\n" +
            "  \"properties\": {\n" +
            "    \"age\": {\n" +
            "      \"description\": \"Age in years which must be equal to or greater than zero.\",\n" +
            "      \"type\": \"integer\",\n" +
            "      \"minimum\": 0\n" +
            "    },\n" +
            "    \"zipcode\": {\n" +
            "      \"description\": \"ZipCode\",\n" +
            "      \"type\": \"integer\"\n" +
            "    }\n" +
            "  }\n" +
            "}";

    public static final String incompatibleJsonSchema = "{\n" +
            "  \"type\": \"object\",\n" +
            "  \"properties\": {\n" +
            "    \"age\": {\n" +
            "      \"description\": \"Age in years which must be equal to or greater than zero.\",\n" +
            "      \"type\": \"string\",\n" +
            "      \"minimum\": 0\n" +
            "    },\n" +
            "    \"zipcode\": {\n" +
            "      \"description\": \"ZipCode\",\n" +
            "      \"type\": \"string\"\n" +
            "    }\n" +
            "  }\n" +
            "}";
}
