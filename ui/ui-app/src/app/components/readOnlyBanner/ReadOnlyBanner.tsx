import { FunctionComponent, useState } from "react";
import { Alert, AlertActionCloseButton } from "@patternfly/react-core";
import { ConfigService, useConfigService } from "@services/useConfigService.ts";
import { useLocalStorageService } from "@services/useLocalStorageService.ts";
import { shash } from "@utils/string.utils.ts";

const DISMISSED_KEY_PREFIX = "readOnlyBanner.dismissed.";

export const ReadOnlyBanner: FunctionComponent = () => {
    const config: ConfigService = useConfigService();
    const localStorage = useLocalStorageService();

    const readOnly = config.featureReadOnly();
    const bannerEnabled = config.featureReadOnlyBannerEnabled();
    const reason = config.featureReadOnlyReason();
    const storageName = config.features().storageName;
    const isGitOps = reason === "storage" && storageName === "gitops";

    const customText = config.featureReadOnlyBannerText();
    const message = (customText !== undefined && customText.trim().length > 0)
        ? customText
        : (isGitOps
            ? "This registry is in read-only mode. Changes are managed via GitOps — update your source Git repository."
            : "This registry is in read-only mode.");

    const dismissKey = DISMISSED_KEY_PREFIX + String(shash(message));
    const alreadyDismissed = localStorage.getConfigProperty(dismissKey, undefined) === "true";

    const [isDismissed, setIsDismissed] = useState(alreadyDismissed);

    if (!readOnly || !bannerEnabled || isDismissed) {
        return null;
    }

    const onClose = () => {
        localStorage.setConfigProperty(dismissKey, "true");
        setIsDismissed(true);
    };

    return (
        <Alert
            variant="info"
            isInline
            title={message}
            actionClose={<AlertActionCloseButton onClose={onClose} />}
            data-testid="read-only-banner"
        />
    );
};
