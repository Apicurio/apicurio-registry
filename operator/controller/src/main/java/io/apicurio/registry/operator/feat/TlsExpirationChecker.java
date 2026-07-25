/*
 * Copyright 2026 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.operator.feat;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.spec.IngressSpec;
import io.apicurio.registry.operator.api.v1.spec.SecretKeyRef;
import io.apicurio.registry.operator.api.v1.spec.TLSSpec;
import io.apicurio.registry.operator.api.v1.status.ConditionStatus;
import io.apicurio.registry.operator.status.CertificateExpiringConditionManager;
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
import java.security.cert.Certificate;
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

    private final ApicurioRegistry3 primary;
    private final Context<ApicurioRegistry3> context;
    private final int warningDays;
    private final CertificateExpiringConditionManager expiringManager;

    private TlsExpirationChecker(ApicurioRegistry3 primary, Context<ApicurioRegistry3> context, int warningDays, CertificateExpiringConditionManager expiringManager) {
        this.primary = primary;
        this.context = context;
        this.warningDays = warningDays;
        this.expiringManager = expiringManager;
    }

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
        TlsExpirationChecker checker = new TlsExpirationChecker(primary, context, warningDays, expiringManager);

        if (tlsSpec != null) {
            checker.checkKeystore(tlsSpec.getKeystoreSecretRef(), tlsSpec.getKeystorePasswordSecretRef(), "user.p12", "user.password");
            checker.checkKeystore(tlsSpec.getTruststoreSecretRef(), tlsSpec.getTruststorePasswordSecretRef(), "ca.p12", "ca.password");
        }

        if (ingressSpec != null && ingressSpec.getTlsSecretName() != null) {
            checker.checkIngressTls(ingressSpec.getTlsSecretName());
        }
    }

    private void checkKeystore(SecretKeyRef storeRef, SecretKeyRef passRef, String defaultStoreKey, String defaultPassKey) {
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

            String password = getKeystorePassword(passTool, passRef, defaultPassKey);
            if (password == null) {
                return;
            }

            byte[] storeBytes = Base64.getDecoder().decode(b64Store);
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(new ByteArrayInputStream(storeBytes), password.toCharArray());

            Enumeration<String> aliases = ks.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                Certificate cert = ks.getCertificate(alias);
                if (cert instanceof X509Certificate x509) {
                    evaluateCertificate(secret, x509);
                }
            }

        } catch (Exception e) {
            log.warn("Failed to parse keystore secret {}", storeRef.getName(), e);
            StatusManager.get(primary).getConditionManager(ValidationErrorConditionManager.class)
                    .recordError("Failed to parse keystore secret '%s': %s", storeRef.getName(), e.getMessage());
        }
    }

    private String getKeystorePassword(SecretKeyRefTool passTool, SecretKeyRef passRef, String defaultPassKey) {
        if (!passTool.isValid()) {
            return "";
        }
        Secret passSecret = context.getClient().secrets().inNamespace(primary.getMetadata().getNamespace()).withName(passRef.getName()).get();
        if (passSecret != null) {
            String passKey = passRef.getKey() != null ? passRef.getKey() : defaultPassKey;
            String b64Pass = passSecret.getData().get(passKey);
            if (b64Pass != null) {
                return new String(Base64.getDecoder().decode(b64Pass));
            }
        }
        
        StatusManager.get(primary).getConditionManager(ValidationErrorConditionManager.class)
                .recordError("TLS Password Secret '%s' not found or missing key.", passRef.getName());
        return null;
    }

    private void checkIngressTls(String secretName) {
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
            Certificate cert = cf.generateCertificate(new ByteArrayInputStream(certBytes));

            if (cert instanceof X509Certificate x509) {
                evaluateCertificate(secret, x509);
            }

        } catch (Exception e) {
            log.warn("Failed to parse ingress TLS secret {}", secretName, e);
            StatusManager.get(primary).getConditionManager(ValidationErrorConditionManager.class)
                    .recordError("Failed to parse ingress TLS secret '%s': %s", secretName, e.getMessage());
        }
    }

    private void evaluateCertificate(Secret secret, X509Certificate cert) {
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

            boolean alreadyAlerted = false;
            if (primary.getStatus() != null && primary.getStatus().getConditions() != null) {
                alreadyAlerted = primary.getStatus().getConditions().stream()
                        .filter(c -> TYPE_CERTIFICATE_EXPIRING.equals(c.getType()) && ConditionStatus.TRUE.equals(c.getStatus()))
                        .anyMatch(c -> c.getMessage() != null && c.getMessage().contains(secret.getMetadata().getName()));
            }

            if (!alreadyAlerted) {
                emitEvent("CertificateExpiring", msg);
            }
        }
    }

    private void emitEvent(String reason, String message) {
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
