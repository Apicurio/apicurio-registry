var ApicurioRegistryConfig = {
    artifacts: {
        url: "http://localhost:8080/apis/registry/v2"
    },
    ui: {
        contextPath: "/",
        navPrefixPath: "/"
    },
    auth: {
        type: "oidc",
        rbacEnabled: true,
        obacEnabled: false,
        options: {
            url: "https://auth.apicur.io/auth/realms/apicurio-local",
            redirectUri: "http://localhost:8888",
            clientId: "apicurio-registry-ui",
        }
    },
    features: {
        readOnly: false,
        breadcrumbs: true,
        roleManagement: false,
        settings: true
    }
};
