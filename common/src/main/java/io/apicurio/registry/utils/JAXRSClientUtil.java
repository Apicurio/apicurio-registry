package io.apicurio.registry.utils;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

public class JAXRSClientUtil {

    private static class NullHostnameVerifier implements HostnameVerifier {
        public static final NullHostnameVerifier INSTANCE = new NullHostnameVerifier();

        @Override
        public boolean verify(String hostname, SSLSession session) {
            return true;
        }
    }

    private static TrustManager[] nullTrustManager = new TrustManager[]{new X509TrustManager() {
        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }

        @Override
        public void checkClientTrusted(X509Certificate[] certs, String authType) {}

        @Override
        public void checkServerTrusted(X509Certificate[] certs, String authType) {}
    }};

    public static Client getJAXRSClient(boolean skipSSLValidation) throws KeyManagementException, NoSuchAlgorithmException {
        ClientBuilder cb = ClientBuilder.newBuilder();

        cb.connectTimeout(10, TimeUnit.SECONDS);

        Client newClient;
        if (skipSSLValidation) {
            SSLContext nullSSLContext = SSLContext.getInstance("TLSv1.2");
            nullSSLContext.init(null, nullTrustManager, null);
            cb.hostnameVerifier(NullHostnameVerifier.INSTANCE)
                    .sslContext(nullSSLContext);

            newClient = cb.build();
        } else {
            newClient = cb.build();
        }

        return newClient;
    }

}
