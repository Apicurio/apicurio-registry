import type { ApicurioRegistry3 } from "./k8s";

export function normalizeHostUrl(host?: string | null): string | null {
  if (!host) {
    return null;
  }
  const trimmed = host.trim();
  if (!trimmed) {
    return null;
  }
  if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
    return trimmed;
  }
  return `https://${trimmed}`;
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
