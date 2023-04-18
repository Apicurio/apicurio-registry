package io.apicurio.registry;

import io.apicurio.registry.storage.StorageEvent;
import io.apicurio.registry.storage.StorageEventType;
import io.sentry.Sentry;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.ObservesAsync;

/**
 * TODO: Remove me, or even better, move me to io.apicurio.common.apps.logging.sentry.AbstractSentryConfiguration
 * <p>
 * Emits a Sentry event on startup, to ensure the GlitchTip integration is working.
 */
@ApplicationScoped
public class TemporaryOnReadySentryEventEmitter {

    void onStorageReady(@ObservesAsync StorageEvent ev) {
        if (StorageEventType.READY.equals(ev.getType())) {
            Sentry.capture("Apicurio Registry storage is ready");
        }
    }
}
