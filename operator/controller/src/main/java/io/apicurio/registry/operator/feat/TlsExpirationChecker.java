package io.apicurio.registry.operator.feat;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.spec.IngressSpec;
import io.apicurio.registry.operator.api.v1.spec.SecretKeyRef;
import io.apicurio.registry.operator.api.v1.spec.TLSSpec;
import io.apicurio.registry.operator.status.CertificateExpiringConditionManager;
import io.apicurio.registry.operator.status.OperatorErrorConditionManager;
import io.apicurio.registry.operator.status.StatusManager;
import io.apicurio.registry.operator.status.ValidationErrorConditionManager;
import io.apicurio.registry.operator.utils.SecretKeyRefTool;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.EventBuilder;
import io.fabric8.kubernetes.api.model.ObjectReferenceBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.Enumeration;
import java.util.UUID;

import static io.apicurio.registry.operator.api.v1.status.ConditionConstants.TYPE_CERTIFICATE_EXPIRING;

public class TlsExpirationChecker {

    private static final Logger log = LoggerFactory.getLogger(TlsExpirationChecker.class);
    private static final String CERT_MANAGER_ANNOTATION = "cert-manager.io/certificate-name";
    private static final int DEFAULT_WARNING_DAYS = 30;

    public static void checkCertificates(ApicurioRegistry3 primary, Context<ApicurioRegistry3> context) {
        if (primary.getSpec() == null || primary.getSpec().getApp() == null) {
            return;
        }

        TLSSpec tlsSpec = primary.getSpec().getApp().getTls();
        IngressSpec ingressSpec = primary.getSpec().getApp().getIngress();

        int warningDays = DEFAULT_WARNING_DAYS;
        if (tlsSpec != null && tlsSpec.getExpirationWarningDays() != null) {
            warningDays = tlsSpec.getExpirationWarningDays();
        }

        CertificateExpiringConditionManager expiringManager = StatusManager.get(primary).getConditionManager(CertificateExpiringConditionManager.class);

        if (tlsSpec != null) {
            checkKeystore(primary, context, tlsSpec.getKeystoreSecretRef(), tlsSpec.getKeystorePasswordSecretRef(), "user.p12", "user.password", warningDays, expiringManager);
            checkKeystore(primary, context, tlsSpec.getTruststoreSecretRef(), tlsSpec.getTruststorePasswordSecretRef(), "ca.p12", "ca.password", warningDays, expiringManager);
        }

        if (ingressSpec != null && ingressSpec.getTlsSecretName() != null) {
            checkIngressTls(primary, context, ingressSpec.getTlsSecretName(), warningDays, expiringManager);
        }
    }

    private static void checkKeystore(ApicurioRegistry3 primary, Context<ApicurioRegistry3> context,
                                      SecretKeyRef storeRef, SecretKeyRef passRef,
                                      String defaultStoreKey, String defaultPassKey,
                                      int warningDays, CertificateExpiringConditionManager expiringManager) {

        SecretKeyRefTool storeTool = new SecretKeyRefTool(storeRef, defaultStoreKey);
        SecretKeyRefTool passTool = new SecretKeyRefTool(passRef, defaultPassKey);

        if (!storeTool.isValid()) {
            return; // Not configured
        }

        try {
            Secret secret = context.getClient().secrets().inNamespace(primary.getMetadata().getNamespace()).withName(storeRef.getName()).get();
            if (secret == null) {
                StatusManager.get(primary).getConditionManager(ValidationErrorConditionManager.class)
                        .recordError("TLS Secret '%s' not found.", storeRef.getName());
                return;
            }

            String storeKey = storeRef.getKey() != null ? storeRef.getKey() : defaultStoreKey;
            String b64Store = secret.getData().get(storeKey);
            if (b64Store == null) {
                StatusManager.get(primary).getConditionManager(ValidationErrorConditionManager.class)
                        .recordError("TLS Secret '%s' does not contain key '%s'.", storeRef.getName(), storeKey);
                return;
            }

            String password = "";
            if (passTool.isValid()) {
                Secret passSecret = context.getClient().secrets().inNamespace(primary.getMetadata().getNamespace()).withName(passRef.getName()).get();
                if (passSecret != null) {
                    String passKey = passRef.getKey() != null ? passRef.getKey() : defaultPassKey;
                    String b64Pass = passSecret.getData().get(passKey);
                    if (b64Pass != null) {
                        password = new String(Base64.getDecoder().decode(b64Pass));
                    }
                }
            }

            byte[] storeBytes = Base64.getDecoder().decode(b64Store);
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(new ByteArrayInputStream(storeBytes), password.toCharArray());

            Enumeration<String> aliases = ks.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                java.security.cert.Certificate cert = ks.getCertificate(alias);
                if (cert instanceof X509Certificate x509) {
                    evaluateCertificate(primary, context, secret, x509, warningDays, expiringManager);
                }
            }

        } catch (Exception e) {
            log.error("Failed to parse keystore secret {}", storeRef.getName(), e);
            StatusManager.get(primary).getConditionManager(OperatorErrorConditionManager.class).recordException(e);
        }
    }

    private static void checkIngressTls(ApicurioRegistry3 primary, Context<ApicurioRegistry3> context, String secretName, int warningDays, CertificateExpiringConditionManager expiringManager) {
        try {
            Secret secret = context.getClient().secrets().inNamespace(primary.getMetadata().getNamespace()).withName(secretName).get();
            if (secret == null) {
                StatusManager.get(primary).getConditionManager(ValidationErrorConditionManager.class)
                        .recordError("Ingress TLS Secret '%s' not found.", secretName);
                return;
            }

            String b64Cert = secret.getData().get("tls.crt");
            if (b64Cert == null) {
                StatusManager.get(primary).getConditionManager(ValidationErrorConditionManager.class)
                        .recordError("Ingress TLS Secret '%s' does not contain key 'tls.crt'.", secretName);
                return;
            }

            byte[] certBytes = Base64.getDecoder().decode(b64Cert);
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            java.security.cert.Certificate cert = cf.generateCertificate(new ByteArrayInputStream(certBytes));

            if (cert instanceof X509Certificate x509) {
                evaluateCertificate(primary, context, secret, x509, warningDays, expiringManager);
            }

        } catch (Exception e) {
            log.error("Failed to parse ingress TLS secret {}", secretName, e);
            StatusManager.get(primary).getConditionManager(OperatorErrorConditionManager.class).recordException(e);
        }
    }

    private static void evaluateCertificate(ApicurioRegistry3 primary, Context<ApicurioRegistry3> context,
                                            Secret secret, X509Certificate cert, int warningDays,
                                            CertificateExpiringConditionManager expiringManager) {
        Date notAfter = cert.getNotAfter();
        Instant expiryDate = notAfter.toInstant();
        Instant warningDate = Instant.now().plus(warningDays, ChronoUnit.DAYS);

        if (expiryDate.isBefore(warningDate)) {
            boolean isCertManager = secret.getMetadata().getAnnotations() != null &&
                    secret.getMetadata().getAnnotations().containsKey(CERT_MANAGER_ANNOTATION);

            String status = expiryDate.isBefore(Instant.now()) ? "expired" : "expiring soon";
            String msg = String.format("Certificate in secret '%s' is %s (NotAfter: %s).", secret.getMetadata().getName(), status, notAfter);

            if (isCertManager) {
                msg += " Note: This certificate appears to be managed by cert-manager, auto-rotation may occur.";
            }

            expiringManager.recordExpiringCertificate(secret.getMetadata().getName(), msg);

            // Only emit Kubernetes Event if the condition was not already True on the previous reconciliation
            boolean alreadyAlerting = false;
            if (primary.getStatus() != null && primary.getStatus().getConditions() != null) {
                alreadyAlerting = primary.getStatus().getConditions().stream()
                        .anyMatch(c -> TYPE_CERTIFICATE_EXPIRING.equals(c.getType()) && "True".equals(c.getStatus()));
            }

            if (!alreadyAlerting) {
                emitEvent(primary, context, "CertificateExpiring", msg);
            }
        }
    }

    private static void emitEvent(ApicurioRegistry3 primary, Context<ApicurioRegistry3> context, String reason, String message) {
        try {
            Event event = new EventBuilder()
                    .withNewMetadata()
                        .withName(primary.getMetadata().getName() + "-" + reason.toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8))
                        .withNamespace(primary.getMetadata().getNamespace())
                    .endMetadata()
                    .withInvolvedObject(new ObjectReferenceBuilder()
                            .withKind(primary.getKind())
                            .withName(primary.getMetadata().getName())
                            .withNamespace(primary.getMetadata().getNamespace())
                            .withApiVersion(primary.getApiVersion())
                            .withUid(primary.getMetadata().getUid())
                            .build())
                    .withReason(reason)
                    .withMessage(message)
                    .withType("Warning")
                    .withNewSource()
                        .withComponent("apicurioregistry3-operator")
                    .endSource()
                    .withFirstTimestamp(Instant.now().toString())
                    .withLastTimestamp(Instant.now().toString())
                    .build();

            context.getClient().v1().events().inNamespace(primary.getMetadata().getNamespace()).resource(event).create();
        } catch (Exception e) {
            log.error("Failed to emit Kubernetes Event for reason {}", reason, e);
        }
    }
}
