import {
  Bullseye,
  EmptyState,
  EmptyStateBody,
  EmptyStateIcon,
  Spinner,
  Title,
} from "@patternfly/react-core";
import { ExclamationTriangleIcon } from "@patternfly/react-icons";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { ApicurioRegistry3, isAuthEnabled } from "../utils/k8s";
import { getEmbeddedUIUrl, getRegistryUIUrl } from "../utils/registry-url";
import "../styles/plugin.css";

interface EmbeddedRegistryUIProps {
  registry: ApicurioRegistry3;
}

const EmbeddedRegistryUI: React.FC<EmbeddedRegistryUIProps> = ({
  registry,
}) => {
  const { t } = useTranslation("plugin__apicurio-registry");
  const [loading, setLoading] = useState(true);

  const uiUrl = getEmbeddedUIUrl(registry);
  const directUrl = getRegistryUIUrl(registry);
  const authEnabled = isAuthEnabled(registry);

  if (!directUrl) {
    return (
      <EmptyState>
        <EmptyStateIcon icon={ExclamationTriangleIcon} />
        <Title headingLevel="h4" size="lg">{t("Registry UI Unavailable")}</Title>
        <EmptyStateBody>
          {t("Unable to determine the Registry UI URL for this instance.")}
        </EmptyStateBody>
      </EmptyState>
    );
  }

  if (authEnabled) {
    return (
      <EmptyState>
        <Title headingLevel="h4" size="lg">{t("Registry UI")}</Title>
        <EmptyStateBody>
          {t("This registry is secured with authentication. The Registry UI must be opened directly.")}
          <br /><br />
          <a
            href={directUrl}
            target="_blank"
            rel="noopener noreferrer"
            style={{ fontSize: "1.1rem", fontWeight: 600 }}
          >
            {t("Open Registry UI")} ↗
          </a>
        </EmptyStateBody>
      </EmptyState>
    );
  }

  return (
    <div className="apicurio-registry-embedded-ui">
      <div className="apicurio-registry-embedded-ui__toolbar">
        <a href={directUrl} target="_blank" rel="noopener noreferrer">
          {t("Open in new tab")} ↗
        </a>
      </div>
      {loading && (
        <Bullseye>
          <Spinner size="xl" aria-label={t("Loading Registry UI...")} />
        </Bullseye>
      )}
      <iframe
        src={uiUrl!}
        title={t("Apicurio Registry")}
        className="apicurio-registry-embedded-ui__iframe"
        onLoad={() => setLoading(false)}
        style={{ display: loading ? "none" : "block" }}
      />
    </div>
  );
};

export default EmbeddedRegistryUI;
