var ApicurioRegistryConfig = {
    mode: "dev",
    artifacts: {
        type: "rest",
        url: "http://localhost:8080/apis/registry"
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
        breadcrumbs: true
    }
};
