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

    private static final Pattern PEM_KEY_PATTERN = Pattern.compile(
            "-----BEGIN (?:RSA )?PRIVATE KEY-----\\s*([A-Za-z0-9+/=\\s]+?)\\s*-----END (?:RSA )?PRIVATE KEY-----",
            Pattern.DOTALL);

    private static final Pattern PKCS8_KEY_PATTERN = Pattern.compile(
            "-----BEGIN PRIVATE KEY-----\\s*([A-Za-z0-9+/=\\s]+?)\\s*-----END PRIVATE KEY-----",
            Pattern.DOTALL);

    private static final Pattern EC_KEY_PATTERN = Pattern.compile(
            "-----BEGIN EC PRIVATE KEY-----\\s*([A-Za-z0-9+/=\\s]+?)\\s*-----END EC PRIVATE KEY-----",
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
        // Try PKCS#8 format first (algorithm-agnostic)
        Matcher pkcs8Matcher = PKCS8_KEY_PATTERN.matcher(pemContent);
        if (pkcs8Matcher.find()) {
            String base64Key = pkcs8Matcher.group(1).replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(base64Key);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
            return tryParseWithMultipleAlgorithms(keySpec);
        }

        // Try EC PRIVATE KEY format (SEC1/RFC 5915)
        Matcher ecMatcher = EC_KEY_PATTERN.matcher(pemContent);
        if (ecMatcher.find()) {
            String base64Key = ecMatcher.group(1).replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(base64Key);
            // Convert SEC1 EC key to PKCS#8 format
            byte[] pkcs8Bytes = convertEcToPkcs8(keyBytes);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(pkcs8Bytes);
            KeyFactory kf = KeyFactory.getInstance("EC");
            return kf.generatePrivate(keySpec);
        }

        // Try RSA PRIVATE KEY format (PKCS#1)
        Matcher rsaMatcher = PEM_KEY_PATTERN.matcher(pemContent);
        if (rsaMatcher.find()) {
            String base64Key = rsaMatcher.group(1).replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(base64Key);
            // Convert PKCS#1 to PKCS#8 format
            byte[] pkcs8Bytes = convertPkcs1ToPkcs8(keyBytes);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(pkcs8Bytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePrivate(keySpec);
        }

        throw new IllegalArgumentException("No valid private key found in PEM content. " +
                "Supported formats: PKCS#8 (BEGIN PRIVATE KEY), RSA (BEGIN RSA PRIVATE KEY), EC (BEGIN EC PRIVATE KEY)");
    }

    private static PrivateKey tryParseWithMultipleAlgorithms(PKCS8EncodedKeySpec keySpec) throws Exception {
        Exception lastException = null;
        for (String algorithm : KEY_ALGORITHMS) {
            try {
                KeyFactory kf = KeyFactory.getInstance(algorithm);
                return kf.generatePrivate(keySpec);
            } catch (Exception e) {
                lastException = e;
            }
        }
        throw new IllegalArgumentException("Failed to parse PKCS#8 private key with any supported algorithm (RSA, EC, DSA)", lastException);
    }

    private static byte[] convertPkcs1ToPkcs8(byte[] pkcs1Bytes) {
        // RSA OID: 1.2.840.113549.1.1.1
        byte[] rsaAlgorithmIdentifier = {
                0x30, 0x0D,                                     // SEQUENCE (13 bytes)
                0x06, 0x09,                                     // OID (9 bytes)
                0x2A, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xF7, 0x0D, 0x01, 0x01, 0x01,  // 1.2.840.113549.1.1.1
                0x05, 0x00                                      // NULL
        };

        return buildPkcs8Structure(pkcs1Bytes, rsaAlgorithmIdentifier);
    }

    private static byte[] convertEcToPkcs8(byte[] ecKeyBytes) {
        // For EC keys, we need to extract the curve OID from the key itself
        // The SEC1 format contains the curve info, so we use a generic EC algorithm identifier
        // EC OID: 1.2.840.10045.2.1 with the curve parameters embedded in the key
        // We'll use id-ecPublicKey without parameters since the curve is in the private key
        byte[] ecAlgorithmIdentifier = {
                0x30, 0x13,                                     // SEQUENCE (19 bytes)
                0x06, 0x07,                                     // OID (7 bytes)
                0x2A, (byte) 0x86, 0x48, (byte) 0xCE, 0x3D, 0x02, 0x01,  // 1.2.840.10045.2.1 (id-ecPublicKey)
                0x06, 0x08,                                     // OID (8 bytes) - secp256r1/prime256v1
                0x2A, (byte) 0x86, 0x48, (byte) 0xCE, 0x3D, 0x03, 0x01, 0x07  // 1.2.840.10045.3.1.7
        };

        return buildPkcs8Structure(ecKeyBytes, ecAlgorithmIdentifier);
    }

    private static byte[] buildPkcs8Structure(byte[] privateKeyBytes, byte[] algorithmIdentifier) {
        // PKCS#8 structure:
        // SEQUENCE {
        //   INTEGER (version = 0)
        //   AlgorithmIdentifier
        //   OCTET STRING (private key)
        // }

        // Version: INTEGER 0
        byte[] version = {0x02, 0x01, 0x00};

        // Private key wrapped in OCTET STRING
        byte[] privateKeyOctetString = wrapInOctetString(privateKeyBytes);

        // Inner content length
        int innerLength = version.length + algorithmIdentifier.length + privateKeyOctetString.length;

        // Build the complete PKCS#8 structure
        byte[] lengthBytes = encodeLength(innerLength);
        byte[] result = new byte[1 + lengthBytes.length + innerLength];

        int offset = 0;
        result[offset++] = 0x30; // SEQUENCE tag
        System.arraycopy(lengthBytes, 0, result, offset, lengthBytes.length);
        offset += lengthBytes.length;

        System.arraycopy(version, 0, result, offset, version.length);
        offset += version.length;

        System.arraycopy(algorithmIdentifier, 0, result, offset, algorithmIdentifier.length);
        offset += algorithmIdentifier.length;

        System.arraycopy(privateKeyOctetString, 0, result, offset, privateKeyOctetString.length);

        return result;
    }

    private static byte[] wrapInOctetString(byte[] data) {
        byte[] lengthBytes = encodeLength(data.length);
        byte[] result = new byte[1 + lengthBytes.length + data.length];
        result[0] = 0x04; // OCTET STRING tag
        System.arraycopy(lengthBytes, 0, result, 1, lengthBytes.length);
        System.arraycopy(data, 0, result, 1 + lengthBytes.length, data.length);
        return result;
    }

    private static byte[] encodeLength(int length) {
        if (length < 128) {
            return new byte[]{(byte) length};
        } else if (length < 256) {
            return new byte[]{(byte) 0x81, (byte) length};
        } else if (length < 65536) {
            return new byte[]{(byte) 0x82, (byte) (length >> 8), (byte) length};
        } else {
            return new byte[]{(byte) 0x83, (byte) (length >> 16), (byte) (length >> 8), (byte) length};
        }
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
