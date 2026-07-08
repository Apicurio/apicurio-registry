import {
  Bullseye,
  EmptyState,
  EmptyStateBody,
  EmptyStateHeader,
  EmptyStateIcon,
  Spinner,
} from "@patternfly/react-core";
import { ExclamationTriangleIcon } from "@patternfly/react-icons";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { ApicurioRegistry3 } from "../utils/k8s";
import { getEmbeddedUIUrl } from "../utils/registry-url";
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

  if (!uiUrl) {
    return (
      <EmptyState>
        <EmptyStateHeader
          titleText={t("Registry UI Unavailable")}
          icon={<EmptyStateIcon icon={ExclamationTriangleIcon} />}
        />
        <EmptyStateBody>
          {t(
            "Unable to determine the Registry UI URL for this instance."
          )}
        </EmptyStateBody>
      </EmptyState>
    );
  }

  return (
    <div className="apicurio-registry-embedded-ui">
      {loading && (
        <Bullseye>
          <Spinner size="xl" aria-label={t("Loading Registry UI...")} />
        </Bullseye>
      )}
      <iframe
        src={uiUrl}
        title={t("Apicurio Registry")}
        className="apicurio-registry-embedded-ui__iframe"
        onLoad={() => setLoading(false)}
        style={{ display: loading ? "none" : "block" }}
      />
    </div>
  );
};

export default EmbeddedRegistryUI;
