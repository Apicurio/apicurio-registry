package io.apicurio.registry.tls;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

@QuarkusTest
@TestProfile(HttpSslProtocolsTestProfile.class)
public class HttpSslRuntimeTest {

    @TestHTTPResource(value = "/apis", tls = true)
    URL httpsUrl;

    @Test
    public void testTlsProtocolRestrictionAtRuntime() throws Exception {
        // Trust manager that trusts self-signed certs
        TrustManager[] trustAllCerts = new TrustManager[]{
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return null; }
                public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                public void checkServerTrusted(X509Certificate[] certs, String authType) {}
            }
        };

        SSLContext sslContext13 = SSLContext.getInstance("TLSv1.3");
        sslContext13.init(null, trustAllCerts, new SecureRandom());

        SSLContext sslContext12 = SSLContext.getInstance("TLSv1.2");
        sslContext12.init(null, trustAllCerts, new SecureRandom());

        String host = httpsUrl.getHost();
        int port = httpsUrl.getPort();

        // 1. Connection with TLSv1.3 should succeed
        SSLSocketFactory factory13 = sslContext13.getSocketFactory();
        try (SSLSocket socket = (SSLSocket) factory13.createSocket(host, port)) {
            socket.setEnabledProtocols(new String[]{"TLSv1.3"});
            socket.startHandshake();
            Assertions.assertTrue(socket.getSession().isValid());
            Assertions.assertEquals("TLSv1.3", socket.getSession().getProtocol());
        }

        // 2. Connection with TLSv1.2 should fail because TLSv1.2 is restricted
        SSLSocketFactory factory12 = sslContext12.getSocketFactory();
        Assertions.assertThrows(IOException.class, () -> {
            try (SSLSocket socket = (SSLSocket) factory12.createSocket(host, port)) {
                socket.setEnabledProtocols(new String[]{"TLSv1.2"});
                socket.startHandshake();
            }
        });
    }
}
