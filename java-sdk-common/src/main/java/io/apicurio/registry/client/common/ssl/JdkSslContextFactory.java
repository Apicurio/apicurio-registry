package io.apicurio.registry.client.common.ssl;

import io.apicurio.registry.client.common.RegistryClientOptions;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Factory class for creating JDK SSLContext and SSLParameters from RegistryClientOptions.
 * Supports JKS, PKCS12, and PEM trust stores and client certificates for mTLS.
 */
public class JdkSslContextFactory {

    private static final Logger log = Logger.getLogger(JdkSslContextFactory.class.getName());

    private static final Pattern PEM_CERT_PATTERN = Pattern.compile(
            "-----BEGIN CERTIFICATE-----\\s*([A-Za-z0-9+/=\\s]+?)\\s*-----END CERTIFICATE-----",
            Pattern.DOTALL);

    private static final Pattern PKCS8_KEY_PATTERN = Pattern.compile(
            "-----BEGIN PRIVATE KEY-----\\s*([A-Za-z0-9+/=\\s]+?)\\s*-----END PRIVATE KEY-----",
            Pattern.DOTALL);

    private static final String[] KEY_ALGORITHMS = {"RSA", "EC", "DSA"};

    private JdkSslContextFactory() {
        // Prevent instantiation
    }

    /**
     * Determines if SSL/TLS configuration is required based on the options.
     *
     * @param options the client options
     * @return true if SSL configuration is needed
     */
    public static boolean hasSslConfig(RegistryClientOptions options) {
        return options.getTrustStoreType() != RegistryClientOptions.TrustStoreType.NONE
                || options.getKeyStoreType() != RegistryClientOptions.KeyStoreType.NONE
                || options.isTrustAll()
                || !options.isVerifyHost();
    }

    /**
     * Creates an SSLContext configured with trust store and client certificate settings
     * from the provided options.
     *
     * @param options the client options containing SSL/TLS configuration
     * @return a configured SSLContext
     * @throws RuntimeException if SSL configuration fails
     */
    public static SSLContext createSslContext(RegistryClientOptions options) {
        try {
            TrustManager[] trustManagers = createTrustManagers(options);
            KeyManager[] keyManagers = createKeyManagers(options);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagers, trustManagers, null);
            return sslContext;

        } catch (Exception e) {
            throw new RuntimeException("Failed to create SSL context: " + e.getMessage(), e);
        }
    }

    /**
     * Creates SSLParameters configured with hostname verification settings.
     *
     * @param options the client options
     * @return configured SSLParameters
     */
    public static SSLParameters createSslParameters(RegistryClientOptions options) {
        SSLParameters params = new SSLParameters();
        if (options.isVerifyHost()) {
            params.setEndpointIdentificationAlgorithm("HTTPS");
        } else {
            params.setEndpointIdentificationAlgorithm(null);
        }
        return params;
    }

    private static TrustManager[] createTrustManagers(RegistryClientOptions options) throws Exception {
        if (options.isTrustAll()) {
            log.warning("Using trust-all TrustManager. This should only be used in development environments.");
            return new TrustManager[]{new TrustAllTrustManager()};
        }

        if (options.getTrustStoreType() == RegistryClientOptions.TrustStoreType.NONE) {
            return null; // Use default JVM trust store
        }

        KeyStore trustStore = loadTrustStore(options);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);
        return tmf.getTrustManagers();
    }

    private static KeyStore loadTrustStore(RegistryClientOptions options) throws Exception {
        switch (options.getTrustStoreType()) {
            case JKS:
                return loadKeyStore("JKS", options.getTrustStorePath(), options.getTrustStorePassword());
            case PKCS12:
                return loadKeyStore("PKCS12", options.getTrustStorePath(), options.getTrustStorePassword());
            case PEM:
                return loadPemTrustStore(options);
            default:
                return null;
        }
    }

    private static KeyStore loadKeyStore(String type, String path, String password) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(type);
        char[] passwordChars = password != null ? password.toCharArray() : null;

        try (InputStream is = openResource(path)) {
            keyStore.load(is, passwordChars);
        }
        return keyStore;
    }

    private static KeyStore loadPemTrustStore(RegistryClientOptions options) throws Exception {
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);

        List<X509Certificate> certificates = new ArrayList<>();

        if (options.getPemCertContent() != null) {
            certificates.addAll(parsePemCertificates(options.getPemCertContent()));
        } else if (options.getPemCertPaths() != null) {
            for (String path : options.getPemCertPaths()) {
                String content = readFile(path);
                certificates.addAll(parsePemCertificates(content));
            }
        }

        int index = 0;
        for (X509Certificate cert : certificates) {
            trustStore.setCertificateEntry("cert-" + index++, cert);
        }

        return trustStore;
    }

    private static KeyManager[] createKeyManagers(RegistryClientOptions options) throws Exception {
        if (options.getKeyStoreType() == RegistryClientOptions.KeyStoreType.NONE) {
            return null;
        }

        KeyStore keyStore = loadClientKeyStore(options);
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, options.getKeyStorePassword() != null
                ? options.getKeyStorePassword().toCharArray()
                : new char[0]);
        return kmf.getKeyManagers();
    }

    private static KeyStore loadClientKeyStore(RegistryClientOptions options) throws Exception {
        switch (options.getKeyStoreType()) {
            case JKS:
                return loadKeyStore("JKS", options.getKeyStorePath(), options.getKeyStorePassword());
            case PKCS12:
                return loadKeyStore("PKCS12", options.getKeyStorePath(), options.getKeyStorePassword());
            case PEM:
                return loadPemClientKeyStore(options);
            default:
                return null;
        }
    }

    private static KeyStore loadPemClientKeyStore(RegistryClientOptions options) throws Exception {
        String certContent;
        String keyContent;

        if (options.getPemClientCertContent() != null && options.getPemClientKeyContent() != null) {
            certContent = options.getPemClientCertContent();
            keyContent = options.getPemClientKeyContent();
        } else {
            certContent = readFile(options.getPemClientCertPath());
            keyContent = readFile(options.getPemClientKeyPath());
        }

        List<X509Certificate> certChain = parsePemCertificates(certContent);
        if (certChain.isEmpty()) {
            throw new IllegalArgumentException("No certificates found in PEM content");
        }

        PrivateKey privateKey = parsePemPrivateKey(keyContent);

        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);

        Certificate[] chain = certChain.toArray(new Certificate[0]);
        keyStore.setKeyEntry("client", privateKey, new char[0], chain);

        return keyStore;
    }

    private static List<X509Certificate> parsePemCertificates(String pemContent) throws Exception {
        List<X509Certificate> certificates = new ArrayList<>();
        CertificateFactory cf = CertificateFactory.getInstance("X.509");

        Matcher matcher = PEM_CERT_PATTERN.matcher(pemContent);
        while (matcher.find()) {
            String base64Cert = matcher.group(1).replaceAll("\\s", "");
            byte[] certBytes = Base64.getDecoder().decode(base64Cert);
            X509Certificate cert = (X509Certificate) cf.generateCertificate(
                    new ByteArrayInputStream(certBytes));
            certificates.add(cert);
        }

        return certificates;
    }

    private static PrivateKey parsePemPrivateKey(String pemContent) throws Exception {
        Matcher pkcs8Matcher = PKCS8_KEY_PATTERN.matcher(pemContent);
        if (pkcs8Matcher.find()) {
            String base64Key = pkcs8Matcher.group(1).replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(base64Key);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);

            // Try each supported algorithm until one works
            for (String algorithm : KEY_ALGORITHMS) {
                try {
                    KeyFactory kf = KeyFactory.getInstance(algorithm);
                    return kf.generatePrivate(keySpec);
                } catch (Exception ignored) {
                    // Try next algorithm
                }
            }
            throw new IllegalArgumentException(
                    "Failed to parse PKCS#8 private key with any supported algorithm (RSA, EC, DSA)");
        }

        throw new IllegalArgumentException(
                "No valid PKCS#8 private key found. Expected '-----BEGIN PRIVATE KEY-----' format. " +
                "If your key is in a different format, convert it using: " +
                "openssl pkcs8 -topk8 -nocrypt -in your-key.pem -out your-key-pkcs8.pem");
    }

    private static InputStream openResource(String path) throws IOException {
        if (path.startsWith("classpath:")) {
            String resourcePath = path.substring("classpath:".length());
            InputStream is = JdkSslContextFactory.class.getClassLoader().getResourceAsStream(resourcePath);
            if (is == null) {
                throw new IOException("Classpath resource not found: " + resourcePath);
            }
            return is;
        }
        return new FileInputStream(path);
    }

    private static String readFile(String path) throws IOException {
        if (path.startsWith("classpath:")) {
            String resourcePath = path.substring("classpath:".length());
            try (InputStream is = JdkSslContextFactory.class.getClassLoader().getResourceAsStream(resourcePath)) {
                if (is == null) {
                    throw new IOException("Classpath resource not found: " + resourcePath);
                }
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        return Files.readString(Paths.get(path), StandardCharsets.UTF_8);
    }

    /**
     * A TrustManager that trusts all certificates. FOR DEVELOPMENT USE ONLY.
     */
    private static class TrustAllTrustManager implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
            // Trust all
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
            // Trust all
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
