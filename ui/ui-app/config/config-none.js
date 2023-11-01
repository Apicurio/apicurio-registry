var ApicurioRegistryConfig = {
    artifacts: {
        url: "http://localhost:8080/apis/registry/v2"
    },
    ui: {
        contextPath: "/",
        navPrefixPath: "/"
    },
    auth: {
        type: "none"
    },
    features: {
        readOnly: false,
        breadcrumbs: true,
        roleManagement: false,
        settings: true
    }
};
