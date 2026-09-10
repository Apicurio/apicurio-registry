import { K8sModel } from "@openshift-console/dynamic-plugin-sdk";

export const ApicurioRegistry3Model: K8sModel = {
  apiGroup: "registry.apicur.io",
  apiVersion: "v1",
  kind: "ApicurioRegistry3",
  plural: "apicurioregistries3",
  label: "Apicurio Registry",
  labelPlural: "Apicurio Registries",
  abbr: "AR3",
  namespaced: true,
};

export const REGISTRY_GROUP_VERSION_KIND = {
  group: ApicurioRegistry3Model.apiGroup,
  version: ApicurioRegistry3Model.apiVersion,
  kind: ApicurioRegistry3Model.kind,
};

export interface IngressSpec {
  enabled?: boolean;
  host?: string;
  annotations?: Record<string, string>;
  ingressClassName?: string;
  tlsSecretName?: string;
  tlsTermination?: "edge" | "passthrough" | "reencrypt";
}

export interface AutoscalingSpec {
  enabled?: boolean;
  minReplicas?: number;
  maxReplicas?: number;
  targetCPUUtilizationPercentage?: number;
  targetMemoryUtilizationPercentage?: number;
}

export interface SqlDataSourceSpec {
  url?: string;
  username?: string;
  password?: { name?: string; key?: string };
}

export interface KafkaSqlSpec {
  bootstrapServers?: string;
  kafkaAccessSecretName?: string;
}

export interface GitOpsRepoSpec {
  url?: string;
  branch?: string;
  path?: string;
}

export interface GitOpsSpec {
  mode?: "pull" | "push";
  repos?: GitOpsRepoSpec[];
  registryId?: string;
}

export interface KubernetesOpsSpec {
  registryId?: string;
  namespace?: string;
  refreshEvery?: string;
  watchEnabled?: boolean;
}

export interface StorageSpec {
  type?: "postgresql" | "mysql" | "kafkasql" | "kubernetesops" | "gitops";
  sql?: { dataSource?: SqlDataSourceSpec };
  kafkasql?: KafkaSqlSpec;
  gitops?: GitOpsSpec;
  kubernetesops?: KubernetesOpsSpec;
}

export interface AuthzSpec {
  enabled?: boolean;
  ownerOnlyEnabled?: boolean;
  groupAccessEnabled?: boolean;
  readAccessEnabled?: boolean;
}

export interface AuthSpec {
  enabled?: boolean;
  appClientId?: string;
  uiClientId?: string;
  authServerUrl?: string;
  logoutUrl?: string;
  anonymousReadsEnabled?: boolean;
  authz?: AuthzSpec;
}

export interface TLSSpec {
  insecureRequests?: "enabled" | "redirect" | "disabled";
  keystoreSecretRef?: { name?: string; key?: string };
  truststoreSecretRef?: { name?: string; key?: string };
}

export interface OTelSpec {
  enabled?: boolean;
  endpoint?: string;
  protocol?: string;
  traceSamplingRatio?: number;
}

export interface SearchIndexSpec {
  enabled?: boolean;
  hosts?: string;
  indexName?: string;
  username?: string;
}

export interface FeaturesSpec {
  resourceDeleteEnabled?: boolean;
  versionMutabilityEnabled?: boolean;
}

export interface AppSpec {
  replicas?: number;
  ingress?: IngressSpec;
  storage?: StorageSpec;
  auth?: AuthSpec;
  tls?: TLSSpec;
  otel?: OTelSpec;
  searchIndex?: SearchIndexSpec;
  features?: FeaturesSpec;
  autoscaling?: AutoscalingSpec;
  podDisruptionBudget?: { enabled?: boolean };
  networkPolicy?: { enabled?: boolean };
  env?: Array<{ name: string; value?: string }>;
}

export interface UiSpec {
  enabled?: boolean;
  replicas?: number;
  ingress?: IngressSpec;
  autoscaling?: AutoscalingSpec;
  podDisruptionBudget?: { enabled?: boolean };
  networkPolicy?: { enabled?: boolean };
  env?: Array<{ name: string; value?: string }>;
}

export interface ApicurioRegistry3 {
  apiVersion: string;
  kind: string;
  metadata: {
    name: string;
    namespace: string;
    uid: string;
    creationTimestamp: string;
    resourceVersion: string;
    generation?: number;
    labels?: Record<string, string>;
    annotations?: Record<string, string>;
    ownerReferences?: Array<{
      apiVersion: string;
      kind: string;
      name: string;
      uid: string;
    }>;
  };
  spec: {
    app?: AppSpec;
    ui?: UiSpec;
    consolePlugin?: { enabled?: boolean };
  };
  status?: {
    observedGeneration?: number;
    conditions?: Condition[];
  };
}

export interface Condition {
  type: string;
  status: "True" | "False" | "Unknown";
  reason?: string;
  message?: string;
  lastTransitionTime?: string;
  lastUpdateTime?: string;
}

// Helper functions

export function getReadyCondition(registry: ApicurioRegistry3): Condition | undefined {
  return registry.status?.conditions?.find((c) => c.type === "Ready");
}

export function getErrorConditions(registry: ApicurioRegistry3): Condition[] {
  return (registry.status?.conditions ?? []).filter(
    (c) => (c.type === "OperatorError" || c.type === "ValidationError") && c.status === "True"
  );
}

export function getStorageType(registry: ApicurioRegistry3): string {
  return registry.spec?.app?.storage?.type ?? "postgresql";
}

export function isAuthEnabled(registry: ApicurioRegistry3): boolean {
  return registry.spec?.app?.auth?.enabled === true;
}

export function getAppUrl(registry: ApicurioRegistry3): string | null {
  const host = registry.spec?.app?.ingress?.host;
  return host ? `https://${host}` : null;
}

export function getUiUrl(registry: ApicurioRegistry3): string | null {
  const host = registry.spec?.ui?.ingress?.host;
  return host ? `https://${host}` : null;
}

export function isReady(registry: ApicurioRegistry3): boolean {
  const c = getReadyCondition(registry);
  return c?.status === "True";
}

export function getResourceLabels(registry: ApicurioRegistry3): Record<string, string> {
  return {
    app: registry.metadata.name,
    "app.kubernetes.io/managed-by": "apicurio-registry-operator",
  };
}

export function getStorageDescription(registry: ApicurioRegistry3): string {
  const storage = registry.spec?.app?.storage;
  if (!storage) return "postgresql (default)";
  switch (storage.type) {
    case "postgresql":
      return storage.sql?.dataSource?.url
        ? `PostgreSQL — ${storage.sql.dataSource.url}`
        : "PostgreSQL";
    case "mysql":
      return storage.sql?.dataSource?.url
        ? `MySQL — ${storage.sql.dataSource.url}`
        : "MySQL";
    case "kafkasql":
      return storage.kafkasql?.bootstrapServers
        ? `KafkaSQL — ${storage.kafkasql.bootstrapServers}`
        : "KafkaSQL";
    case "gitops":
      return storage.gitops?.mode
        ? `GitOps (${storage.gitops.mode})`
        : "GitOps";
    case "kubernetesops":
      return storage.kubernetesops?.registryId
        ? `KubernetesOps — ${storage.kubernetesops.registryId}`
        : "KubernetesOps";
    default:
      return storage.type ?? "postgresql";
  }
}
