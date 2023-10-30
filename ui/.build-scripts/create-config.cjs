#! /usr/bin/env node

const fs = require("fs");

const CONFIG_OUTPUT_PATH=process.env["DESIGNER_CONFIG_OUTPUT_PATH"] || "/opt/app-root/src/config.js";

console.info("Generating application config at:", CONFIG_OUTPUT_PATH);

const CONTEXT_PATH=process.env["DESIGNER_CONTEXT_PATH"] || "/";
const NAV_PREFIX_PATH=process.env["DESIGNER_NAV_PREFIX_PATH"] || "";
const DESIGNER_API_URL=process.env["DESIGNER_API_URL"] || "/apis/designer/v0";
const SHOW_MASTHEAD=process.env["DESIGNER_SHOW_MASTHEAD"] || "true";
const MASTHEAD_LABEL=process.env["DESIGNER_MASTHEAD_LABEL"] || "API DESIGNER";
const EDITORS_URL=process.env["DESIGNER_EDITORS_URL"] || "/editors/";

const AUTH_TYPE=process.env["DESIGNER_AUTH_TYPE"] || "none";
const AUTH_URL=process.env["DESIGNER_AUTH_URL"] || "";
const AUTH_CLIENT_ID=process.env["DESIGNER_AUTH_CLIENT_ID"] || "api-designer-ui";
const AUTH_REDIRECT_URL=process.env["DESIGNER_AUTH_REDIRECT_URL"] || "";

// Create the config to output.
const CONFIG = {
    "apis": {
        "designer": DESIGNER_API_URL
    },
    "ui": {
        "contextPath": CONTEXT_PATH,
        "navPrefixPath": NAV_PREFIX_PATH
    },
    "components": {
        "masthead": {
            "show": SHOW_MASTHEAD === "true",
            "label": MASTHEAD_LABEL
        },
        "editors": {
            "url": EDITORS_URL
        },
        "nav": {
            "show": false,
            "registry": "registry-nav"
        }
    },
    "auth": {
        "type": AUTH_TYPE,
        "options": {
            "redirectUri": AUTH_REDIRECT_URL,
            "clientId": AUTH_CLIENT_ID,
            "url": AUTH_URL
        }
    }
};

const FILE_CONTENT = `
const ApiDesignerConfig = ${JSON.stringify(CONFIG, null, 4)};
`;

fs.writeFile(CONFIG_OUTPUT_PATH, FILE_CONTENT, "utf8", (err) => {
    if (err) {
      console.error("Error writing config to file:", err);
      return;
    }
    console.log("Config successfully writen to file.");
});
