package io.apicurio.registry.services.http;

import io.apicurio.registry.rest.v3.beans.Error;
import jakarta.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ErrorHttpResponse {

    private int status;
    private String contentType;
    private Error error;
    private Response jaxrsResponse;

}
