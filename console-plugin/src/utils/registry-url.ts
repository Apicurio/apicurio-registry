import { ApicurioRegistry3 } from "./k8s";

function normalizeHostUrl(host: string): string {
  if (host.startsWith("http://") || host.startsWith("https://")) {
    return host;
  }
  return `https://${host}`;
}

export function getRegistryUIUrl(registry: ApicurioRegistry3): string | null {
  const uiHost = registry.spec?.ui?.ingress?.host;
  if (uiHost) {
    return normalizeHostUrl(uiHost);
  }

  const appHost = registry.spec?.app?.ingress?.host;
  if (appHost) {
    return normalizeHostUrl(appHost);
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
