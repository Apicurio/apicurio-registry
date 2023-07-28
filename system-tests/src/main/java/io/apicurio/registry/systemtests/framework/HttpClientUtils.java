package io.apicurio.registry.systemtests.framework;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class HttpClientUtils {
    public static URI buildURI(String uri, Object... args) {
        try {
            return new URI(String.format(uri, args));
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public static HttpResponse<String> processRequest(HttpRequest request) {
        try {
            return HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static HttpRequest.Builder newBuilder() {
        return HttpRequest.newBuilder().version(HttpClient.Version.HTTP_1_1);
    }
}
