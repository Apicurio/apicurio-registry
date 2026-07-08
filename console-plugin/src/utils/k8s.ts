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
  };
  spec: {
    app?: {
      replicas?: number;
      ingress?: {
        enabled?: boolean;
        host?: string;
      };
      storage?: {
        type?: "postgresql" | "mysql" | "kafkasql" | "kubernetesops" | "gitops";
      };
      auth?: {
        enabled?: boolean;
      };
      features?: {
        resourceDeleteEnabled?: boolean;
        versionMutabilityEnabled?: boolean;
      };
    };
    ui?: {
      enabled?: boolean;
      replicas?: number;
      ingress?: {
        enabled?: boolean;
        host?: string;
      };
    };
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

export function getReadyCondition(
  registry: ApicurioRegistry3
): Condition | undefined {
  return registry.status?.conditions?.find((c) => c.type === "Ready");
}

export function getStorageType(registry: ApicurioRegistry3): string {
  return registry.spec?.app?.storage?.type ?? "postgresql";
}

export function isAuthEnabled(registry: ApicurioRegistry3): boolean {
  return registry.spec?.app?.auth?.enabled === true;
}
