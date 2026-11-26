package io.apicurio.registry.maven;

import com.microsoft.kiota.ApiExceptionBuilder;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpStatusMessageTest {

    @Test
    void namesTheStatusOfAnApiExceptionInTheCauseChain() {
        var unauthorized = new ApiExceptionBuilder().withMessage("no body").withResponseStatusCode(401).build();
        assertEquals(" (HTTP 401)", AbstractRegistryMojo.httpStatus(unauthorized));
        assertEquals(" (HTTP 401)", AbstractRegistryMojo.httpStatus(
                new RuntimeException("wrapped", new CompletionException(unauthorized))));
    }

    @Test
    void nothingWithoutAStatus() {
        assertEquals("", AbstractRegistryMojo.httpStatus(new IllegalStateException("handshake failed")));
        assertEquals("", AbstractRegistryMojo.httpStatus(new ApiExceptionBuilder().withMessage("x").build()));
    }
}
