import {
  DescriptionList,
  DescriptionListDescription,
  DescriptionListGroup,
  DescriptionListTerm,
  Grid,
  GridItem,
  Label,
  PageSection,
  Tab,
  Tabs,
  TabTitleText,
  Title,
} from "@patternfly/react-core";
import {
  TableComposable,
  Tbody,
  Td,
  Th,
  Thead,
  Tr,
} from "@patternfly/react-table";
import {
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
  getReadyCondition,
  getStorageType,
  isAuthEnabled,
} from "../utils/k8s";
import RegistryStatusBadge from "./RegistryStatusBadge";
import EmbeddedRegistryUI from "./EmbeddedRegistryUI";
import "../styles/plugin.css";

const ConditionsTable: React.FC<{ conditions: Condition[] }> = ({
  conditions,
}) => {
  const { t } = useTranslation("plugin__apicurio-registry");

  return (
    <TableComposable variant="compact">
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
            <Td>{condition.message ?? "-"}</Td>
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
    </TableComposable>
  );
};

const RegistryDetailPage: React.FC = () => {
  const { t } = useTranslation("plugin__apicurio-registry");
  const { name, ns: namespace } =
    useParams<{ name: string; ns: string }>();
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
  const conditions = registry.status?.conditions ?? [];

  return (
    <>
      <PageSection variant="light">
        <Grid hasGutter>
          <GridItem>
            <Title headingLevel="h1">
              {registry.metadata.name}{" "}
              <RegistryStatusBadge condition={readyCondition} />
            </Title>
          </GridItem>
        </Grid>
      </PageSection>

      <PageSection variant="light" padding={{ default: "noPadding" }}>
        <Tabs
          activeKey={activeTab}
          onSelect={(_event, tabIndex) => setActiveTab(tabIndex)}
          className="apicurio-registry-detail-tabs"
        >
          <Tab
            eventKey="overview"
            title={<TabTitleText>{t("Overview")}</TabTitleText>}
          >
            <PageSection>
              <Grid hasGutter>
                <GridItem span={6}>
                  <Title headingLevel="h2">{t("Configuration")}</Title>
                  <DescriptionList>
                    <DescriptionListGroup>
                      <DescriptionListTerm>
                        {t("Storage")}
                      </DescriptionListTerm>
                      <DescriptionListDescription>
                        {getStorageType(registry)}
                      </DescriptionListDescription>
                    </DescriptionListGroup>
                    <DescriptionListGroup>
                      <DescriptionListTerm>
                        {t("Authentication")}
                      </DescriptionListTerm>
                      <DescriptionListDescription>
                        {isAuthEnabled(registry)
                          ? t("Enabled")
                          : t("Disabled")}
                      </DescriptionListDescription>
                    </DescriptionListGroup>
                    <DescriptionListGroup>
                      <DescriptionListTerm>
                        {t("Replicas")}
                      </DescriptionListTerm>
                      <DescriptionListDescription>
                        {registry.spec?.app?.replicas ?? 1}
                      </DescriptionListDescription>
                    </DescriptionListGroup>
                    <DescriptionListGroup>
                      <DescriptionListTerm>
                        {t("Ingress Host")}
                      </DescriptionListTerm>
                      <DescriptionListDescription>
                        {registry.spec?.app?.ingress?.host ?? "-"}
                      </DescriptionListDescription>
                    </DescriptionListGroup>
                    <DescriptionListGroup>
                      <DescriptionListTerm>
                        {t("Age")}
                      </DescriptionListTerm>
                      <DescriptionListDescription>
                        <Timestamp
                          timestamp={registry.metadata.creationTimestamp}
                        />
                      </DescriptionListDescription>
                    </DescriptionListGroup>
                  </DescriptionList>
                </GridItem>

                <GridItem span={6}>
                  <Title headingLevel="h2">
                    {t("Conditions")}
                  </Title>
                  {conditions.length > 0 ? (
                    <ConditionsTable conditions={conditions} />
                  ) : (
                    <p>-</p>
                  )}
                </GridItem>
              </Grid>
            </PageSection>
          </Tab>

          <Tab
            eventKey="registry-ui"
            title={<TabTitleText>{t("Registry UI")}</TabTitleText>}
          >
            <EmbeddedRegistryUI registry={registry} />
          </Tab>
        </Tabs>
      </PageSection>
    </>
  );
};

export default RegistryDetailPage;
