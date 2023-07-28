package io.apicurio.registry.systemtests.framework;

import io.apicurio.registry.systemtests.executor.Exec;
import io.apicurio.registry.systemtests.platform.Kubernetes;
import io.apicurio.registry.systemtests.registryinfra.ResourceManager;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CertificateUtils {
    private static final Logger LOGGER = LoggerUtils.getLogger();

    /**
     * @param path Path to the new truststore
     * @param password Password for the new truststore
     * @param publicKey Path to the public key to be imported
     */
    private static void runTruststoreCmd(Path path, String password, Path publicKey) {
        Exec.executeAndCheck(
                "keytool",
                "-keystore", path.toString(),
                "-storepass", password,
                "-noprompt",
                "-alias", "ca",
                "-import",
                "-file", publicKey.toString(),
                "-storetype", "PKCS12"
        );
    }

    /**
     * @param path Path to the new keystore
     * @param password Password for the new keystore
     * @param publicKey Public key to be imported
     * @param privateKey Private key to be imported
     */
    private static void runKeystoreCmd(Path path, String password, Path publicKey, Path privateKey, String hostname) {
        List<String> commands = List.of(
                "openssl",
                "pkcs12",
                "-export",
                "-in", publicKey.toString(),
                "-inkey", privateKey.toString(),
                "-name", hostname,
                "-password", "pass:" + password,
                "-out", path.toString()
        );

        Exec.executeAndCheck(
                commands,
                60_000,
                true,
                true,
                Collections.singletonMap("RANDFILE", Environment.getTmpPath(".rnd").toString())
        );
    }

    private static String encode(Path path) {
        try {
            return Base64.getEncoder().encodeToString(Files.readAllBytes(path));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String encode(String data) {
        return Base64.getEncoder().encodeToString(data.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String data) {
        return new String(
                Base64.getDecoder().decode(data)
        );
    }

    private static String decodeBase64Secret(String namespace, String name, String key) {
        return decode(Kubernetes.getSecretValue(namespace, name, key));
    }

    private static void writeToFile(String data, Path path) {
        try {
            Files.writeString(path, data, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void createSecret(
            ExtensionContext testContext, String namespace, String name, Map<String, String> secretData
    ) throws InterruptedException {
        Secret secret = new SecretBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                .endMetadata()
                .addToData(secretData)
                .build();

        ResourceManager.getInstance().createResource( true, secret);
    }

    public static void createTruststore(
            ExtensionContext testContext,
            String namespace,
            String caCertSecretName,
            String truststoreSecretName
    ) throws InterruptedException {
        LOGGER.info("Preparing truststore...");

        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String caCertSecretValue = decodeBase64Secret(namespace, caCertSecretName, "ca.crt");
        Path caPath = Environment.getTmpPath("ca-" + timestamp + ".crt");

        writeToFile(caCertSecretValue, caPath);

        Path truststorePath = Environment.getTmpPath("truststore-" + timestamp + ".p12");
        String truststorePassword = RandomStringUtils.randomAlphanumeric(32);

        runTruststoreCmd(truststorePath, truststorePassword, caPath);

        Map<String, String> secretData = new HashMap<>() {{
            put("ca.p12", encode(truststorePath));
            put("ca.password", encode(truststorePassword));
        }};

        createSecret(testContext, namespace, truststoreSecretName, secretData);
    }

    public static void createKeystore(
            ExtensionContext testContext,
            String namespace,
            String clientCertSecretName,
            String keystoreSecretName,
            String hostname
    ) throws InterruptedException {
        LOGGER.info("Preparing keystore...");

        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        Path userCertPath = Environment.getTmpPath("user-" + timestamp + ".crt");

        writeToFile(decodeBase64Secret(namespace, clientCertSecretName, "user.crt"), userCertPath);

        Path userKeyPath = Environment.getTmpPath("user-" + timestamp + ".key");

        writeToFile(decodeBase64Secret(namespace, clientCertSecretName, "user.key"), userKeyPath);

        Path keystorePath = Environment.getTmpPath("keystore-" + timestamp + ".p12");
        String keystorePassword = RandomStringUtils.randomAlphanumeric(32);

        runKeystoreCmd(keystorePath, keystorePassword, userCertPath, userKeyPath, hostname);

        Map<String, String> secretData = new HashMap<>() {{
            put("user.p12", encode(keystorePath));
            put("user.password", encode(keystorePassword));
        }};

        createSecret(testContext, namespace, keystoreSecretName, secretData);
    }
}
