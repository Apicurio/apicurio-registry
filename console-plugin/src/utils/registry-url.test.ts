import test from "node:test";
import assert from "node:assert/strict";
import {
  getEmbeddedUIUrl,
  getRegistryUIUrl,
  isTlsEnabled,
  normalizeHostUrl,
} from "./registry-url.ts";
import type { ApicurioRegistry3 } from "./k8s.ts";

test("normalizeHostUrl handles empty and invalid inputs", () => {
  assert.equal(normalizeHostUrl(null), null);
  assert.equal(normalizeHostUrl(undefined), null);
  assert.equal(normalizeHostUrl(""), null);
  assert.equal(normalizeHostUrl("   "), null);
});

test("normalizeHostUrl preserves explicit scheme", () => {
  assert.equal(normalizeHostUrl("http://localhost:8080"), "http://localhost:8080");
  assert.equal(normalizeHostUrl("  https://registry.example.com  "), "https://registry.example.com");
  assert.equal(normalizeHostUrl("HTTP://my-registry.org"), "HTTP://my-registry.org");
});

test("normalizeHostUrl defaults localhost to http", () => {
  assert.equal(normalizeHostUrl("localhost:8080"), "http://localhost:8080");
  assert.equal(normalizeHostUrl("127.0.0.1:9000"), "http://127.0.0.1:9000");
});

test("normalizeHostUrl respects isTls option", () => {
  assert.equal(normalizeHostUrl("registry.apps.cluster.com", { isTls: true }), "https://registry.apps.cluster.com");
  assert.equal(normalizeHostUrl("registry.apps.cluster.com", { isTls: false }), "http://registry.apps.cluster.com");
  assert.equal(normalizeHostUrl("localhost:8080", { isTls: true }), "https://localhost:8080");
});

test("isTlsEnabled detects ingress TLS settings", () => {
  assert.equal(isTlsEnabled(undefined), false);
  assert.equal(isTlsEnabled({ host: "example.com" }), false);
  assert.equal(isTlsEnabled({ tlsSecretName: "my-tls-secret" }), true);
  assert.equal(isTlsEnabled({ tlsTermination: "edge" }), true);
  assert.equal(isTlsEnabled({ tlsTermination: "passthrough" }), true);
  assert.equal(isTlsEnabled({ tlsTermination: "reencrypt" }), true);
});

test("getRegistryUIUrl prefers UI ingress and falls back to app ingress", () => {
  const withBoth: ApicurioRegistry3 = {
    apiVersion: "registry.apicur.io/v1",
    kind: "ApicurioRegistry3",
    metadata: { name: "test", namespace: "default", uid: "1", creationTimestamp: "", resourceVersion: "" },
    spec: {
      ui: { ingress: { host: "ui.example.com", tlsTermination: "edge" } },
      app: { ingress: { host: "app.example.com" } },
    },
  };
  assert.equal(getRegistryUIUrl(withBoth), "https://ui.example.com");

  const withWhitespaceUi: ApicurioRegistry3 = {
    apiVersion: "registry.apicur.io/v1",
    kind: "ApicurioRegistry3",
    metadata: { name: "test", namespace: "default", uid: "1", creationTimestamp: "", resourceVersion: "" },
    spec: {
      ui: { ingress: { host: "   " } },
      app: { ingress: { host: "app.example.com" } },
    },
  };
  assert.equal(getRegistryUIUrl(withWhitespaceUi), "http://app.example.com");

  const withAppOnlyTls: ApicurioRegistry3 = {
    apiVersion: "registry.apicur.io/v1",
    kind: "ApicurioRegistry3",
    metadata: { name: "test", namespace: "default", uid: "1", creationTimestamp: "", resourceVersion: "" },
    spec: {
      app: { ingress: { host: "app.example.com", tlsTermination: "passthrough" } },
    },
  };
  assert.equal(getRegistryUIUrl(withAppOnlyTls), "https://app.example.com");

  const withNeither: ApicurioRegistry3 = {
    apiVersion: "registry.apicur.io/v1",
    kind: "ApicurioRegistry3",
    metadata: { name: "test", namespace: "default", uid: "1", creationTimestamp: "", resourceVersion: "" },
    spec: {},
  };
  assert.equal(getRegistryUIUrl(withNeither), null);
});

test("getEmbeddedUIUrl adds masthead query param safely", () => {
  const reg: ApicurioRegistry3 = {
    apiVersion: "registry.apicur.io/v1",
    kind: "ApicurioRegistry3",
    metadata: { name: "test", namespace: "default", uid: "1", creationTimestamp: "", resourceVersion: "" },
    spec: {
      app: { ingress: { host: "localhost:8080" } },
    },
  };
  assert.equal(getEmbeddedUIUrl(reg), "http://localhost:8080/?masthead=false");
});
