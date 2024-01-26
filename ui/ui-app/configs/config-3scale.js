var ApicurioRegistryConfig = {
    artifacts: {
        url: "https://registry-api.dev.apicur.io/apis/registry/v3"
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
            url: "https://sso.dev.apicur.io/realms/apicurio",
            redirectUri: "http://localhost:8888",
            clientId: "registry-ui"
        }
    },
    features: {
        readOnly: false,
        breadcrumbs: true,
        roleManagement: false,
        settings: true
    }
};
