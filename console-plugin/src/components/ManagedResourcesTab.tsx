import {
  Label,
  PageSection,
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
  ResourceLink,
  useK8sWatchResource,
} from "@openshift-console/dynamic-plugin-sdk";
import { useTranslation } from "react-i18next";
import { ApicurioRegistry3, getResourceLabels } from "../utils/k8s";

interface K8sResource {
  apiVersion: string;
  kind: string;
  metadata: {
    name: string;
    namespace?: string;
    uid: string;
    creationTimestamp: string;
  };
  spec?: Record<string, unknown>;
  status?: Record<string, unknown>;
}

interface DeploymentResource extends K8sResource {
  spec?: {
    replicas?: number;
    template?: {
      spec?: {
        containers?: Array<{ image?: string }>;
      };
    };
  };
  status?: {
    readyReplicas?: number;
    replicas?: number;
    availableReplicas?: number;
  };
}

interface ServiceResource extends K8sResource {
  spec?: {
    clusterIP?: string;
    ports?: Array<{ name?: string; port?: number; protocol?: string }>;
    type?: string;
  };
}

interface IngressResource extends K8sResource {
  spec?: {
    rules?: Array<{ host?: string }>;
    tls?: Array<{ hosts?: string[] }>;
  };
}

const DeploymentStatus: React.FC<{ d: DeploymentResource }> = ({ d }) => {
  const ready = d.status?.readyReplicas ?? 0;
  const desired = d.spec?.replicas ?? 0;
  const color = ready === desired && ready > 0 ? "green" : ready > 0 ? "orange" : "red";
  return <Label color={color}>{ready}/{desired} ready</Label>;
};

const ManagedResourcesTab: React.FC<{ registry: ApicurioRegistry3 }> = ({
  registry,
}) => {
  const { t } = useTranslation("plugin__apicurio-registry");
  const labels = getResourceLabels(registry);
  const ns = registry.metadata.namespace;

  const [deployments] = useK8sWatchResource<DeploymentResource[]>({
    groupVersionKind: { group: "apps", version: "v1", kind: "Deployment" },
    namespace: ns,
    selector: { matchLabels: labels },
    isList: true,
  });

  const [services] = useK8sWatchResource<ServiceResource[]>({
    groupVersionKind: { group: "", version: "v1", kind: "Service" },
    namespace: ns,
    selector: { matchLabels: labels },
    isList: true,
  });

  const [ingresses] = useK8sWatchResource<IngressResource[]>({
    groupVersionKind: {
      group: "networking.k8s.io",
      version: "v1",
      kind: "Ingress",
    },
    namespace: ns,
    selector: { matchLabels: labels },
    isList: true,
  });

  return (
    <PageSection>
      <Title headingLevel="h2" style={{ marginBottom: "1rem" }}>
        {t("Deployments")}
      </Title>
      {(deployments ?? []).length > 0 ? (
        <Table variant="compact">
          <Thead>
            <Tr>
              <Th>{t("Name")}</Th>
              <Th>{t("Status")}</Th>
              <Th>{t("Image")}</Th>
            </Tr>
          </Thead>
          <Tbody>
            {(deployments ?? []).map((d) => (
              <Tr key={d.metadata.uid}>
                <Td>
                  <ResourceLink
                    groupVersionKind={{
                      group: "apps",
                      version: "v1",
                      kind: "Deployment",
                    }}
                    name={d.metadata.name}
                    namespace={ns}
                  />
                </Td>
                <Td>
                  <DeploymentStatus d={d} />
                </Td>
                <Td>
                  {d.spec?.template?.spec?.containers?.[0]?.image ?? "-"}
                </Td>
              </Tr>
            ))}
          </Tbody>
        </Table>
      ) : (
        <p>-</p>
      )}

      <Title headingLevel="h2" style={{ marginTop: "1.5rem", marginBottom: "1rem" }}>
        {t("Services")}
      </Title>
      {(services ?? []).length > 0 ? (
        <Table variant="compact">
          <Thead>
            <Tr>
              <Th>{t("Name")}</Th>
              <Th>{t("Type")}</Th>
              <Th>{t("Cluster IP")}</Th>
              <Th>{t("Ports")}</Th>
            </Tr>
          </Thead>
          <Tbody>
            {(services ?? []).map((s) => (
              <Tr key={s.metadata.uid}>
                <Td>
                  <ResourceLink
                    groupVersionKind={{
                      group: "",
                      version: "v1",
                      kind: "Service",
                    }}
                    name={s.metadata.name}
                    namespace={ns}
                  />
                </Td>
                <Td>{s.spec?.type ?? "ClusterIP"}</Td>
                <Td>{s.spec?.clusterIP ?? "-"}</Td>
                <Td>
                  {(s.spec?.ports ?? [])
                    .map((p) => `${p.port}/${p.protocol ?? "TCP"}`)
                    .join(", ") || "-"}
                </Td>
              </Tr>
            ))}
          </Tbody>
        </Table>
      ) : (
        <p>-</p>
      )}

      <Title headingLevel="h2" style={{ marginTop: "1.5rem", marginBottom: "1rem" }}>
        {t("Ingresses")}
      </Title>
      {(ingresses ?? []).length > 0 ? (
        <Table variant="compact">
          <Thead>
            <Tr>
              <Th>{t("Name")}</Th>
              <Th>{t("Host")}</Th>
              <Th>{t("TLS")}</Th>
            </Tr>
          </Thead>
          <Tbody>
            {(ingresses ?? []).map((i) => (
              <Tr key={i.metadata.uid}>
                <Td>
                  <ResourceLink
                    groupVersionKind={{
                      group: "networking.k8s.io",
                      version: "v1",
                      kind: "Ingress",
                    }}
                    name={i.metadata.name}
                    namespace={ns}
                  />
                </Td>
                <Td>{i.spec?.rules?.[0]?.host ?? "-"}</Td>
                <Td>
                  {i.spec?.tls && i.spec.tls.length > 0 ? (
                    <Label color="green">{t("Enabled")}</Label>
                  ) : (
                    <Label color="grey">{t("Disabled")}</Label>
                  )}
                </Td>
              </Tr>
            ))}
          </Tbody>
        </Table>
      ) : (
        <p>-</p>
      )}
    </PageSection>
  );
};

export default ManagedResourcesTab;
