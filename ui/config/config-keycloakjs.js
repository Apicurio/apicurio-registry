var ApicurioRegistryConfig = {
    artifacts: {
        url: "http://localhost:8080/apis/registry"
    },
    ui: {
        contextPath: "/",
        navPrefixPath: "/"
    },
    auth: {
        type: "keycloakjs",
        rbacEnabled: true,
        obacEnabled: false,
        options: {
            url: "https://studio-auth.apicur.io/auth",
            realm: "apicurio-local",
            clientId:"apicurio-registry",
            onLoad: "login-required",
            checkLoginIframe: false
        }
    },
    features: {
        readOnly: false,
        breadcrumbs: true,
        roleManagement: false,
        settings: true
    }
};
