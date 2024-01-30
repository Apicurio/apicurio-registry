#! /usr/bin/env node

const fs = require("fs");

const CONFIG_OUTPUT_PATH=process.env["REGISTRY_CONFIG_OUTPUT_PATH"] || "/opt/app-root/src/config.js";

console.info("Generating application config at:", CONFIG_OUTPUT_PATH);

const REGISTRY_API_URL=process.env["REGISTRY_API_URL"] || "http://localhost:8080/apis/registry/v2";

const CONTEXT_PATH=process.env["REGISTRY_CONTEXT_PATH"];
const NAV_PREFIX_PATH=process.env["REGISTRY_NAV_PREFIX_PATH"];
const DOCS_URL=process.env["REGISTRY_DOCS_URL"];

const AUTH_TYPE=process.env["REGISTRY_AUTH_TYPE"];
const AUTH_RBAC_ENABLED=process.env["REGISTRY_AUTH_RBAC_ENABLED"];
const AUTH_OBAC_ENABLED=process.env["REGISTRY_AUTH_OBAC_ENABLED"];
const AUTH_URL=process.env["REGISTRY_AUTH_URL"];
const AUTH_CLIENT_ID=process.env["REGISTRY_AUTH_CLIENT_ID"];
const AUTH_CLIENT_SCOPES=process.env["REGISTRY_AUTH_CLIENT_SCOPES"];
const AUTH_REDIRECT_URL=process.env["REGISTRY_AUTH_REDIRECT_URL"];

const FEATURE_READ_ONLY=process.env["REGISTRY_FEATURE_READ_ONLY"];
const FEATURE_BREADCRUMBS=process.env["REGISTRY_FEATURE_BREADCRUMBS"];
const FEATURE_ROLE_MANAGEMENT=process.env["REGISTRY_FEATURE_ROLE_MANAGEMENT"];
const FEATURE_SETTINGS=process.env["REGISTRY_FEATURE_SETTINGS"];


// Create the config to output.
const CONFIG = {
    artifacts: {
        url: `${REGISTRY_API_URL}`
    },
    ui: {},
    auth: {},
    features: {}
};


// Configure UI elements
if (CONTEXT_PATH) {
    CONFIG.ui.contextPath = CONTEXT_PATH;
}
if (NAV_PREFIX_PATH) {
    CONFIG.ui.navPrefixPath = NAV_PREFIX_PATH;
}
if (DOCS_URL) {
    CONFIG.ui.oaiDocsUrl = DOCS_URL;
}


// Configure auth
if (AUTH_TYPE) {
    CONFIG.auth.type = AUTH_TYPE;
}
if (AUTH_RBAC_ENABLED) {
    CONFIG.auth.rbacEnabled = AUTH_RBAC_ENABLED === "true";
}
if (AUTH_OBAC_ENABLED) {
    CONFIG.auth.obacEnabled = AUTH_OBAC_ENABLED === "true";
}

if (AUTH_TYPE === "oidc") {
    CONFIG.auth.options = {};
    if (AUTH_URL) {
        CONFIG.auth.options.url = AUTH_URL;
    }
    if (AUTH_REDIRECT_URL) {
        CONFIG.auth.options.redirectUri = AUTH_REDIRECT_URL;
    }
    if (AUTH_CLIENT_ID) {
        CONFIG.auth.options.clientId = AUTH_CLIENT_ID_URL;
    }
    if (AUTH_CLIENT_SCOPES) {
        CONFIG.auth.options.scope = AUTH_CLIENT_SCOPES;
    }
}

// Configure features
if (FEATURE_READ_ONLY) {
    CONFIG.features.readOnly = FEATURE_READ_ONLY === "true";
}
if (FEATURE_BREADCRUMBS) {
    CONFIG.features.breadcrumbs = FEATURE_BREADCRUMBS === "true";
}
if (FEATURE_ROLE_MANAGEMENT) {
    CONFIG.features.roleManagement = FEATURE_ROLE_MANAGEMENT === "true";
}
if (FEATURE_SETTINGS) {
    CONFIG.features.settings = FEATURE_SETTINGS === "true";
}



const FILE_CONTENT = `
const ApicurioRegistryConfig = ${JSON.stringify(CONFIG, null, 4)};
`;

fs.writeFile(CONFIG_OUTPUT_PATH, FILE_CONTENT, "utf8", (err) => {
    if (err) {
      console.error("Error writing config to file:", err);
      return;
    }
    console.log("Config successfully writen to file.");
});
