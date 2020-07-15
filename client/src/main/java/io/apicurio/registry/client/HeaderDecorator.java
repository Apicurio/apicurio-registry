package io.apicurio.registry.client;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import java.io.IOException;

public class HeaderDecorator implements ClientRequestFilter {

    public static final String AUTHORIZATION = "Authorization";

    @Override
    public void filter(ClientRequestContext requestContext) throws IOException {
        requestContext.getHeaders().add(AUTHORIZATION, RegistryClient.getToken());
    }

}
