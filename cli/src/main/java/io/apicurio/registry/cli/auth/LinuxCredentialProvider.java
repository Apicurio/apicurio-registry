package io.apicurio.registry.cli.auth;

import org.jboss.logging.Logger;

class LinuxCredentialProvider implements CredentialProvider {

    private static final Logger log = Logger.getLogger(LinuxCredentialProvider.class);

    private static final String CMD = "secret-tool";
    private static final String KEY_SERVICE = "service";
    private static final String KEY_ACCOUNT = "account";

    private final String serviceName;

    LinuxCredentialProvider(final String serviceName) {
        this.serviceName = serviceName;
    }

    @Override
    public void store(final String account, final String secret) {
        ProcessUtils.execWithStdin(secret, CMD, "store",
                "--label", serviceName + " " + account,
                KEY_SERVICE, serviceName,
                KEY_ACCOUNT, account);
    }

    @Override
    public String retrieve(final String account) {
        return ProcessUtils.exec(CMD, "lookup",
                KEY_SERVICE, serviceName,
                KEY_ACCOUNT, account);
    }

    @Override
    public void delete(final String account) {
        try {
            ProcessUtils.exec(CMD, "clear",
                    KEY_SERVICE, serviceName,
                    KEY_ACCOUNT, account);
        } catch (RuntimeException ex) {
            log.debugf("No existing secret-tool entry to delete.");
        }
    }
}
