package io.apicurio.registry.resolver.client;

import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.client.common.RegistryClientOptions.KeyStoreType;
import io.apicurio.registry.client.common.RegistryClientOptions.TrustStoreType;
import io.apicurio.registry.resolver.config.SchemaResolverConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

class RegistryClientFacadeFactoryTest {

    @Test
    void testBuildClientOptionsJks() {
        Map<String, Object> originals = new HashMap<>();
        originals.put(SchemaResolverConfig.REGISTRY_URL, "http://localhost:8080/apis/registry/v3");
        originals.put(SchemaResolverConfig.TLS_TRUSTSTORE_LOCATION, "/path/to/truststore.jks");
        originals.put(SchemaResolverConfig.TLS_TRUSTSTORE_PASSWORD, "trustpassword");
        originals.put(SchemaResolverConfig.TLS_TRUSTSTORE_TYPE, "JKS");
        originals.put(SchemaResolverConfig.TLS_KEYSTORE_LOCATION, "/path/to/keystore.jks");
        originals.put(SchemaResolverConfig.TLS_KEYSTORE_PASSWORD, "keypassword");
        originals.put(SchemaResolverConfig.TLS_KEYSTORE_TYPE, "JKS");

        SchemaResolverConfig config = new SchemaResolverConfig(originals);
        RegistryClientOptions options = RegistryClientFacadeFactory.buildClientOptions(config, null);

        Assertions.assertEquals(TrustStoreType.JKS, options.getTrustStoreType());
        Assertions.assertEquals("/path/to/truststore.jks", options.getTrustStorePath());
        Assertions.assertEquals("trustpassword", options.getTrustStorePassword());

        Assertions.assertEquals(KeyStoreType.JKS, options.getKeyStoreType());
        Assertions.assertEquals("/path/to/keystore.jks", options.getKeyStorePath());
        Assertions.assertEquals("keypassword", options.getKeyStorePassword());
    }

    @Test
    void testBuildClientOptionsPkcs12() {
        Map<String, Object> originals = new HashMap<>();
        originals.put(SchemaResolverConfig.REGISTRY_URL, "http://localhost:8080/apis/registry/v3");
        originals.put(SchemaResolverConfig.TLS_TRUSTSTORE_LOCATION, "/path/to/truststore.p12");
        originals.put(SchemaResolverConfig.TLS_TRUSTSTORE_PASSWORD, "trustpassword");
        originals.put(SchemaResolverConfig.TLS_TRUSTSTORE_TYPE, "PKCS12");
        originals.put(SchemaResolverConfig.TLS_KEYSTORE_LOCATION, "/path/to/keystore.p12");
        originals.put(SchemaResolverConfig.TLS_KEYSTORE_PASSWORD, "keypassword");
        originals.put(SchemaResolverConfig.TLS_KEYSTORE_TYPE, "PKCS12");

        SchemaResolverConfig config = new SchemaResolverConfig(originals);
        RegistryClientOptions options = RegistryClientFacadeFactory.buildClientOptions(config, null);

        Assertions.assertEquals(TrustStoreType.PKCS12, options.getTrustStoreType());
        Assertions.assertEquals("/path/to/truststore.p12", options.getTrustStorePath());
        Assertions.assertEquals("trustpassword", options.getTrustStorePassword());

        Assertions.assertEquals(KeyStoreType.PKCS12, options.getKeyStoreType());
        Assertions.assertEquals("/path/to/keystore.p12", options.getKeyStorePath());
        Assertions.assertEquals("keypassword", options.getKeyStorePassword());
    }

    @Test
    void testBuildClientOptionsPemFilePaths() {
        Map<String, Object> originals = new HashMap<>();
        originals.put(SchemaResolverConfig.REGISTRY_URL, "http://localhost:8080/apis/registry/v3");
        originals.put(SchemaResolverConfig.TLS_TRUSTSTORE_LOCATION, "/path/to/truststore.pem");
        originals.put(SchemaResolverConfig.TLS_TRUSTSTORE_TYPE, "PEM");
        originals.put(SchemaResolverConfig.TLS_KEYSTORE_LOCATION, "/path/to/keystore.pem");
        originals.put(SchemaResolverConfig.TLS_KEYSTORE_TYPE, "PEM");
        originals.put(SchemaResolverConfig.TLS_CLIENT_KEY, "/path/to/client.key");

        SchemaResolverConfig config = new SchemaResolverConfig(originals);
        RegistryClientOptions options = RegistryClientFacadeFactory.buildClientOptions(config, null);

        Assertions.assertEquals(TrustStoreType.PEM, options.getTrustStoreType());
        Assertions.assertArrayEquals(new String[]{"/path/to/truststore.pem"}, options.getPemCertPaths());

        Assertions.assertEquals(KeyStoreType.PEM, options.getKeyStoreType());
        Assertions.assertEquals("/path/to/keystore.pem", options.getPemClientCertPath());
        Assertions.assertEquals("/path/to/client.key", options.getPemClientKeyPath());
    }

    @Test
    void testBuildClientOptionsPemContent() {
        String certContent = "-----BEGIN CERTIFICATE-----\ncert-body\n-----END CERTIFICATE-----";
        String keyContent = "-----BEGIN PRIVATE KEY-----\nkey-body\n-----END PRIVATE KEY-----";

        Map<String, Object> originals = new HashMap<>();
        originals.put(SchemaResolverConfig.REGISTRY_URL, "http://localhost:8080/apis/registry/v3");
        originals.put(SchemaResolverConfig.TLS_CLIENT_CERTIFICATE, certContent);
        originals.put(SchemaResolverConfig.TLS_CLIENT_KEY, keyContent);

        SchemaResolverConfig config = new SchemaResolverConfig(originals);
        RegistryClientOptions options = RegistryClientFacadeFactory.buildClientOptions(config, null);

        Assertions.assertEquals(KeyStoreType.PEM, options.getKeyStoreType());
        Assertions.assertEquals(certContent, options.getPemClientCertContent());
        Assertions.assertEquals(keyContent, options.getPemClientKeyContent());
    }

    @Test
    void testBuildClientOptionsPemMixedCertPathKeyContent() throws IOException {
        Path tempCertFile = Files.createTempFile("client-cert", ".pem");
        String certContent = "-----BEGIN CERTIFICATE-----\ninline-cert\n-----END CERTIFICATE-----";
        Files.writeString(tempCertFile, certContent);

        String keyContent = "-----BEGIN PRIVATE KEY-----\ninline-key\n-----END PRIVATE KEY-----";

        try {
            Map<String, Object> originals = new HashMap<>();
            originals.put(SchemaResolverConfig.REGISTRY_URL, "http://localhost:8080/apis/registry/v3");
            originals.put(SchemaResolverConfig.TLS_CLIENT_CERTIFICATE, tempCertFile.toString()); // path
            originals.put(SchemaResolverConfig.TLS_CLIENT_KEY, keyContent); // inline content

            SchemaResolverConfig config = new SchemaResolverConfig(originals);
            RegistryClientOptions options = RegistryClientFacadeFactory.buildClientOptions(config, null);

            Assertions.assertEquals(KeyStoreType.PEM, options.getKeyStoreType());
            Assertions.assertEquals(certContent, options.getPemClientCertContent());
            Assertions.assertEquals(keyContent, options.getPemClientKeyContent());
        } finally {
            Files.deleteIfExists(tempCertFile);
        }
    }

    @Test
    void testBuildClientOptionsPemMixedCertContentKeyPath() throws IOException {
        String certContent = "-----BEGIN CERTIFICATE-----\ninline-cert\n-----END CERTIFICATE-----";

        Path tempKeyFile = Files.createTempFile("client-key", ".pem");
        String keyContent = "-----BEGIN PRIVATE KEY-----\ninline-key\n-----END PRIVATE KEY-----";
        Files.writeString(tempKeyFile, keyContent);

        try {
            Map<String, Object> originals = new HashMap<>();
            originals.put(SchemaResolverConfig.REGISTRY_URL, "http://localhost:8080/apis/registry/v3");
            originals.put(SchemaResolverConfig.TLS_CLIENT_CERTIFICATE, certContent); // inline content
            originals.put(SchemaResolverConfig.TLS_CLIENT_KEY, tempKeyFile.toString()); // path

            SchemaResolverConfig config = new SchemaResolverConfig(originals);
            RegistryClientOptions options = RegistryClientFacadeFactory.buildClientOptions(config, null);

            Assertions.assertEquals(KeyStoreType.PEM, options.getKeyStoreType());
            Assertions.assertEquals(certContent, options.getPemClientCertContent());
            Assertions.assertEquals(keyContent, options.getPemClientKeyContent());
        } finally {
            Files.deleteIfExists(tempKeyFile);
        }
    }

    @Test
    void testBuildClientOptionsInvalidKeystoreType() {
        Map<String, Object> originals = new HashMap<>();
        originals.put(SchemaResolverConfig.REGISTRY_URL, "http://localhost:8080/apis/registry/v3");
        originals.put(SchemaResolverConfig.TLS_KEYSTORE_LOCATION, "/path/to/keystore.jks");
        originals.put(SchemaResolverConfig.TLS_KEYSTORE_TYPE, "INVALID_TYPE");

        SchemaResolverConfig config = new SchemaResolverConfig(originals);
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            RegistryClientFacadeFactory.buildClientOptions(config, null);
        });
    }

    @Test
    void testBuildClientOptionsInvalidTruststoreType() {
        Map<String, Object> originals = new HashMap<>();
        originals.put(SchemaResolverConfig.REGISTRY_URL, "http://localhost:8080/apis/registry/v3");
        originals.put(SchemaResolverConfig.TLS_TRUSTSTORE_LOCATION, "/path/to/truststore.jks");
        originals.put(SchemaResolverConfig.TLS_TRUSTSTORE_TYPE, "INVALID_TYPE");

        SchemaResolverConfig config = new SchemaResolverConfig(originals);
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            RegistryClientFacadeFactory.buildClientOptions(config, null);
        });
    }

    @Test
    void testBuildClientOptionsPemMissingKey() {
        Map<String, Object> originals = new HashMap<>();
        originals.put(SchemaResolverConfig.REGISTRY_URL, "http://localhost:8080/apis/registry/v3");
        originals.put(SchemaResolverConfig.TLS_KEYSTORE_LOCATION, "/path/to/keystore.pem");
        originals.put(SchemaResolverConfig.TLS_KEYSTORE_TYPE, "PEM");
        // Missing TLS_CLIENT_KEY

        SchemaResolverConfig config = new SchemaResolverConfig(originals);
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            RegistryClientFacadeFactory.buildClientOptions(config, null);
        });
    }

    @Test
    void testBuildClientOptionsPemMalformedContent() {
        Map<String, Object> originals = new HashMap<>();
        originals.put(SchemaResolverConfig.REGISTRY_URL, "http://localhost:8080/apis/registry/v3");
        originals.put(SchemaResolverConfig.TLS_CLIENT_CERTIFICATE, "invalid-pem-without-header");
        originals.put(SchemaResolverConfig.TLS_CLIENT_KEY, "-----BEGIN PRIVATE KEY-----\nkey\n-----END PRIVATE KEY-----");

        SchemaResolverConfig config = new SchemaResolverConfig(originals);
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            RegistryClientFacadeFactory.buildClientOptions(config, null);
        });
    }

    @Test
    void testBuildClientOptionsPemFilePermissionError() throws IOException {
        Path tempCertFile = Files.createTempFile("client-cert", ".pem");
        String certContent = "-----BEGIN CERTIFICATE-----\ninline-cert\n-----END CERTIFICATE-----";
        Files.writeString(tempCertFile, certContent);

        // Make the file unreadable to simulate file permission errors
        tempCertFile.toFile().setReadable(false);

        String keyContent = "-----BEGIN PRIVATE KEY-----\ninline-key\n-----END PRIVATE KEY-----";

        try {
            Map<String, Object> originals = new HashMap<>();
            originals.put(SchemaResolverConfig.REGISTRY_URL, "http://localhost:8080/apis/registry/v3");
            originals.put(SchemaResolverConfig.TLS_CLIENT_CERTIFICATE, tempCertFile.toString()); // path (unreadable)
            originals.put(SchemaResolverConfig.TLS_CLIENT_KEY, keyContent); // inline content

            SchemaResolverConfig config = new SchemaResolverConfig(originals);
            Assertions.assertThrows(IllegalArgumentException.class, () -> {
                RegistryClientFacadeFactory.buildClientOptions(config, null);
            });
        } finally {
            tempCertFile.toFile().setReadable(true);
            Files.deleteIfExists(tempCertFile);
        }
    }

    @Test
    void testBuildClientOptionsPemPathTraversalError() throws IOException {
        Path tempDir = Files.createTempDirectory("traversal-test");
        Path tempFile = Files.createTempFile(tempDir, "client-cert", ".pem");
        Files.writeString(tempFile, "-----BEGIN CERTIFICATE-----\ninline-cert\n-----END CERTIFICATE-----");

        // Construct a path that contains '..' but resolves to the existing file
        String traversalPath = tempDir.resolve("nonexistent-subdir").resolve("..").resolve(tempFile.getFileName()).toString();

        String keyContent = "-----BEGIN PRIVATE KEY-----\ninline-key\n-----END PRIVATE KEY-----";
        try {
            Map<String, Object> originals = new HashMap<>();
            originals.put(SchemaResolverConfig.REGISTRY_URL, "http://localhost:8080/apis/registry/v3");
            originals.put(SchemaResolverConfig.TLS_CLIENT_CERTIFICATE, traversalPath);
            originals.put(SchemaResolverConfig.TLS_CLIENT_KEY, keyContent);

            SchemaResolverConfig config = new SchemaResolverConfig(originals);
            IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class, () -> {
                RegistryClientFacadeFactory.buildClientOptions(config, null);
            });
            Assertions.assertTrue(ex.getMessage().contains("Path traversal detected"), "Expected path traversal exception, but got: " + ex.getMessage());
        } finally {
            Files.deleteIfExists(tempFile);
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    void testBuildClientOptionsClasspathResolution() {
        Map<String, Object> originals = new HashMap<>();
        originals.put(SchemaResolverConfig.REGISTRY_URL, "http://localhost:8080/apis/registry/v3");
        originals.put(SchemaResolverConfig.TLS_TRUSTSTORE_LOCATION, "classpath:io/apicurio/registry/resolver/client/RegistryClientFacadeFactory.class");
        originals.put(SchemaResolverConfig.TLS_TRUSTSTORE_TYPE, "JKS");
        originals.put(SchemaResolverConfig.TLS_TRUSTSTORE_PASSWORD, "password");

        SchemaResolverConfig config = new SchemaResolverConfig(originals);
        RegistryClientOptions options = RegistryClientFacadeFactory.buildClientOptions(config, null);

        String path = options.getTrustStorePath();
        Assertions.assertNotNull(path);
        Assertions.assertTrue(Files.exists(Path.of(path)));
        Assertions.assertTrue(path.contains("apicurio-tls-"));
    }
}
