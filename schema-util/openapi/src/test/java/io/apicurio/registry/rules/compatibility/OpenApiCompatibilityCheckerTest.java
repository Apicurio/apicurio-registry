package io.apicurio.registry.rules.compatibility;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.types.ContentTypes;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;


class OpenApiCompatibilityCheckerTest {
    private TypedContent toTypedContent(String content) {
        return TypedContent.create(ContentHandle.create(content), ContentTypes.APPLICATION_JSON);
    }

    private static final String BEFORE = """
        {
            "openapi": "3.0.0",
            "info": {
                "title": "Test API",
                "version": "1.0.0"
            },
            "paths": {
                "/test": {
                    "get": {
                        "responses": {
                            "200": {
                                "description": "OK"
                            }
                        }
                    }
                }
            }
        }
        """;
    private static final String AFTER_VALID = """
        {
            "openapi": "3.0.0",
            "info": {
                "title": "Test API",
                "version": "1.0.0"
            },
            "paths": {
                "/test": {
                    "get": {
                        "responses": {
                            "200": {
                                "description": "OK"
                            }
                        }
                    }
                },
                "/test2": {
                    "get": {
                        "responses": {
                            "200": {
                                "description": "OK"
                            }
                        }
                    }
                }
            }
        }
        """;
    private static final String AFTER_INVALID = """
        {
            "openapi": "3.0.0",
            "info": {
                "title": "Test API",
                "version": "1.0.0"
            },
            "paths": {}
        }
        """;

    @Test
    void givenBackwardCompatibleChange_whenCheckingCompatibility_thenReturnCompatibleResult() {
        OpenApiCompatibilityChecker checker = new OpenApiCompatibilityChecker();
        TypedContent existing = toTypedContent(BEFORE);
        TypedContent proposed = toTypedContent(AFTER_VALID);
        CompatibilityExecutionResult result = checker.testCompatibility(CompatibilityLevel.BACKWARD, Collections.singletonList(existing), proposed,
                Collections.emptyMap());

       Assertions.assertTrue(result.isCompatible());
    }

    @Test
    void givenIncompatibleChange_whenCheckingCompatibility_thenReturnIncompatibleResult() {
        OpenApiCompatibilityChecker checker = new OpenApiCompatibilityChecker();
        TypedContent existing = toTypedContent(BEFORE);
        TypedContent proposed = toTypedContent(AFTER_INVALID);
        CompatibilityExecutionResult result = checker.testCompatibility(CompatibilityLevel.BACKWARD, Collections.singletonList(existing), proposed,
                Collections.emptyMap());

       Assertions.assertFalse(result.isCompatible());
    }
}