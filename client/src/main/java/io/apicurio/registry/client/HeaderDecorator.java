package io.apicurio.registry.client;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;

public class HeaderDecorator implements ClientRequestFilter {

    public static final String AUTHORIZATION = "Authorization";
    private static String token;

    public static String getToken() {
        return token;
    }

    public static void setToken(String token) {
        HeaderDecorator.token = token;
    }

    @Override
    public void filter(ClientRequestContext requestContext) {
        requestContext.getHeaders().add(AUTHORIZATION, getToken());
    }
}
