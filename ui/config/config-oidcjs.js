var ApicurioRegistryConfig = {
    artifacts: {
        url: "http://localhost:8080/apis/registry"
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
            url: "http://localhost:8090",
            redirectUri: "http://localhost:8888",
            clientId:"apicurio-registry",
        }
    },
    features: {
        readOnly: false,
        breadcrumbs: true,
        roleManagement: false,
        settings: true
    }
};
