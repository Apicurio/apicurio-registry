package io.apicurio.registry.client.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RegistryClientOptionsKeyPasswordTest {

    @Test
    void testJksKeyPasswordIsKeptSeparateFromStorePassword() {
        var options = RegistryClientOptions.create().keystoreJks("/path/to/keystore.jks", "store", "key");

        assertEquals(RegistryClientOptions.KeyStoreType.JKS, options.getKeyStoreType());
        assertEquals("store", options.getKeyStorePassword());
        assertEquals("key", options.getKeyPassword());
    }

    @Test
    void testPkcs12KeyPasswordIsKeptSeparateFromStorePassword() {
        var options = RegistryClientOptions.create().keystorePkcs12("/path/to/keystore.p12", "store", "key");

        assertEquals(RegistryClientOptions.KeyStoreType.PKCS12, options.getKeyStoreType());
        assertEquals("store", options.getKeyStorePassword());
        assertEquals("key", options.getKeyPassword());
    }

    @Test
    void testTwoArgumentOverloadResetsKeyPassword() {
        var options = RegistryClientOptions.create()
                .keystoreJks("/path/to/keystore.jks", "store", "key")
                .keystorePkcs12("/path/to/keystore.p12", "store");

        assertEquals(RegistryClientOptions.KeyStoreType.PKCS12, options.getKeyStoreType());
        assertNull(options.getKeyPassword());
    }

    @Test
    void testJksTwoArgumentOverloadResetsKeyPassword() {
        var options = RegistryClientOptions.create()
                .keystoreJks("/path/to/keystore.jks", "store", "key")
                .keystoreJks("/path/to/other.jks", "store");

        assertEquals(RegistryClientOptions.KeyStoreType.JKS, options.getKeyStoreType());
        assertEquals("/path/to/other.jks", options.getKeyStorePath());
        assertNull(options.getKeyPassword());
    }

    @Test
    void testKeystorePemContentResetsKeyPassword() {
        var options = RegistryClientOptions.create()
                .keystorePkcs12("/path/to/keystore.p12", "store", "key")
                .keystorePemContent("-----BEGIN CERTIFICATE-----\ncert\n-----END CERTIFICATE-----",
                        "-----BEGIN PRIVATE KEY-----\nkey\n-----END PRIVATE KEY-----");

        assertEquals(RegistryClientOptions.KeyStoreType.PEM, options.getKeyStoreType());
        assertNull(options.getKeyPassword());
    }

    @Test
    void testKeystorePemResetsKeyPassword() {
        var options = RegistryClientOptions.create()
                .keystoreJks("/path/to/keystore.jks", "store", "key")
                .keystorePem("/path/to/cert.pem", "/path/to/key.pem");

        assertEquals(RegistryClientOptions.KeyStoreType.PEM, options.getKeyStoreType());
        assertNull(options.getKeyPassword());
    }

    @Test
    void testClearKeyStoreResetsKeyPassword() {
        var options = RegistryClientOptions.create()
                .keystoreJks("/path/to/keystore.jks", "store", "key")
                .clearKeyStore();

        assertEquals(RegistryClientOptions.KeyStoreType.NONE, options.getKeyStoreType());
        assertNull(options.getKeyStorePassword());
        assertNull(options.getKeyPassword());
    }
}
