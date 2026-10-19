import type { ApicurioRegistry3, IngressSpec, TLSSpec } from "./k8s";

export function isTlsEnabled(ingress?: IngressSpec, tls?: TLSSpec): boolean {
  if (ingress?.tlsSecretName || ingress?.tlsTermination) {
    return true;
  }
  if (tls?.insecureRequests === "disabled" || tls?.insecureRequests === "redirect") {
    return true;
  }
  return false;
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
  const isLocal = host.startsWith("localhost") || host.startsWith("127.0.0.1");
  const useTls = options?.isTls ?? (!isLocal);
  const scheme = useTls ? "https" : "http";
  return `${scheme}://${host}`;
}

export function getRegistryUIUrl(registry: ApicurioRegistry3): string | null {
  const ui = registry.spec?.ui;
  const app = registry.spec?.app;
  const uiTls = isTlsEnabled(ui?.ingress, app?.tls);
  const uiUrl = normalizeHostUrl(ui?.ingress?.host, { isTls: uiTls });
  if (uiUrl) {
    return uiUrl;
  }

  const appTls = isTlsEnabled(app?.ingress, app?.tls);
  return normalizeHostUrl(app?.ingress?.host, { isTls: appTls });
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
