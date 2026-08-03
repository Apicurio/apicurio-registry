import { ApicurioRegistry3 } from "./k8s";

export function getRegistryUIUrl(registry: ApicurioRegistry3): string | null {
  const uiHost = registry.spec?.ui?.ingress?.host;
  if (uiHost) {
    return `https://${uiHost}`;
  }

  const appHost = registry.spec?.app?.ingress?.host;
  if (appHost) {
    return `https://${appHost}`;
  }

  return null;
}

export function getEmbeddedUIUrl(registry: ApicurioRegistry3): string | null {
  const baseUrl = getRegistryUIUrl(registry);
  if (!baseUrl) {
    return null;
  }

  const url = new URL(baseUrl);
  url.searchParams.set("masthead", "false");
  return url.toString();
}
