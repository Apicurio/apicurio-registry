package io.apicurio.registry.rules.compatibility.jsonschema;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;
import io.apicurio.registry.rules.compatibility.JsonSchemaCompatibilityChecker;
import io.apicurio.registry.types.ContentTypes;
import org.junit.jupiter.api.Test;

import java.util.Collections;

public class JsonSchemaCompatibilityCheckerTest {

	private TypedContent toTypedContent(String content) {
		return TypedContent.create(ContentHandle.create(content), ContentTypes.APPLICATION_JSON);
	}

	private static final String BEFORE = "{\r\n"
			+ "    \"$id\": \"https://example.com/blank.schema.json\",\r\n"
			+ "    \"$schema\": \"http://json-schema.org/draft-07/schema#\",\r\n"
			+ "    \"title\": \"Test JSON Schema\",\r\n"
			+ "    \"description\": \"\",\r\n"
			+ "    \"type\": \"object\",\r\n"
			+ "    \"properties\": {}\r\n"
			+ "}";
	private static final String AFTER_VALID = "{\r\n"
			+ "    \"$id\": \"https://example.com/blank.schema.json\",\r\n"
			+ "    \"$schema\": \"http://json-schema.org/draft-07/schema#\",\r\n"
			+ "    \"title\": \"Test JSON Schema\",\r\n"
			+ "    \"description\": \"A simple description added.\",\r\n"
			+ "    \"type\": \"object\",\r\n"
			+ "    \"properties\": {}\r\n"
			+ "}";
	private static final String AFTER_INVALID = "{\r\n"
			+ "    \"$id\": \"https://example.com/blank.schema.json\",\r\n"
			+ "    \"$schema\": \"http://json-schema.org/draft-07/schema#\",\r\n"
			+ "    \"title\": \"Test JSON Schema\",\r\n"
			+ "    \"description\": \"\",\r\n"
			+ "    \"type\": \"object\",\r\n"
			+ "    \"properties\": {\r\n"
			+ "        \"firstName\": {\r\n"
			+ "            \"type\": \"string\",\r\n"
			+ "            \"description\": \"The person's first name.\"\r\n"
			+ "        },\r\n"
			+ "        \"lastName\": {\r\n"
			+ "            \"type\": \"string\",\r\n"
			+ "            \"description\": \"The person's last name.\"\r\n"
			+ "        },\r\n"
			+ "        \"age\": {\r\n"
			+ "            \"description\": \"Age in years which must be equal to or greater than zero.\",\r\n"
			+ "            \"type\": \"integer\",\r\n"
			+ "            \"minimum\": 0\r\n"
			+ "        }\r\n"
			+ "    }\r\n"
			+ "}";
	

    @Test
	public void testJsonSchemaCompatibilityChecker() {
    	JsonSchemaCompatibilityChecker checker = new JsonSchemaCompatibilityChecker();
    	TypedContent existing = toTypedContent(BEFORE);
		TypedContent proposed = toTypedContent(AFTER_VALID);
		checker.testCompatibility(CompatibilityLevel.BACKWARD, Collections.singletonList(existing), proposed, Collections.emptyMap());
	}

    @Test
	public void testJsonSchemaCompatibilityChecker_Fail() {
    	JsonSchemaCompatibilityChecker checker = new JsonSchemaCompatibilityChecker();
		TypedContent existing = toTypedContent(BEFORE);
		TypedContent proposed = toTypedContent(AFTER_INVALID);
		checker.testCompatibility(CompatibilityLevel.BACKWARD, Collections.singletonList(existing), proposed, Collections.emptyMap());
	}
	
}
