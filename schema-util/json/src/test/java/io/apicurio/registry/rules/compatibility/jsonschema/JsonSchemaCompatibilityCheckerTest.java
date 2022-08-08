/*
 * Copyright 2022 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.rules.compatibility.jsonschema;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;
import io.apicurio.registry.rules.compatibility.JsonSchemaCompatibilityChecker;

public class JsonSchemaCompatibilityCheckerTest {
	
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
    	ContentHandle existing = ContentHandle.create(BEFORE);
    	ContentHandle proposed = ContentHandle.create(AFTER_VALID);
		checker.testCompatibility(CompatibilityLevel.BACKWARD, Collections.singletonList(existing), proposed);
	}

    @Test
	public void testJsonSchemaCompatibilityChecker_Fail() {
    	JsonSchemaCompatibilityChecker checker = new JsonSchemaCompatibilityChecker();
    	ContentHandle existing = ContentHandle.create(BEFORE);
    	ContentHandle proposed = ContentHandle.create(AFTER_INVALID);
		checker.testCompatibility(CompatibilityLevel.BACKWARD, Collections.singletonList(existing), proposed);
	}
	
}
