package io.apicurio.registry.cli.auth;

import org.jboss.logging.Logger;

class MacOSCredentialProvider implements CredentialProvider {

    private static final Logger log = Logger.getLogger(MacOSCredentialProvider.class);

    private static final String CMD = "security";
    private static final String FLAG_SERVICE = "-s";
    private static final String FLAG_ACCOUNT = "-a";

    private final String serviceName;

    MacOSCredentialProvider(final String serviceName) {
        this.serviceName = serviceName;
    }

    @Override
    public void store(final String account, final String secret) {
        delete(account);
        ProcessUtils.exec(CMD, "add-generic-password",
                FLAG_SERVICE, serviceName,
                FLAG_ACCOUNT, account,
                "-w", secret);
    }

    @Override
    public String retrieve(final String account) {
        return ProcessUtils.exec(CMD, "find-generic-password",
                FLAG_SERVICE, serviceName,
                FLAG_ACCOUNT, account,
                "-w");
    }

    @Override
    public void delete(final String account) {
        try {
            ProcessUtils.exec(CMD, "delete-generic-password",
                    FLAG_SERVICE, serviceName,
                    FLAG_ACCOUNT, account);
        } catch (RuntimeException ex) {
            log.debugf("No existing keychain entry to delete.");
        }
    }
}
