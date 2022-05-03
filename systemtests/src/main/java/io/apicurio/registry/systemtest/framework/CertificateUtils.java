package io.apicurio.registry.systemtest.framework;

import io.apicurio.registry.systemtest.executor.Exec;
import io.apicurio.registry.systemtest.platform.Kubernetes;
import io.apicurio.registry.systemtest.registryinfra.ResourceManager;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CertificateUtils {
    private static final Logger LOGGER = LoggerUtils.getLogger();

    /**
     * @param newTruststorePath Path to the new truststore
     * @param newTruststorePassword Password for the new truststore
     * @param certificateAlias Alias of certificate
     * @param publicKeyToImportPath Path to the public key to be imported
     */
    private static void createTruststore(String newTruststorePath, String newTruststorePassword, String certificateAlias, String publicKeyToImportPath) {
        Exec.executeAndCheck("keytool", "-keystore", newTruststorePath, "-storepass", newTruststorePassword, "-noprompt", "-alias", certificateAlias, "-import", "-file", publicKeyToImportPath, "-storetype", "PKCS12");
    }

    /**
     * @param newKeystorePath Path to the new keystore
     * @param newKeystorePassword Password for the new keystore
     * @param publicKeyToImportPath Public key to be imported
     * @param privateKeyToImportPath Private key to be imported
     */
    private static void createKeystore(String newKeystorePath, String newKeystorePassword, String publicKeyToImportPath, String privateKeyToImportPath, String hostname) {
        List<String> commands = List.of("openssl",  "pkcs12",  "-export",  "-in", publicKeyToImportPath, "-inkey", privateKeyToImportPath, "-name", hostname, "-password", "pass:" + newKeystorePassword, "-out", newKeystorePath);

        Exec.executeAndCheck(commands, 60_000, true, true, Collections.singletonMap("RANDFILE", Paths.get(Environment.tempPath, ".rnd").toString()));
    }

    private static String getBase64DecodedSecretValue(String namespace, String name, String dataKey) {
        return new String(
                Base64.getDecoder().decode(
                        Kubernetes.getClient().secrets().inNamespace(namespace).withName(name).get().getData().get(dataKey)
                )
        );
    }

    private static void writeContentToFile(String content, String filePath) {
        try {
            Files.writeString(Paths.get(filePath), content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void createSecret(ExtensionContext testContext, String namespace, String secretName, Map<String, String> secretData) {
        Secret secret = new SecretBuilder()
                .withNewMetadata()
                    .withName(secretName)
                    .withNamespace(namespace)
                .endMetadata()
                .addToData(secretData)
                .build();

        ResourceManager.getInstance().createResource(testContext, true, secret);
    }

    public static void createCertificateStores(ExtensionContext testContext, String clusterCaCertificateSecretName, String clientCertificateSecretName, String truststoreSecretName, String keystoreSecretName, String hostname, String namespace) {
        if(clusterCaCertificateSecretName == null || clusterCaCertificateSecretName.equals("")) {
            throw new IllegalArgumentException("Cluster CA certificate name is not set.");
        }

        LOGGER.info("Cluster CA certificate name: {}", clusterCaCertificateSecretName);

        LOGGER.info("Client certificate secret name: {}", clientCertificateSecretName);

        if(truststoreSecretName == null || truststoreSecretName.equals("")) {
            throw new IllegalArgumentException("Truststore secret name is not set.");
        }

        LOGGER.info("Truststore secret name: {}", truststoreSecretName);

        LOGGER.info("Keystore secret name: {}", keystoreSecretName);

        LOGGER.info("Hostname: {}", hostname);

        LOGGER.info("Namespace: {}", namespace);

        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String caPath = Paths.get(Environment.tempPath, "ca-" + timestamp + ".crt").toString();
        String truststorePath = Paths.get(Environment.tempPath, "truststore-" + timestamp + ".p12").toString();

        String clusterCaCertificateSecretValue = getBase64DecodedSecretValue(namespace, clusterCaCertificateSecretName, "ca.crt");

        LOGGER.info("Preparing truststore...");

        String truststorePassword = StringUtils.getRandom(32);

        writeContentToFile(clusterCaCertificateSecretValue, caPath);

        createTruststore(truststorePath, truststorePassword, "ca", caPath);

        try {
            createSecret(
                    testContext,
                    namespace,
                    truststoreSecretName,
                    new HashMap<>() {{
                        put("ca.p12", Base64.getEncoder().encodeToString(Files.readAllBytes(Paths.get(truststorePath))));
                        put("ca.password", Base64.getEncoder().encodeToString(truststorePassword.getBytes(StandardCharsets.UTF_8)));
                    }}
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if(clientCertificateSecretName != null && !clientCertificateSecretName.equals("")) {
            String userCertificatePath = Paths.get(Environment.tempPath, "user-" + timestamp + ".crt").toString();
            String userKeyPath = Paths.get(Environment.tempPath, "user-" + timestamp + ".key").toString();
            String keystorePath = Paths.get(Environment.tempPath, "keystore-" + timestamp + ".p12").toString();
            // TODO: Check necessary values to be set
            LOGGER.info("Preparing keystore...");

            String keystorePassword = StringUtils.getRandom(32);

            writeContentToFile(getBase64DecodedSecretValue(namespace, clientCertificateSecretName, "user.crt"), userCertificatePath);
            writeContentToFile(getBase64DecodedSecretValue(namespace, clientCertificateSecretName, "user.key"), userKeyPath);

            createKeystore(keystorePath, keystorePassword, userCertificatePath, userKeyPath, hostname);

            try {
                createSecret(
                        testContext,
                        namespace,
                        keystoreSecretName,
                        new HashMap<>() {{
                            put("user.p12", Base64.getEncoder().encodeToString(Files.readAllBytes(Paths.get(keystorePath))));
                            put("user.password", Base64.getEncoder().encodeToString(keystorePassword.getBytes(StandardCharsets.UTF_8)));
                        }}
                );
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
