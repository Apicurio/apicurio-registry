var ApicurioRegistryConfig = {
    artifacts: {
        url: "http://localhost:8080/apis/registry/v3"
    },
    ui: {
        contextPath: "/",
        navPrefixPath: "/",
        oaiDocsUrl: "http://localhost:8889"
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
