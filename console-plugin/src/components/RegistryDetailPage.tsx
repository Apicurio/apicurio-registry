import {
  Alert,
  DescriptionList,
  DescriptionListDescription,
  DescriptionListGroup,
  DescriptionListTerm,
  Grid,
  GridItem,
  Label,
  LabelGroup,
  PageSection,
  Tab,
  Tabs,
  TabTitleText,
  Title,
} from "@patternfly/react-core";
import {
  Table,
  Tbody,
  Td,
  Th,
  Thead,
  Tr,
} from "@patternfly/react-table";
import {
  ResourceEventStream,
  ResourceYAMLEditor,
  Timestamp,
  useK8sWatchResource,
} from "@openshift-console/dynamic-plugin-sdk";
import { useState } from "react";
import { useParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import {
  ApicurioRegistry3,
  ApicurioRegistry3Model,
  Condition,
  getAppUrl,
  getErrorConditions,
  getReadyCondition,
  getStorageDescription,
  getUiUrl,
  isAuthEnabled,
} from "../utils/k8s";
import RegistryStatusBadge from "./RegistryStatusBadge";
import EmbeddedRegistryUI from "./EmbeddedRegistryUI";
import ManagedResourcesTab from "./ManagedResourcesTab";
import "../styles/plugin.css";

const ExternalLink: React.FC<{ href: string; text: string }> = ({
  href,
  text,
}) => (
  <a href={href} target="_blank" rel="noopener noreferrer">
    {text} ↗
  </a>
);

const ConditionsTable: React.FC<{ conditions: Condition[] }> = ({
  conditions,
}) => {
  const { t } = useTranslation("plugin__apicurio-registry");

  return (
    <Table variant="compact">
      <Thead>
        <Tr>
          <Th>{t("Type")}</Th>
          <Th>{t("Status")}</Th>
          <Th>{t("Reason")}</Th>
          <Th>{t("Message")}</Th>
          <Th>{t("Last Transition")}</Th>
        </Tr>
      </Thead>
      <Tbody>
        {conditions.map((condition) => (
          <Tr key={condition.type}>
            <Td>{condition.type}</Td>
            <Td>
              <Label
                color={
                  condition.status === "True"
                    ? "green"
                    : condition.status === "False"
                      ? "red"
                      : "grey"
                }
              >
                {condition.status}
              </Label>
            </Td>
            <Td>{condition.reason ?? "-"}</Td>
            <Td style={{ maxWidth: 400, wordBreak: "break-word" }}>
              {condition.message ?? "-"}
            </Td>
            <Td>
              {condition.lastTransitionTime ? (
                <Timestamp timestamp={condition.lastTransitionTime} />
              ) : (
                "-"
              )}
            </Td>
          </Tr>
        ))}
      </Tbody>
    </Table>
  );
};

const LabelsDisplay: React.FC<{ labels?: Record<string, string> }> = ({
  labels,
}) => {
  if (!labels || Object.keys(labels).length === 0) return <span>-</span>;
  return (
    <LabelGroup>
      {Object.entries(labels).map(([key, value]) => (
        <Label key={key}>{`${key}=${value}`}</Label>
      ))}
    </LabelGroup>
  );
};

const RegistryDetailPage: React.FC = () => {
  const { t } = useTranslation("plugin__apicurio-registry");
  const { name, ns: namespace } = useParams();
  const [activeTab, setActiveTab] = useState<string | number>("overview");

  const [registry, loaded] = useK8sWatchResource<ApicurioRegistry3>({
    groupVersionKind: {
      group: ApicurioRegistry3Model.apiGroup,
      version: ApicurioRegistry3Model.apiVersion,
      kind: ApicurioRegistry3Model.kind,
    },
    name,
    namespace,
  });

  if (!loaded || !registry) {
    return null;
  }

  const readyCondition = getReadyCondition(registry);
  const errorConditions = getErrorConditions(registry);
  const conditions = registry.status?.conditions ?? [];
  const appUrl = getAppUrl(registry);
  const uiUrl = getUiUrl(registry);
  const app = registry.spec?.app;
  const ui = registry.spec?.ui;

  return (
    <>
      <PageSection variant="light" padding={{ default: "noPadding" }}>
        <div style={{ padding: "1rem 1.5rem 0" }}>
          <Title headingLevel="h1">
            {registry.metadata.name}{" "}
            <RegistryStatusBadge condition={readyCondition} />
          </Title>
        </div>

        {errorConditions.length > 0 && (
          <div style={{ padding: "0.5rem 1.5rem" }}>
            {errorConditions.map((c) => (
              <Alert
                key={c.type}
                variant="danger"
                isInline
                title={`${c.type}: ${c.reason ?? ""}`}
                style={{ marginBottom: "0.5rem" }}
              >
                {c.message}
              </Alert>
            ))}
          </div>
        )}

        <Tabs
          activeKey={activeTab}
          onSelect={(_event, tabIndex) => setActiveTab(tabIndex)}
        >
          <Tab
            eventKey="overview"
            title={<TabTitleText>{t("Overview")}</TabTitleText>}
          >
            <PageSection>
              <Grid hasGutter>
                <GridItem span={6}>
                  <Title headingLevel="h2">{t("Details")}</Title>
                  <DescriptionList>
                    <DescriptionListGroup>
                      <DescriptionListTerm>{t("Name")}</DescriptionListTerm>
                      <DescriptionListDescription>
                        {registry.metadata.name}
                      </DescriptionListDescription>
                    </DescriptionListGroup>
                    <DescriptionListGroup>
                      <DescriptionListTerm>{t("Namespace")}</DescriptionListTerm>
                      <DescriptionListDescription>
                        {registry.metadata.namespace}
                      </DescriptionListDescription>
                    </DescriptionListGroup>
                    <DescriptionListGroup>
                      <DescriptionListTerm>{t("Labels")}</DescriptionListTerm>
                      <DescriptionListDescription>
                        <LabelsDisplay labels={registry.metadata.labels} />
                      </DescriptionListDescription>
                    </DescriptionListGroup>
                    <DescriptionListGroup>
                      <DescriptionListTerm>{t("Created")}</DescriptionListTerm>
                      <DescriptionListDescription>
                        <Timestamp timestamp={registry.metadata.creationTimestamp} />
                      </DescriptionListDescription>
                    </DescriptionListGroup>
                  </DescriptionList>

                  <Title headingLevel="h2" style={{ marginTop: "1.5rem" }}>
                    {t("Endpoints")}
                  </Title>
                  <DescriptionList>
                    <DescriptionListGroup>
                      <DescriptionListTerm>{t("Registry API")}</DescriptionListTerm>
                      <DescriptionListDescription>
                        {appUrl ? (
                          <ExternalLink href={appUrl} text={app?.ingress?.host ?? ""} />
                        ) : "-"}
                      </DescriptionListDescription>
                    </DescriptionListGroup>
                    <DescriptionListGroup>
                      <DescriptionListTerm>{t("Registry UI")}</DescriptionListTerm>
                      <DescriptionListDescription>
                        {uiUrl ? (
                          <ExternalLink href={uiUrl} text={ui?.ingress?.host ?? ""} />
                        ) : ui?.enabled === false ? t("Disabled") : "-"}
                      </DescriptionListDescription>
                    </DescriptionListGroup>
                  </DescriptionList>
                </GridItem>

                <GridItem span={6}>
                  <Title headingLevel="h2">{t("Configuration")}</Title>
                  <DescriptionList>
                    <DescriptionListGroup>
                      <DescriptionListTerm>{t("Storage")}</DescriptionListTerm>
                      <DescriptionListDescription>
                        {getStorageDescription(registry)}
                      </DescriptionListDescription>
                    </DescriptionListGroup>
                    <DescriptionListGroup>
                      <DescriptionListTerm>{t("Authentication")}</DescriptionListTerm>
                      <DescriptionListDescription>
                        {isAuthEnabled(registry) ? (
                          <>
                            <Label color="green">{t("Enabled")}</Label>
                            {app?.auth?.authServerUrl && (
                              <span style={{ marginLeft: "0.5rem" }}>
                                {app.auth.authServerUrl}
                              </span>
                            )}
                          </>
                        ) : (
                          <Label color="grey">{t("Disabled")}</Label>
                        )}
                      </DescriptionListDescription>
                    </DescriptionListGroup>
                    {isAuthEnabled(registry) && app?.auth?.authz?.enabled && (
                      <DescriptionListGroup>
                        <DescriptionListTerm>{t("Authorization")}</DescriptionListTerm>
                        <DescriptionListDescription>
                          <Label color="blue">{t("Enabled")}</Label>
                          {app.auth.authz.ownerOnlyEnabled && " (owner-only)"}
                        </DescriptionListDescription>
                      </DescriptionListGroup>
                    )}
                    <DescriptionListGroup>
                      <DescriptionListTerm>{t("TLS")}</DescriptionListTerm>
                      <DescriptionListDescription>
                        {app?.ingress?.tlsTermination ?? (app?.tls?.insecureRequests === "disabled") ? (
                          <Label color="green">
                            {app?.ingress?.tlsTermination ?? "enabled"}
                          </Label>
                        ) : (
                          <Label color="grey">{t("Not configured")}</Label>
                        )}
                      </DescriptionListDescription>
                    </DescriptionListGroup>
                    <DescriptionListGroup>
                      <DescriptionListTerm>{t("Replicas")}</DescriptionListTerm>
                      <DescriptionListDescription>
                        {t("App")}: {app?.replicas ?? 1}
                        {app?.autoscaling?.enabled && (
                          <span> ({t("autoscaling")}: {app.autoscaling.minReplicas}-{app.autoscaling.maxReplicas})</span>
                        )}
                        {ui?.enabled !== false && (
                          <>
                            {" / "}{t("UI")}: {ui?.replicas ?? 1}
                            {ui?.autoscaling?.enabled && (
                              <span> ({t("autoscaling")}: {ui.autoscaling.minReplicas}-{ui.autoscaling.maxReplicas})</span>
                            )}
                          </>
                        )}
                      </DescriptionListDescription>
                    </DescriptionListGroup>
                  </DescriptionList>

                  {(app?.features || app?.otel || app?.searchIndex) && (
                    <>
                      <Title headingLevel="h2" style={{ marginTop: "1.5rem" }}>
                        {t("Features")}
                      </Title>
                      <DescriptionList>
                        {app?.features?.resourceDeleteEnabled !== undefined && (
                          <DescriptionListGroup>
                            <DescriptionListTerm>{t("Resource Delete")}</DescriptionListTerm>
                            <DescriptionListDescription>
                              <Label color={app.features.resourceDeleteEnabled ? "green" : "grey"}>
                                {app.features.resourceDeleteEnabled ? t("Enabled") : t("Disabled")}
                              </Label>
                            </DescriptionListDescription>
                          </DescriptionListGroup>
                        )}
                        {app?.features?.versionMutabilityEnabled !== undefined && (
                          <DescriptionListGroup>
                            <DescriptionListTerm>{t("Version Mutability")}</DescriptionListTerm>
                            <DescriptionListDescription>
                              <Label color={app.features.versionMutabilityEnabled ? "green" : "grey"}>
                                {app.features.versionMutabilityEnabled ? t("Enabled") : t("Disabled")}
                              </Label>
                            </DescriptionListDescription>
                          </DescriptionListGroup>
                        )}
                        {app?.otel?.enabled && (
                          <DescriptionListGroup>
                            <DescriptionListTerm>{t("OpenTelemetry")}</DescriptionListTerm>
                            <DescriptionListDescription>
                              <Label color="blue">{t("Enabled")}</Label>
                              {app.otel.endpoint && (
                                <span style={{ marginLeft: "0.5rem" }}>
                                  {app.otel.endpoint} ({app.otel.protocol ?? "grpc"})
                                </span>
                              )}
                            </DescriptionListDescription>
                          </DescriptionListGroup>
                        )}
                        {app?.searchIndex?.enabled && (
                          <DescriptionListGroup>
                            <DescriptionListTerm>{t("Search Index")}</DescriptionListTerm>
                            <DescriptionListDescription>
                              <Label color="blue">{t("Enabled")}</Label>
                              {app.searchIndex.hosts && (
                                <span style={{ marginLeft: "0.5rem" }}>
                                  {app.searchIndex.hosts}
                                </span>
                              )}
                            </DescriptionListDescription>
                          </DescriptionListGroup>
                        )}
                      </DescriptionList>
                    </>
                  )}
                </GridItem>
              </Grid>

              {conditions.length > 0 && (
                <div style={{ marginTop: "1.5rem" }}>
                  <Title headingLevel="h2">{t("Conditions")}</Title>
                  <ConditionsTable conditions={conditions} />
                </div>
              )}
            </PageSection>
          </Tab>

          <Tab
            eventKey="yaml"
            title={<TabTitleText>{t("YAML")}</TabTitleText>}
          >
            <div id="apicurio-yaml-editor" style={{
              position: "relative",
              width: "100%",
              height: "calc(100vh - 230px)",
              minHeight: 400,
            }}>
              <ResourceYAMLEditor initialResource={registry} />
            </div>
          </Tab>

          <Tab
            eventKey="resources"
            title={<TabTitleText>{t("Resources")}</TabTitleText>}
          >
            <ManagedResourcesTab registry={registry} />
          </Tab>

          <Tab
            eventKey="registry-ui"
            title={<TabTitleText>{t("Registry UI")}</TabTitleText>}
          >
            <EmbeddedRegistryUI registry={registry} />
          </Tab>

          <Tab
            eventKey="events"
            title={<TabTitleText>{t("Events")}</TabTitleText>}
          >
            <PageSection>
              <ResourceEventStream resource={registry} />
            </PageSection>
          </Tab>
        </Tabs>
      </PageSection>
    </>
  );
};

export default RegistryDetailPage;
