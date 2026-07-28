package io.apicurio.registry.cli.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Generates PKCE (RFC 7636) code verifier and challenge for authorization code flow.
 */
record PkceChallenge(String codeVerifier, String codeChallenge, String codeChallengeMethod) {

    private static final int VERIFIER_LENGTH = 64;
    private static final String CHALLENGE_METHOD = "S256";

    static PkceChallenge generate() {
        final byte[] randomBytes = new byte[VERIFIER_LENGTH];
        new SecureRandom().nextBytes(randomBytes);
        final String verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            final String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
            return new PkceChallenge(verifier, challenge, CHALLENGE_METHOD);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
