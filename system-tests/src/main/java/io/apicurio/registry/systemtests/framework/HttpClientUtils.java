package io.apicurio.registry.systemtests.framework;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

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

    public static SSLContext getInsecureContext() {
        TrustManager[] noopTrustManager = new TrustManager[]{
                new X509TrustManager() {
                    public void checkClientTrusted(X509Certificate[] xcs, String string) {}
                    public void checkServerTrusted(X509Certificate[] xcs, String string) {}
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }
                }
        };
        try {
            SSLContext sc = SSLContext.getInstance("ssl");
            sc.init(null, noopTrustManager, null);
            return sc;
        } catch (KeyManagementException | NoSuchAlgorithmException ex) {
            return null;
        }
    }
}
