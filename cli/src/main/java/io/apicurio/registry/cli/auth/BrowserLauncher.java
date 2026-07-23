package io.apicurio.registry.cli.auth;

import io.apicurio.registry.cli.utils.PlatformUtils;
import org.jboss.logging.Logger;

/**
 * Opens a URL in the user's default browser.
 */
final class BrowserLauncher {

    private static final Logger log = Logger.getLogger(BrowserLauncher.class);

    private BrowserLauncher() {
    }

    static void openUrl(final String url) {
        try {
            if (PlatformUtils.isMacOS()) {
                ProcessUtils.exec("open", url);
            } else {
                ProcessUtils.exec("xdg-open", url);
            }
        } catch (CredentialStoreException ex) {
            log.debugf("Could not open browser: %s", ex.getMessage());
            System.out.println("Open this URL in your browser to log in:");
            System.out.println("  " + url);
        }
    }
}
