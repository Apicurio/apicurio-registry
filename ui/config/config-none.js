var ApicurioRegistryConfig = {
    mode: "prod",
    artifacts: {
        type: "rest",
        url: "http://localhost:8080/apis/registry"
    },
    ui: {
        contextPath: null
    },
    auth: {
        type: "none"
    },
    features: {
        readOnly: false,
        breadcrumbs: true
    }
};
