package io.apicurio.registry.resolver.cache;

import com.microsoft.kiota.ApiException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ERCacheRegistryErrorTest {

    @Test
    void describeRegistryErrorIncludesHttpStatusFromApiException() {
        ApiException apiException = new TestApiException(409,
                "RuleViolationProblemDetails: BACKWARD incompatible");

        String description = ERCache.describeRegistryError(apiException);
        assertTrue(description.contains("409"));
        assertTrue(description.contains("BACKWARD incompatible"));
    }

    @Test
    void describeRegistryErrorFindsApiExceptionInNestedCauseChain() {
        ApiException apiException = new TestApiException(500, "internal error");
        RuntimeException nested = new RuntimeException("outer",
                new ExecutionException(apiException));

        String description = ERCache.describeRegistryError(nested);
        assertTrue(description.contains("500"));
        assertTrue(description.contains("internal error"));
    }

    private static class TestApiException extends ApiException {
        TestApiException(int statusCode, String message) {
            super(message);
            setResponseStatusCode(statusCode);
        }
    }
}
