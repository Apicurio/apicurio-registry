var ApicurioRegistryConfig = {
    mode: "prod",
    artifacts: {
        type: "rest",
        url: "http://localhost:8080/apis/registry"
    },
    ui: {
        contextPath: null,
        url: "http://localhost:8080/ui"
    },
    auth: {
        type: "keycloakjs",
        options: {
            url: "https://studio-auth.apicur.io/auth",
            realm: "apicurio-local",
            clientId:"apicurio-registry",
            onLoad: "login-required"
        }
    },
    features: {
        readOnly: false,
        breadcrumbs: true
    }
};
