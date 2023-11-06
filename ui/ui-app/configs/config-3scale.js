var ApicurioRegistryConfig = {
    artifacts: {
        url: "https://apicurio-registry-api-rhaf-apicurio-registry.apps.dev-eng-ocp4-mas.dev.3sca.net/apis/registry/v2"
    },
    ui: {
        contextPath: "/",
        navPrefixPath: "/",
        oaiDocsUrl: "http://localhost:8889"
    },
    auth: {
        type: "oidc",
        rbacEnabled: true,
        obacEnabled: false,
        options: {
            url: "https://keycloak-rhaf-apicurio-designer.apps.dev-eng-ocp4-mas.dev.3sca.net/realms/registry",
            redirectUri: "http://localhost:8888",
            clientId: "apicurio-registry",
            scopes: "openid profile email offline_token"
        }
    },
    features: {
        readOnly: false,
        breadcrumbs: true,
        roleManagement: false,
        settings: true
    }
};
