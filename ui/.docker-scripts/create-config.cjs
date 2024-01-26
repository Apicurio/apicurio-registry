#! /usr/bin/env node

const fs = require("fs");

const CONFIG_OUTPUT_PATH=process.env["REGISTRY_CONFIG_OUTPUT_PATH"] || "/opt/app-root/src/config.js";

console.info("Generating application config at:", CONFIG_OUTPUT_PATH);

const CONTEXT_PATH=process.env["REGISTRY_CONTEXT_PATH"] || "/";
const NAV_PREFIX_PATH=process.env["REGISTRY_NAV_PREFIX_PATH"] || "/";
const REGISTRY_API_URL=process.env["REGISTRY_API_URL"] || "http://localhost:8080/apis/registry/v2";

const AUTH_TYPE=process.env["REGISTRY_AUTH_TYPE"] || "none";
const AUTH_RBAC_ENABLED=process.env["REGISTRY_AUTH_RBAC_ENABLED"] || "false";
const AUTH_OBAC_ENABLED=process.env["REGISTRY_AUTH_OBAC_ENABLED"] || "false";
const AUTH_URL=process.env["REGISTRY_AUTH_URL"] || "";
const AUTH_CLIENT_ID=process.env["REGISTRY_AUTH_CLIENT_ID"] || "registry-ui";
const AUTH_CLIENT_SCOPES=process.env["REGISTRY_AUTH_CLIENT_SCOPES"] || "openid profile email";
const AUTH_REDIRECT_URL=process.env["REGISTRY_AUTH_REDIRECT_URL"] || "http://localhost:8888";

const FEATURE_READ_ONLY=process.env["REGISTRY_FEATURE_READ_ONLY"] || "false";
const FEATURE_BREADCRUMBS=process.env["REGISTRY_FEATURE_BREADCRUMBS"] || "true";
const FEATURE_ROLE_MANAGEMENT=process.env["REGISTRY_FEATURE_ROLE_MANAGEMENT"] || "false";
const FEATURE_SETTINGS=process.env["REGISTRY_FEATURE_SETTINGS"] || "true";


// Create the config to output.
const CONFIG = {
    artifacts: {
        url: `${REGISTRY_API_URL}`
    },
    ui: {
        contextPath: CONTEXT_PATH,
        navPrefixPath: NAV_PREFIX_PATH,
        oaiDocsUrl: "/docs/"
    },
    auth: {
        type: AUTH_TYPE,
        rbacEnabled: AUTH_RBAC_ENABLED === "true",
        obacEnabled: AUTH_OBAC_ENABLED === "true",
        options: {
            url: AUTH_URL,
            redirectUri: AUTH_REDIRECT_URL,
            clientId: AUTH_CLIENT_ID,
            scope: AUTH_CLIENT_SCOPES
        }
    },
    features: {
        readOnly: FEATURE_READ_ONLY === "true",
        breadcrumbs: FEATURE_BREADCRUMBS === "true",
        roleManagement: FEATURE_ROLE_MANAGEMENT === "true",
        settings: FEATURE_SETTINGS === "true"
    }
};

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
