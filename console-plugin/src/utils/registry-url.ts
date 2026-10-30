import type { ApicurioRegistry3, IngressSpec } from "./k8s";

export function isTlsEnabled(ingress?: IngressSpec): boolean {
  return Boolean(ingress?.tlsSecretName || ingress?.tlsTermination);
}

export function normalizeHostUrl(
  rawHost?: string | null,
  options?: { isTls?: boolean }
): string | null {
  if (!rawHost) {
    return null;
  }
  const host = rawHost.trim();
  if (!host) {
    return null;
  }
  if (/^https?:\/\//i.test(host)) {
    return host;
  }
  const scheme = options?.isTls ? "https" : "http";
  return `${scheme}://${host}`;
}

export function getRegistryUIUrl(registry: ApicurioRegistry3): string | null {
  const uiIngress = registry.spec?.ui?.ingress;
  const uiUrl = normalizeHostUrl(uiIngress?.host, { isTls: isTlsEnabled(uiIngress) });
  if (uiUrl) {
    return uiUrl;
  }

  const appIngress = registry.spec?.app?.ingress;
  return normalizeHostUrl(appIngress?.host, { isTls: isTlsEnabled(appIngress) });
}

export function getEmbeddedUIUrl(registry: ApicurioRegistry3): string | null {
  const baseUrl = getRegistryUIUrl(registry);
  if (!baseUrl) {
    return null;
  }

  try {
    const url = new URL(baseUrl);
    url.searchParams.set("masthead", "false");
    return url.toString();
  } catch {
    return null;
  }
}
